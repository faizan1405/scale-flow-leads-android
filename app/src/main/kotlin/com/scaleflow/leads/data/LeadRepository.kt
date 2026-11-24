package com.scaleflow.leads.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveOutcome(
    val payload: SheetPayload,
    val syncedImmediately: Boolean,
    val pendingCount: Int,
    val errorDetail: String? = null
)

data class FlushResult(
    val flushedCount: Int,
    val remainingCount: Int,
    val lastError: String? = null
)

class LeadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("scale_flow_leads_prefs", Context.MODE_PRIVATE)
    private val api = SheetsApiClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        val parsed = runCatching { json.decodeFromString<AppConfig>(raw) }.getOrElse { AppConfig() }
        // Automatically migrate old placeholder portfolio message to live joinscaleflow.in/work link
        return if (parsed.portfolioWhatsAppMessage == AppConfig.LEGACY_PORTFOLIO_MESSAGE) {
            val migrated = parsed.copy(portfolioWhatsAppMessage = AppConfig.DEFAULT_PORTFOLIO_MESSAGE)
            saveConfig(migrated)
            migrated
        } else {
            parsed
        }
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

    fun loadPendingOps(): List<PendingSyncOp> {
        val raw = prefs.getString(KEY_PENDING_QUEUE, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingSyncOp>>(raw) }.getOrElse { emptyList() }
    }

    private fun savePendingOps(ops: List<PendingSyncOp>) {
        prefs.edit().putString(KEY_PENDING_QUEUE, json.encodeToString(ops)).apply()
    }

    private fun enqueuePendingOp(op: PendingSyncOp): Int {
        val current = loadPendingOps().toMutableList()
        current.add(op)
        savePendingOps(current)
        return current.size
    }

    suspend fun flushPendingQueue(config: AppConfig): FlushResult {
        val queue = loadPendingOps()
        if (queue.isEmpty() || config.demoMode || !config.isConnected) {
            return FlushResult(0, queue.size)
        }

        val remaining = mutableListOf<PendingSyncOp>()
        var flushed = 0
        var lastError: String? = null

        for (i in queue.indices) {
            val op = queue[i]
            val res = if (op.type == "logHistory") {
                api.logHistory(
                    scriptUrl = config.scriptUrl,
                    apiKey = config.apiKey,
                    sheetName = op.sheetName,
                    entry = HistoryEntry(
                        timestamp = op.createdAt,
                        caller = op.caller,
                        rowIndex = op.rowIndex,
                        leadName = op.leadName,
                        phone = op.phone,
                        action = op.eventAction,
                        fieldChanged = op.fieldChanged,
                        oldValue = op.oldValue,
                        newValue = op.newValue,
                        callDuration = op.callDuration
                    )
                )
            } else {
                api.updateRow(
                    scriptUrl = config.scriptUrl,
                    apiKey = config.apiKey,
                    sheetName = op.sheetName,
                    rowIndex = op.rowIndex,
                    fields = op.fields,
                    caller = op.caller,
                    leadName = op.leadName,
                    phone = op.phone,
                    callDuration = op.callDuration
                )
            }

            if (res.isSuccess) {
                flushed++
            } else {
                lastError = res.exceptionOrNull()?.message
                remaining.addAll(queue.subList(i, queue.size))
                break
            }
        }

        savePendingOps(remaining)
        return FlushResult(flushed, remaining.size, lastError)
    }

    suspend fun fetchLeads(config: AppConfig): Result<SheetPayload> {
        if (config.demoMode || !config.isConnected) {
            return Result.success(loadCachedPayload() ?: SheetPayload())
        }
        val result = api.fetchSheet(config.scriptUrl, config.apiKey, config.selectedSheet)
        return result.map { remote ->
            val pending = loadPendingOps()
            val pendingUpdates = pending.filter { it.type == "update" && it.sheetName == remote.sheetName }
            val mergedRows = if (pendingUpdates.isEmpty()) {
                remote.rows
            } else {
                remote.rows.map { row ->
                    val rowUpdates = pendingUpdates.filter { it.rowIndex == row.rowIndex }
                    if (rowUpdates.isEmpty()) row else {
                        val mergedFields = row.fields.toMutableMap()
                        rowUpdates.forEach { mergedFields.putAll(it.fields) }
                        row.copy(fields = mergedFields)
                    }
                }
            }
            val merged = remote.copy(rows = mergedRows)
            saveCachedPayload(merged)
            merged
        }
    }

    suspend fun updateLeadWithHistoryAndOfflineQueue(
        config: AppConfig,
        currentPayload: SheetPayload,
        rowIndex: Int,
        updatedFields: Map<String, String>,
        caller: String,
        leadName: String,
        phone: String,
        callDuration: String = ""
    ): SaveOutcome {
        val existingRow = currentPayload.rows.firstOrNull { it.rowIndex == rowIndex }
        val nowStamp = formatLastCallTimestamp()

        // Create local immutable history entries immediately for instant UI display
        val newHistoryEntries = updatedFields.entries.mapNotNull { (col, newVal) ->
            val oldVal = existingRow?.fields?.get(col).orEmpty()
            if (newVal != oldVal && !col.equals("Last Call", ignoreCase = true)) {
                HistoryEntry(
                    timestamp = nowStamp,
                    caller = caller,
                    rowIndex = rowIndex,
                    leadName = leadName,
                    phone = phone,
                    action = "Updated",
                    fieldChanged = col,
                    oldValue = oldVal,
                    newValue = newVal,
                    callDuration = callDuration
                )
            } else null
        }

        val mergedRows = currentPayload.rows.map { row ->
            if (row.rowIndex == rowIndex) row.copy(fields = row.fields + updatedFields) else row
        }
        val optimistic = currentPayload.copy(
            rows = mergedRows,
            history = newHistoryEntries + currentPayload.history,
            updatedAt = nowIso()
        )
        saveCachedPayload(optimistic)

        if (config.demoMode || !config.isConnected) {
            return SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        }

        val apiResult = api.updateRow(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            rowIndex = rowIndex,
            fields = updatedFields,
            caller = caller,
            leadName = leadName,
            phone = phone,
            callDuration = callDuration
        )

        return if (apiResult.isSuccess) {
            SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        } else {
            val count = enqueuePendingOp(
                PendingSyncOp(
                    id = System.currentTimeMillis(),
                    type = "update",
                    sheetName = currentPayload.sheetName,
                    rowIndex = rowIndex,
                    caller = caller,
                    leadName = leadName,
                    phone = phone,
                    fields = updatedFields,
                    callDuration = callDuration,
                    createdAt = nowStamp
                )
            )
            SaveOutcome(
                payload = optimistic,
                syncedImmediately = false,
                pendingCount = count,
                errorDetail = apiResult.exceptionOrNull()?.message
            )
        }
    }

    suspend fun logCallHistoryWithOfflineQueue(
        config: AppConfig,
        currentPayload: SheetPayload,
        entry: HistoryEntry
    ): SaveOutcome {
        val optimistic = currentPayload.copy(
            history = listOf(entry) + currentPayload.history,
            updatedAt = nowIso()
        )
        saveCachedPayload(optimistic)

        if (config.demoMode || !config.isConnected) {
            return SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        }

        val apiResult = api.logHistory(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            entry = entry
        )

        return if (apiResult.isSuccess) {
            SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        } else {
            val count = enqueuePendingOp(
                PendingSyncOp(
                    id = System.currentTimeMillis(),
                    type = "logHistory",
                    sheetName = currentPayload.sheetName,
                    rowIndex = entry.rowIndex,
                    caller = entry.caller,
                    leadName = entry.leadName,
                    phone = entry.phone,
                    eventAction = entry.action,
                    fieldChanged = entry.fieldChanged,
                    oldValue = entry.oldValue,
                    newValue = entry.newValue,
                    callDuration = entry.callDuration,
                    createdAt = entry.timestamp
                )
            )
            SaveOutcome(
                payload = optimistic,
                syncedImmediately = false,
                pendingCount = count,
                errorDetail = apiResult.exceptionOrNull()?.message
            )
        }
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    companion object {
        private const val KEY_CONFIG = "app_config_json"
        private const val KEY_CACHE = "cached_sheet_payload_json"
        private const val KEY_PENDING_QUEUE = "offline_pending_sync_queue_json"
    }
}
