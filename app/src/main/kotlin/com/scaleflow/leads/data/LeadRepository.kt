package com.scaleflow.leads.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("scale_flow_leads_prefs", Context.MODE_PRIVATE)
    private val api = SheetsApiClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        return runCatching { json.decodeFromString<AppConfig>(raw) }.getOrElse { AppConfig() }
    }

    fun saveConfig(config: AppConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(config)).apply()
    }

    fun loadCachedPayload(): SheetPayload? {
        val raw = prefs.getString(KEY_CACHE, null) ?: return null
        return runCatching { json.decodeFromString<SheetPayload>(raw) }.getOrNull()
    }

    fun saveCachedPayload(payload: SheetPayload) {
        prefs.edit().putString(KEY_CACHE, json.encodeToString(payload)).apply()
    }

    fun demoPayload(): SheetPayload {
        val cached = loadCachedPayload()
        if (cached != null && cached.spreadsheetName == "Scale Flow Leads (Demo)") {
            return cached
        }
        val initial = SheetPayload(
            ok = true,
            spreadsheetName = "Scale Flow Leads (Demo)",
            sheetName = "Website Leads",
            sheetNames = listOf("Website Leads"),
            headers = listOf("Business Name", "Phone", "Website", "Status", "Call Notes", "Follow-Up"),
            rows = listOf(
                LeadRow(
                    rowIndex = 2,
                    fields = mapOf(
                        "Business Name" to "Apex Dental Clinic",
                        "Phone" to "+1 555 234 8910",
                        "Website" to "https://apexdental.example.com",
                        "Status" to "New",
                        "Call Notes" to "Outdated mobile layout, great fit for redesign.",
                        "Follow-Up" to "Today 4:00 PM"
                    )
                ),
                LeadRow(
                    rowIndex = 3,
                    fields = mapOf(
                        "Business Name" to "BrightPath Roofing",
                        "Phone" to "+1 555 891 4420",
                        "Website" to "https://brightpathroofing.example.com",
                        "Status" to "Callback",
                        "Call Notes" to "[23 Nov, 11:15 AM] Spoke with receptionist, owner available after 3 PM.",
                        "Follow-Up" to "Tomorrow"
                    )
                ),
                LeadRow(
                    rowIndex = 4,
                    fields = mapOf(
                        "Business Name" to "GreenLeaf Landscaping",
                        "Phone" to "+1 555 302 7781",
                        "Website" to "No website",
                        "Status" to "Interested",
                        "Call Notes" to "[23 Nov, 01:30 PM] Owner wants portfolio samples on WhatsApp.",
                        "Follow-Up" to "Send quote"
                    )
                )
            ),
            updatedAt = nowIso()
        )
        saveCachedPayload(initial)
        return initial
    }

    suspend fun fetchLeads(config: AppConfig): Result<SheetPayload> {
        if (config.demoMode || !config.isConnected) {
            return Result.success(demoPayload())
        }
        val result = api.fetchSheet(config.scriptUrl, config.apiKey, config.selectedSheet)
        result.onSuccess { saveCachedPayload(it) }
        return result
    }

    suspend fun updateLead(
        config: AppConfig,
        currentPayload: SheetPayload,
        rowIndex: Int,
        updatedFields: Map<String, String>
    ): Result<SheetPayload> {
        val mergedRows = currentPayload.rows.map { row ->
            if (row.rowIndex == rowIndex) {
                row.copy(fields = row.fields + updatedFields)
            } else {
                row
            }
        }
        val optimistic = currentPayload.copy(rows = mergedRows, updatedAt = nowIso())
        saveCachedPayload(optimistic)

        if (config.demoMode || !config.isConnected) {
            return Result.success(optimistic)
        }
        val apiResult = api.updateRow(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            rowIndex = rowIndex,
            fields = updatedFields
        )
        return apiResult.map { optimistic }
    }

    suspend fun appendCallNote(
        config: AppConfig,
        currentPayload: SheetPayload,
        rowIndex: Int,
        noteColumn: String?,
        noteText: String,
        statusColumn: String?,
        statusValue: String?
    ): Result<SheetPayload> {
        val stamp = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val trimmedNote = noteText.trim()

        val mergedRows = currentPayload.rows.map { row ->
            if (row.rowIndex == rowIndex) {
                val mutable = row.fields.toMutableMap()
                if (!noteColumn.isNullOrBlank() && trimmedNote.isNotEmpty()) {
                    val existing = mutable[noteColumn].orEmpty().trim()
                    val entry = "[$stamp] $trimmedNote"
                    mutable[noteColumn] = if (existing.isNotEmpty()) "$entry\n$existing" else entry
                }
                if (!statusColumn.isNullOrBlank() && !statusValue.isNullOrBlank()) {
                    mutable[statusColumn] = statusValue
                }
                row.copy(fields = mutable)
            } else {
                row
            }
        }
        val optimistic = currentPayload.copy(rows = mergedRows, updatedAt = nowIso())
        saveCachedPayload(optimistic)

        if (config.demoMode || !config.isConnected) {
            return Result.success(optimistic)
        }

        val apiResult = api.appendNote(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            rowIndex = rowIndex,
            noteColumn = noteColumn,
            noteText = trimmedNote,
            statusColumn = statusColumn,
            statusValue = statusValue
        )
        return apiResult.map { optimistic }
    }

    suspend fun addLead(
        config: AppConfig,
        currentPayload: SheetPayload,
        fields: Map<String, String>
    ): Result<SheetPayload> {
        val nextRowIndex = (currentPayload.rows.maxOfOrNull { it.rowIndex } ?: 1) + 1
        if (config.demoMode || !config.isConnected) {
            val newRow = LeadRow(rowIndex = nextRowIndex, fields = fields)
            val updated = currentPayload.copy(rows = listOf(newRow) + currentPayload.rows, updatedAt = nowIso())
            saveCachedPayload(updated)
            return Result.success(updated)
        }

        val apiResult = api.appendRow(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            fields = fields
        )
        return apiResult.map { resp ->
            val realRowIndex = resp.rowIndex ?: nextRowIndex
            val newRow = LeadRow(rowIndex = realRowIndex, fields = fields)
            val updated = currentPayload.copy(rows = currentPayload.rows + newRow, updatedAt = nowIso())
            saveCachedPayload(updated)
            updated
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    companion object {
        private const val KEY_CONFIG = "app_config_json"
        private const val KEY_CACHE = "cached_sheet_payload_json"
    }
}
