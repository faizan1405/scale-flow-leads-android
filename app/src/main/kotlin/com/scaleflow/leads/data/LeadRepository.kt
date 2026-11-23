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

    fun loadPendingOps(): List<PendingSyncOp> {
        val raw = prefs.getString(KEY_PENDING_QUEUE, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingSyncOp>>(raw) }.getOrElse { emptyList() }
    }

    private fun savePendingOps(ops: List<PendingSyncOp>) {
        prefs.edit().putString(KEY_PENDING_QUEUE, json.encodeToString(ops)).apply()
    }

    fun enqueuePendingOp(sheetName: String, rowIndex: Int, fields: Map<String, String>): Int {
        val current = loadPendingOps().toMutableList()
        // Merge with existing pending op for the same sheet + row so queue stays compact & ordered
        val existingIdx = current.indexOfFirst { it.sheetName == sheetName && it.rowIndex == rowIndex }
        if (existingIdx != -1) {
            val existing = current[existingIdx]
            current[existingIdx] = existing.copy(
                fields = existing.fields + fields,
                createdAt = nowIso()
            )
        } else {
            current.add(
                PendingSyncOp(
                    id = System.currentTimeMillis(),
                    sheetName = sheetName,
                    rowIndex = rowIndex,
                    fields = fields,
                    createdAt = nowIso()
                )
            )
        }
        savePendingOps(current)
        return current.size
    }

    /**
     * Flushes all queued offline edits in order to Google Sheets.
     */
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
            val res = api.updateRow(
                scriptUrl = config.scriptUrl,
                apiKey = config.apiKey,
                sheetName = op.sheetName,
                rowIndex = op.rowIndex,
                fields = op.fields
            )
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

    /**
     * Fetches live leads and overlays any unsynced local offline edits so local work is never overwritten.
     */
    suspend fun fetchLeads(config: AppConfig): Result<SheetPayload> {
        if (config.demoMode || !config.isConnected) {
            return Result.success(loadCachedPayload() ?: SheetPayload())
        }
        val result = api.fetchSheet(config.scriptUrl, config.apiKey, config.selectedSheet)
        return result.map { remote ->
            val pending = loadPendingOps().filter { it.sheetName == remote.sheetName }
            val merged = if (pending.isEmpty()) {
                remote
            } else {
                val pendingByRow = pending.associateBy { it.rowIndex }
                remote.copy(
                    rows = remote.rows.map { row ->
                        val p = pendingByRow[row.rowIndex]
                        if (p != null) row.copy(fields = row.fields + p.fields) else row
                    }
                )
            }
            saveCachedPayload(merged)
            merged
        }
    }

    /**
     * Updates lead fields immediately if online, or queues cleanly for offline sync if network fails.
     */
    suspend fun updateLeadWithOfflineQueue(
        config: AppConfig,
        currentPayload: SheetPayload,
        rowIndex: Int,
        updatedFields: Map<String, String>
    ): SaveOutcome {
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
            return SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        }

        val apiResult = api.updateRow(
            scriptUrl = config.scriptUrl,
            apiKey = config.apiKey,
            sheetName = currentPayload.sheetName,
            rowIndex = rowIndex,
            fields = updatedFields
        )

        return if (apiResult.isSuccess) {
            SaveOutcome(optimistic, syncedImmediately = true, pendingCount = loadPendingOps().size)
        } else {
            val count = enqueuePendingOp(currentPayload.sheetName, rowIndex, updatedFields)
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
