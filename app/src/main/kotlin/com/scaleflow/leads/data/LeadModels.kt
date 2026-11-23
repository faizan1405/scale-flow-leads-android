package com.scaleflow.leads.data

import com.scaleflow.leads.BuildConfig
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class LeadRow(
    val rowIndex: Int,
    val fields: Map<String, String> = emptyMap()
)

@Serializable
data class HistoryEntry(
    val timestamp: String = "",
    val caller: String = "",
    val rowIndex: Int = 0,
    val leadName: String = "",
    val phone: String = "",
    val action: String = "",
    val fieldChanged: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val callDuration: String = ""
)

@Serializable
data class SheetPayload(
    val ok: Boolean = true,
    val error: String? = null,
    val spreadsheetName: String = "Scale Flow Leads",
    val sheetName: String = "Leads ",
    val sheetNames: List<String> = listOf("Leads "),
    val headers: List<String> = emptyList(),
    val dropdownOptions: Map<String, List<String>> = emptyMap(),
    val rows: List<LeadRow> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
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
data class SyncLogEntry(
    val timestamp: String,
    val level: String,
    val message: String
)

@Serializable
data class PendingSyncOp(
    val id: Long,
    val type: String = "update", // "update" or "logHistory"
    val sheetName: String,
    val rowIndex: Int,
    val caller: String = "App User",
    val leadName: String = "",
    val phone: String = "",
    val fields: Map<String, String> = emptyMap(),
    val eventAction: String = "",
    val fieldChanged: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val callDuration: String = "",
    val createdAt: String = ""
)

@Serializable
data class AppConfig(
    val scriptUrl: String = DEFAULT_SCRIPT_URL,
    val apiKey: String = "",
    val selectedSheet: String = "Leads ",
    val autoSyncSeconds: Int = 15,
    val demoMode: Boolean = false,
    val githubRepo: String = DEFAULT_GITHUB_REPO,
    val darkTheme: Boolean = false,
    val followUpWhatsAppMessage: String = "Hi {{name}}, we are following up regarding the website. Let me know a convenient time to connect.",
    val portfolioWhatsAppMessage: String = "Hi {{name}}, here is our website portfolio and recent work. Let us know what you think!",
    val autoOpenNotesAfterCall: Boolean = true,
    val sortBy: String = "Sheet order",
    val preferredNoteColumn: String = "",
    val callerProfile: String = ""
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
    val lastCallCol: String?,
    val assignedCol: String?,
    val dateCol: String?,
    val allHeaders: List<String>
) {
    fun isLockedColumn(header: String): Boolean = header == nameCol || header == phoneCol
    fun isDateColumn(header: String): Boolean = header == dateCol || header == lastCallCol

    val noteColumnOptions: List<String>
        get() {
            val excluded = setOfNotNull(
                nameCol, phoneCol, dateCol, lastCallCol, statusCol, followUpCol, assignedCol, websiteCol
            )
            return allHeaders.filter { it !in excluded }
        }

    companion object {
        fun detect(headers: List<String>): SchemaMapping {
            fun findFirst(vararg keywords: String): String? {
                for (kw in keywords) {
                    val exact = headers.firstOrNull { it.trim().equals(kw, ignoreCase = true) }
                    if (exact != null) return exact
                }
                for (kw in keywords) {
                    val partial = headers.firstOrNull { it.trim().contains(kw, ignoreCase = true) }
                    if (partial != null) return partial
                }
                return null
            }

            val nameCol = findFirst("name", "business", "company", "lead", "client", "shop", "store", "title")
                ?: headers.firstOrNull()
            val phoneCol = findFirst("numbers", "phone", "mobile", "number", "contact", "tel", "whatsapp")
            val websiteCol = findFirst("website", "url", "site", "domain", "link", "web")
            val statusCol = findFirst("lead type", "status", "stage", "result", "outcome", "state", "type")
            val followUpCol = findFirst("follow-ups", "follow-up", "follow up", "followup", "callback", "next call")
            val lastCallCol = findFirst("last call", "last called", "called on")
            val assignedCol = findFirst("assined to", "assigned to", "assigned", "owner", "caller")
            val dateCol = findFirst("date", "created", "added")

            val excluded = setOfNotNull(
                nameCol, phoneCol, dateCol, lastCallCol, statusCol, followUpCol, assignedCol, websiteCol
            )
            val notesCol = findFirst("caller notes", "call notes", "notes", "note", "remarks", "comment", "summary", "faizan")
                ?.takeIf { it !in excluded }
                ?: headers.firstOrNull { it !in excluded }

            return SchemaMapping(
                nameCol = nameCol,
                phoneCol = phoneCol,
                websiteCol = websiteCol,
                statusCol = statusCol,
                notesCol = notesCol,
                followUpCol = followUpCol,
                lastCallCol = lastCallCol,
                assignedCol = assignedCol,
                dateCol = dateCol,
                allHeaders = headers
            )
        }
    }
}

fun LeadRow.displayValue(col: String?): String = if (col == null) "" else fields[col]?.trim().orEmpty()
fun LeadRow.rawValue(col: String?): String = if (col == null) "" else fields[col].orEmpty()

fun LeadRow.initials(nameCol: String?): String {
    val raw = displayValue(nameCol).ifBlank { "SF" }
    val parts = raw.split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        raw.length >= 2 -> raw.take(2).uppercase()
        else -> raw.uppercase()
    }
}

fun formatLastCallTimestamp(date: Date = Date()): String {
    val sdf = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.ENGLISH)
    val formatted = sdf.format(date)
    return formatted.replace(" am,", " AM,").replace(" pm,", " PM,")
}

fun appendNotePreservingCase(existing: String, addition: String): String {
    val cleanAddition = addition.trim()
    if (cleanAddition.isEmpty()) return existing
    val cleanExisting = existing.trimEnd()
    return if (cleanExisting.isEmpty()) cleanAddition else "$cleanExisting\n$cleanAddition"
}
