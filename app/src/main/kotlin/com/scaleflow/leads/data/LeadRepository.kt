package com.scaleflow.leads.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InstantSaveResult(
    val payload: SheetPayload,
    val pendingCount: Int
)

data class FlushResult(
    val flushedCount: Int,
    val remainingCount: Int,
    val lastError: String? = null
)

class LeadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("scale_flow_leads_prefs", Context.MODE_PRIVATE)
    private val api = SheetsApiClient()
    private val queueLock = Any()
    private val syncMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadConfig(): AppConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AppConfig()
        val parsed = runCatching { json.decodeFromString<AppConfig>(raw) }.getOrElse { AppConfig() }
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

    fun loadPendingOps(): List<PendingSyncOp> = synchronized(queueLock) {
        val raw = prefs.getString(KEY_PENDING_QUEUE, null) ?: return emptyList()
        runCatching { json.decodeFromString<List<PendingSyncOp>>(raw) }.getOrElse { emptyList() }
    }

    private fun savePendingOps(ops: List<PendingSyncOp>) = synchronized(queueLock) {
        prefs.edit().putString(KEY_PENDING_QUEUE, json.encodeToString(ops)).apply()
    }

    private fun enqueuePendingOp(op: PendingSyncOp): Int = synchronized(queueLock) {
        val current = loadPendingOps().toMutableList()
        current.add(op)
        savePendingOps(current)
        current.size
    }

    /**
     * INSTANT (0ms UI wait): Applies lead edits + local History locally and queues for immediate background sync.
     */
    fun applyUpdateLocallyAndQueue(
        currentPayload: SheetPayload,
        rowIndex: Int,
        updatedFields: Map<String, String>,
        caller: String,
        leadName: String,
        phone: String,
        callDuration: String = ""
    ): InstantSaveResult {
        val existingRow = currentPayload.rows.firstOrNull { it.rowIndex == rowIndex }
        val nowStamp = formatLastCallTimestamp()

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

        val pendingCount = enqueuePendingOp(
            PendingSyncOp(
                id = System.nanoTime(),
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
        return InstantSaveResult(optimistic, pendingCount)
    }

    /**
     * INSTANT (0ms UI wait): Records a completed call locally and queues it for background sync.
     */
    fun applyCallHistoryLocallyAndQueue(
        currentPayload: SheetPayload,
        entry: HistoryEntry
    ): InstantSaveResult {
        val optimistic = currentPayload.copy(
            history = listOf(entry) + currentPayload.history,
            updatedAt = nowIso()
        )
        saveCachedPayload(optimistic)

        val pendingCount = enqueuePendingOp(
            PendingSyncOp(
                id = System.nanoTime(),
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
        return InstantSaveResult(optimistic, pendingCount)
    }

    /**
     * Serialized background sync (Mutex prevents collisions with Google Apps Script LockService).
     * Processes operations in order and removes each op the moment it succeeds.
     */
    suspend fun flushPendingQueue(config: AppConfig): FlushResult = syncMutex.withLock {
        if (config.demoMode || !config.isConnected) {
            return@withLock FlushResult(0, loadPendingOps().size)
        }

        var flushed = 0
        var lastError: String? = null

        while (true) {
            val nextOp = loadPendingOps().firstOrNull() ?: break
            val res = if (nextOp.type == "logHistory") {
                api.logHistory(
                    scriptUrl = config.scriptUrl,
                    apiKey = config.apiKey,
                    sheetName = nextOp.sheetName,
                    entry = HistoryEntry(
                        timestamp = nextOp.createdAt,
                        caller = nextOp.caller,
                        rowIndex = nextOp.rowIndex,
                        leadName = nextOp.leadName,
                        phone = nextOp.phone,
                        action = nextOp.eventAction,
                        fieldChanged = nextOp.fieldChanged,
                        oldValue = nextOp.oldValue,
                        newValue = nextOp.newValue,
                        callDuration = nextOp.callDuration
                    )
                )
            } else {
                api.updateRow(
                    scriptUrl = config.scriptUrl,
                    apiKey = config.apiKey,
                    sheetName = nextOp.sheetName,
                    rowIndex = nextOp.rowIndex,
                    fields = nextOp.fields,
                    caller = nextOp.caller,
                    leadName = nextOp.leadName,
                    phone = nextOp.phone,
                    callDuration = nextOp.callDuration
                )
            }

            if (res.isSuccess) {
                flushed++
                synchronized(queueLock) {
                    val remaining = loadPendingOps().filterNot { it.id == nextOp.id }
                    savePendingOps(remaining)
                }
            } else {
                lastError = res.exceptionOrNull()?.message
                break
            }
        }

        FlushResult(flushed, loadPendingOps().size, lastError)
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

    fun loadTodayCalledRows(): Map<Int, Long> {
        val savedDate = prefs.getString(KEY_TODAY_CALLS_DATE, "")
        val today = todayKey()
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_TODAY_CALLS_DATE, today)
                .remove(KEY_TODAY_CALLS_MAP)
                .apply()
            return emptyMap()
        }
        val raw = prefs.getString(KEY_TODAY_CALLS_MAP, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<Int, Long>>(raw) }.getOrElse { emptyMap() }
    }

    fun recordCalledToday(rowIndex: Int): Map<Int, Long> {
        val current = loadTodayCalledRows().toMutableMap()
        current[rowIndex] = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_TODAY_CALLS_DATE, todayKey())
            .putString(KEY_TODAY_CALLS_MAP, json.encodeToString(current))
            .apply()
        return current
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    companion object {
        private const val KEY_CONFIG = "app_config_json"
        private const val KEY_CACHE = "cached_sheet_payload_json"
        private const val KEY_PENDING_QUEUE = "offline_pending_sync_queue_json"
        private const val KEY_TODAY_CALLS_DATE = "today_calls_date_key"
        private const val KEY_TODAY_CALLS_MAP = "today_calls_map_json"
    }
}
