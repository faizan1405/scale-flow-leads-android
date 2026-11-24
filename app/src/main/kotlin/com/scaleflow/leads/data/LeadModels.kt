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
    val portfolioWhatsAppMessage: String = DEFAULT_PORTFOLIO_MESSAGE,
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
        const val LEGACY_PORTFOLIO_MESSAGE =
            "Hi {{name}}, here is our website portfolio and recent work. Let us know what you think!"
        const val DEFAULT_PORTFOLIO_MESSAGE =
            "Hi {{name}}, here is our website portfolio and recent work: https://www.joinscaleflow.in/work\n\nLet us know what you think!"
    }
}

data class SchemaMapping(
    val nameCol: String?,
    val phoneCol: String?,
    val websiteCol: String?,
    val statusCol: String?,
    val notesCol: String?,
    val followUpCol: String?,
    val followUpDateCol: String?,
    val extraNotesCol: String?,
    val lastCallCol: String?,
    val assignedCol: String?,
    val dateCol: String?,
    val allHeaders: List<String>
) {
    /**
     * Locked columns (cannot be manually edited in the app):
     * Date, Last Call (auto-updated only), Name, and Phone Number.
     */
    fun isLockedColumn(header: String): Boolean =
        header == dateCol || header == lastCallCol || header == nameCol || header == phoneCol

    fun isDateColumn(header: String): Boolean =
        header == dateCol ||
            header == lastCallCol ||
            header == followUpDateCol ||
            header.trim().contains("date", ignoreCase = true)

    val noteColumnOptions: List<String>
        get() {
            val excluded = setOfNotNull(
                nameCol,
                phoneCol,
                dateCol,
                lastCallCol,
                statusCol,
                followUpCol,
                followUpDateCol,
                extraNotesCol,
                assignedCol,
                websiteCol
            )
            return allHeaders.filter { it !in excluded && !isDateColumn(it) }
        }

    companion object {
        fun detect(headers: List<String>): SchemaMapping {
            fun findFirst(exclude: Set<String> = emptySet(), vararg keywords: String): String? {
                val pool = headers.filter { it !in exclude }
                for (kw in keywords) {
                    val exact = pool.firstOrNull { it.trim().equals(kw, ignoreCase = true) }
                    if (exact != null) return exact
                }
                for (kw in keywords) {
                    val partial = pool.firstOrNull { it.trim().contains(kw, ignoreCase = true) }
                    if (partial != null) return partial
                }
                return null
            }

            val nameCol = findFirst(emptySet(), "name", "business", "company", "lead", "client", "shop", "store", "title")
                ?: headers.firstOrNull()
            val phoneCol = findFirst(emptySet(), "numbers", "phone", "mobile", "number", "contact", "tel", "whatsapp")
            val websiteCol = findFirst(emptySet(), "website", "url", "site", "domain", "link", "web")
            val statusCol = findFirst(emptySet(), "lead type", "status", "stage", "result", "outcome", "state", "type")
            val followUpDateCol = findFirst(emptySet(), "follow up date", "follow-up date", "followup date", "next call date", "callback date")
            val extraNotesCol = findFirst(emptySet(), "extra notes", "extra note", "additional notes", "other notes")
            val followUpCol = findFirst(setOfNotNull(followUpDateCol), "follow-ups", "follow-up", "follow up", "followup", "callback")
            val lastCallCol = findFirst(emptySet(), "last call", "last called", "called on")
            val assignedCol = findFirst(emptySet(), "assined to", "assigned to", "assigned", "owner", "caller")
            val dateCol = findFirst(setOfNotNull(followUpDateCol, lastCallCol), "date", "created", "added")

            val excluded = setOfNotNull(
                nameCol,
                phoneCol,
                dateCol,
                lastCallCol,
                statusCol,
                followUpCol,
                followUpDateCol,
                extraNotesCol,
                assignedCol,
                websiteCol
            )
            val notesCol = headers.firstOrNull { it !in excluded && !it.trim().contains("date", ignoreCase = true) }

            return SchemaMapping(
                nameCol = nameCol,
                phoneCol = phoneCol,
                websiteCol = websiteCol,
                statusCol = statusCol,
                notesCol = notesCol,
                followUpCol = followUpCol,
                followUpDateCol = followUpDateCol,
                extraNotesCol = extraNotesCol,
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

/**
 * Checks whether a Last Call / History timestamp string falls on [today].
 * Matches both "02:45 PM, 24 Nov 2026" and "24-Nov-2026".
 */
fun isTimestampFromDate(timestamp: String, today: Date = Date()): Boolean {
    val clean = timestamp.trim()
    if (clean.isEmpty()) return false
    val spaced = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(today)
    val dashed = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(today)
    val shortSpaced = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(today)
    return clean.contains(spaced, ignoreCase = true) ||
        clean.contains(dashed, ignoreCase = true) ||
        clean.contains(shortSpaced, ignoreCase = true)
}

fun todayKey(date: Date = Date()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

fun appendNotePreservingCase(existing: String, addition: String): String {
    val cleanAddition = addition.trim()
    if (cleanAddition.isEmpty()) return existing
    val cleanExisting = existing.trimEnd()
    return if (cleanExisting.isEmpty()) cleanAddition else "$cleanExisting\n$cleanAddition"
}
