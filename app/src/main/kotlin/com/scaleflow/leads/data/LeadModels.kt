package com.scaleflow.leads.data

import com.scaleflow.leads.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class LeadRow(
    val rowIndex: Int,
    val fields: Map<String, String> = emptyMap()
)

@Serializable
data class SheetPayload(
    val ok: Boolean = true,
    val error: String? = null,
    val spreadsheetName: String = "Scale Flow Leads",
    val sheetName: String = "Leads",
    val sheetNames: List<String> = listOf("Leads"),
    val headers: List<String> = emptyList(),
    val rows: List<LeadRow> = emptyList(),
    val updatedAt: String = ""
)

@Serializable
data class MutationResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val action: String? = null,
    val rowIndex: Int? = null,
    val updatedAt: String? = null
)

@Serializable
data class AppConfig(
    val scriptUrl: String = DEFAULT_SCRIPT_URL,
    val apiKey: String = "",
    val selectedSheet: String = "Leads ",
    val autoSyncSeconds: Int = 15,
    val demoMode: Boolean = false,
    val githubRepo: String = DEFAULT_GITHUB_REPO
) {
    val isConnected: Boolean
        get() = scriptUrl.trim().startsWith("https://")

    companion object {
        val DEFAULT_SCRIPT_URL: String
            get() = BuildConfig.DEFAULT_SCRIPT_URL
        const val DEFAULT_GITHUB_REPO = "faizan1405/scale-flow-leads-android"
    }
}

data class SchemaMapping(
    val nameCol: String?,
    val phoneCol: String?,
    val websiteCol: String?,
    val statusCol: String?,
    val notesCol: String?,
    val followUpCol: String?,
    val allHeaders: List<String>
) {
    companion object {
        fun detect(headers: List<String>): SchemaMapping {
            fun findFirst(vararg keywords: String): String? {
                for (kw in keywords) {
                    val exact = headers.firstOrNull { it.equals(kw, ignoreCase = true) }
                    if (exact != null) return exact
                }
                for (kw in keywords) {
                    val partial = headers.firstOrNull { it.contains(kw, ignoreCase = true) }
                    if (partial != null) return partial
                }
                return null
            }

            val nameCol = findFirst("business", "company", "name", "lead", "client", "shop", "store", "title")
                ?: headers.firstOrNull()
            val phoneCol = findFirst("phone", "mobile", "numbers", "number", "contact", "tel", "whatsapp", "call")
            val websiteCol = findFirst("website", "url", "site", "domain", "link", "web")
            val statusCol = findFirst("status", "lead type", "stage", "result", "outcome", "state", "type")
            val notesCol = findFirst("call notes", "notes", "note", "remarks", "comment", "feedback", "summary", "follow-ups", "follow ups", "last call")
            val followUpCol = findFirst("follow-ups", "follow up", "follow-up", "followup", "callback", "next call", "last call", "reminder")

            return SchemaMapping(
                nameCol = nameCol,
                phoneCol = phoneCol,
                websiteCol = websiteCol,
                statusCol = statusCol,
                notesCol = notesCol,
                followUpCol = followUpCol,
                allHeaders = headers
            )
        }
    }
}

fun LeadRow.displayValue(col: String?): String {
    if (col == null) return ""
    return fields[col]?.trim().orEmpty()
}
