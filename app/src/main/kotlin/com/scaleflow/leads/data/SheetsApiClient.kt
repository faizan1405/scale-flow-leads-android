package com.scaleflow.leads.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SheetsApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchSheet(
        scriptUrl: String,
        apiKey: String,
        sheetName: String
    ): Result<SheetPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val base = scriptUrl.trim()
            require(base.startsWith("https://")) { "Apps Script URL must start with https://" }

            val sep = if (base.contains("?")) "&" else "?"
            val params = buildList {
                add("action=list")
                if (apiKey.isNotBlank()) add("key=${URLEncoder.encode(apiKey.trim(), "UTF-8")}")
                if (sheetName.isNotEmpty()) add("sheet=${URLEncoder.encode(sheetName, "UTF-8")}")
                add("_t=${System.currentTimeMillis()}")
            }.joinToString("&")

            val responseText = executeGet("$base$sep$params")
            val payload = json.decodeFromString<SheetPayload>(responseText)
            if (!payload.ok) {
                error(payload.error ?: "Google Sheet returned an error")
            }
            payload
        }
    }

    suspend fun updateRow(
        scriptUrl: String,
        apiKey: String,
        sheetName: String,
        rowIndex: Int,
        fields: Map<String, String>
    ): Result<MutationResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("action", JsonPrimitive("update"))
                if (apiKey.isNotBlank()) put("key", JsonPrimitive(apiKey.trim()))
                if (sheetName.isNotEmpty()) put("sheet", JsonPrimitive(sheetName))
                put("rowIndex", JsonPrimitive(rowIndex))
                put("fields", JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
            }.toString()

            val responseText = executePostWithAppsScriptRedirect(scriptUrl.trim(), body)
            val parsed = json.decodeFromString<MutationResponse>(responseText)
            if (!parsed.ok) error(parsed.error ?: "Failed to update lead")
            parsed
        }
    }

    suspend fun appendNote(
        scriptUrl: String,
        apiKey: String,
        sheetName: String,
        rowIndex: Int,
        noteColumn: String?,
        noteText: String,
        statusColumn: String?,
        statusValue: String?
    ): Result<MutationResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("action", JsonPrimitive("appendNote"))
                if (apiKey.isNotBlank()) put("key", JsonPrimitive(apiKey.trim()))
                if (sheetName.isNotEmpty()) put("sheet", JsonPrimitive(sheetName))
                put("rowIndex", JsonPrimitive(rowIndex))
                if (!noteColumn.isNullOrBlank()) put("noteColumn", JsonPrimitive(noteColumn))
                put("noteText", JsonPrimitive(noteText))
                if (!statusColumn.isNullOrBlank()) put("statusColumn", JsonPrimitive(statusColumn))
                if (!statusValue.isNullOrBlank()) put("statusValue", JsonPrimitive(statusValue))
            }.toString()

            val responseText = executePostWithAppsScriptRedirect(scriptUrl.trim(), body)
            val parsed = json.decodeFromString<MutationResponse>(responseText)
            if (!parsed.ok) error(parsed.error ?: "Failed to save call note")
            parsed
        }
    }

    suspend fun appendRow(
        scriptUrl: String,
        apiKey: String,
        sheetName: String,
        fields: Map<String, String>
    ): Result<MutationResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("action", JsonPrimitive("append"))
                if (apiKey.isNotBlank()) put("key", JsonPrimitive(apiKey.trim()))
                if (sheetName.isNotEmpty()) put("sheet", JsonPrimitive(sheetName))
                put("fields", JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
            }.toString()

            val responseText = executePostWithAppsScriptRedirect(scriptUrl.trim(), body)
            val parsed = json.decodeFromString<MutationResponse>(responseText)
            if (!parsed.ok) error(parsed.error ?: "Failed to add lead")
            parsed
        }
    }

    private fun executeGet(urlString: String, redirectsLeft: Int = 5): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 301..308 && redirectsLeft > 0) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    conn.disconnect()
                    return executeGet(location, redirectsLeft - 1)
                }
            }
            val text = readBody(conn, code)
            if (code !in 200..299) {
                error("HTTP $code: ${text.take(200)}")
            }
            validateJsonResponse(text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Google Apps Script executes doPost() and returns HTTP 302 redirecting to
     * script.googleusercontent.com, which must be fetched via GET.
     */
    private fun executePostWithAppsScriptRedirect(urlString: String, jsonBody: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    conn.disconnect()
                    return executeGet(location)
                }
            }
            val text = readBody(conn, code)
            if (code !in 200..299) {
                error("HTTP $code: ${text.take(200)}")
            }
            validateJsonResponse(text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun validateJsonResponse(text: String) {
        val trimmed = text.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            error("Received an HTML page instead of JSON. Make sure your Apps Script deployment has 'Who has access: Anyone'.")
        }
    }
}
