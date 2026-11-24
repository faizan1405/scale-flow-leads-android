package com.scaleflow.leads.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scaleflow.leads.data.AppConfig
import com.scaleflow.leads.data.AppUpdater
import com.scaleflow.leads.data.AvailableUpdate
import com.scaleflow.leads.data.CallLogHelper
import com.scaleflow.leads.data.HistoryEntry
import com.scaleflow.leads.data.LeadRepository
import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.SheetPayload
import com.scaleflow.leads.data.SyncLogEntry
import com.scaleflow.leads.data.appendNotePreservingCase
import com.scaleflow.leads.data.displayValue
import com.scaleflow.leads.data.formatLastCallTimestamp
import com.scaleflow.leads.data.isTimestampFromDate
import com.scaleflow.leads.data.rawValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BottomTab {
    LEADS,
    FOLLOW_UPS,
    CALLS,
    SETTINGS
}

object LeadFilterEngine {
    fun filterAndSort(
        rows: List<LeadRow>,
        schema: SchemaMapping,
        searchQuery: String,
        selectedStatusFilter: String,
        selectedAssigneeFilter: String,
        selectedFollowUpFilter: String,
        callerProfile: String,
        sortBy: String
    ): List<LeadRow> {
        val q = searchQuery.trim().lowercase()
        val qRowNum = q.removePrefix("#").toIntOrNull()

        val filtered = rows.filter { row ->
            val matchesQuery = if (q.isEmpty()) {
                true
            } else {
                val matchesRowIndex = (qRowNum != null && row.rowIndex == qRowNum) ||
                    "#${row.rowIndex}".contains(q)
                val matchesAnyField = row.fields.values.any { it.trim().lowercase().contains(q) }
                matchesRowIndex || matchesAnyField
            }

            val statusVal = row.displayValue(schema.statusCol)
            val matchesStatus = when (selectedStatusFilter) {
                "All" -> true
                else -> statusVal.equals(selectedStatusFilter.trim(), ignoreCase = true)
            }

            val assignedVal = row.displayValue(schema.assignedCol)
            val matchesAssignee = when (selectedAssigneeFilter) {
                "All" -> true
                "My Leads" -> callerProfile.isNotBlank() &&
                    assignedVal.equals(callerProfile.trim(), ignoreCase = true)
                "Unassigned" -> assignedVal.isBlank()
                else -> assignedVal.equals(selectedAssigneeFilter.trim(), ignoreCase = true)
            }

            val followVal = row.displayValue(schema.followUpCol)
            val matchesFollowUp = when (selectedFollowUpFilter) {
                "All" -> true
                "None" -> followVal.isBlank()
                else -> followVal.equals(selectedFollowUpFilter.trim(), ignoreCase = true)
            }

            matchesQuery && matchesStatus && matchesAssignee && matchesFollowUp
        }

        return when (sortBy) {
            "Name (A-Z)" -> filtered.sortedBy { it.displayValue(schema.nameCol).lowercase() }
            "Row # (Newest first)" -> filtered.sortedByDescending { it.rowIndex }
            "Follow-Ups" -> filtered.sortedByDescending { it.displayValue(schema.followUpCol).isNotBlank() }
            "Last Call" -> filtered.sortedByDescending { it.displayValue(schema.lastCallCol).isNotBlank() }
            else -> filtered.sortedBy { it.rowIndex }
        }
    }
}

data class LeadsUiState(
    val config: AppConfig = AppConfig(),
    val payload: SheetPayload = SheetPayload(),
    val schema: SchemaMapping = SchemaMapping.detect(emptyList()),
    val activeTab: BottomTab = BottomTab.LEADS,
    val detailLeadRowIndex: Int? = null,
    val detailOrderedRowIds: List<Int> = emptyList(),
    val postCallLeadRowIndex: Int? = null,
    val lastDetectedCallDuration: String = "",
    val searchQuery: String = "",
    val selectedStatusFilter: String = "All",
    val selectedAssigneeFilter: String = "All",
    val selectedFollowUpFilter: String = "All",
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastSyncedTime: String = "",
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val showEditAllSheet: Boolean = false,
    val showLogsDialog: Boolean = false,
    val availableUpdate: AvailableUpdate? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentVersion: String = "1.5.3",
    val logs: List<SyncLogEntry> = emptyList(),
    val todayCalledRowTimes: Map<Int, Long> = emptyMap()
) {
    val detailLead: LeadRow?
        get() = detailLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val detailPagerLeads: List<LeadRow>
        get() {
            val byId = payload.rows.associateBy { it.rowIndex }
            val ordered = detailOrderedRowIds.mapNotNull { byId[it] }
            return ordered.ifEmpty { payload.rows.sortedBy { it.rowIndex } }
        }

    val postCallLead: LeadRow?
        get() = postCallLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    private fun dropdownFromSheet(colName: String?): List<String> {
        if (colName == null) return emptyList()
        return payload.dropdownOptions[colName].orEmpty()
    }

    val leadTypeOptions: List<String>
        get() {
            val fromValidation = dropdownFromSheet(schema.statusCol)
            val fromRows = payload.rows.map { it.displayValue(schema.statusCol) }.filter { it.isNotBlank() }
            val defaults = listOf("New", "Contacted", "Qualified", "Proposal sent", "Won", "lost")
            return dedupeIgnoreCase(fromValidation + fromRows + defaults)
        }

    val statusFilterOptions: List<String>
        get() = listOf("All") + leadTypeOptions

    val followUpDropdownOptions: List<String>
        get() {
            val fromValidation = dropdownFromSheet(schema.followUpCol)
            val defaults = listOf("1 done.", "2 done.", "3 done.", "4 done.", "5 done.", "Lost")
            val fromRows = payload.rows.map { it.displayValue(schema.followUpCol) }.filter { it.isNotBlank() }
            return dedupeIgnoreCase(fromValidation + defaults + fromRows)
        }

    val followUpFilterOptions: List<String>
        get() = listOf("All") + followUpDropdownOptions + listOf("None")

    /**
     * Merges Google Sheet Data Validation dropdown options (e.g. Arshi, Faizan, Saurabh, Open)
     * with existing cell values so newly added people appear immediately on Refresh!
     */
    val assigneeDropdownOptions: List<String>
        get() {
            val fromValidation = dropdownFromSheet(schema.assignedCol)
            val fromRows = payload.rows.map { it.displayValue(schema.assignedCol) }.filter { it.isNotBlank() }
            val defaults = listOf("Arshi", "Faizan", "Saurabh", "Open")
            return dedupeIgnoreCase(fromValidation + fromRows + defaults)
        }

    /**
     * Live lead counts for each assigned person (plus Unassigned & All).
     */
    fun countForAssignee(person: String): Int = when (person) {
        "All" -> payload.rows.size
        "My Leads" -> if (config.callerProfile.isBlank()) 0 else payload.rows.count {
            it.displayValue(schema.assignedCol).equals(config.callerProfile.trim(), ignoreCase = true)
        }
        "Unassigned" -> payload.rows.count { it.displayValue(schema.assignedCol).isBlank() }
        else -> payload.rows.count {
            it.displayValue(schema.assignedCol).equals(person.trim(), ignoreCase = true)
        }
    }

    val assigneeFilterOptions: List<String>
        get() = buildList {
            add("All")
            if (config.callerProfile.isNotBlank()) add("My Leads")
            addAll(assigneeDropdownOptions)
            add("Unassigned")
        }

    /**
     * ONLY real assigned team members (from Assined to dropdown, excluding "Open", "Follow up Date", "Extra notes").
     */
    val callerProfileOptions: List<String>
        get() = assigneeDropdownOptions.filter { !it.equals("Open", ignoreCase = true) }

    /**
     * ONLY note columns belonging to real assigned team members (e.g. Faizan, Saurabh, Arshi).
     * Strictly excludes Follow up Date, Extra notes, Follow-Ups, etc.
     */
    val callerNoteColumns: List<String>
        get() {
            val teamNames = callerProfileOptions.map { it.trim().lowercase() }.toSet()
            val matched = schema.noteColumnOptions.filter { it.trim().lowercase() in teamNames }
            return matched.ifEmpty { schema.noteColumnOptions }
        }

    val activeCallerName: String
        get() = config.callerProfile.trim().ifBlank { activeNoteColumn ?: "App User" }

    val activeNoteColumn: String?
        get() {
            val lockedProfile = config.callerProfile.trim()
            if (lockedProfile.isNotBlank()) {
                val matchedCol = callerNoteColumns.firstOrNull {
                    it.trim().equals(lockedProfile, ignoreCase = true)
                }
                if (matchedCol != null) return matchedCol
            }
            val saved = config.preferredNoteColumn
            if (saved.isNotBlank() && saved in callerNoteColumns) return saved
            return callerNoteColumns.firstOrNull()
        }

    val isNoteColumnLockedByProfile: Boolean
        get() {
            val locked = config.callerProfile.trim()
            if (locked.isBlank()) return false
            return callerNoteColumns.any { it.trim().equals(locked, ignoreCase = true) }
        }

    fun historyForLead(rowIndex: Int): List<HistoryEntry> =
        payload.history.filter { it.rowIndex == rowIndex }

    fun isLeadCalledToday(lead: LeadRow): Boolean {
        if (todayCalledRowTimes.containsKey(lead.rowIndex)) return true
        if (isTimestampFromDate(lead.displayValue(schema.lastCallCol))) return true
        return payload.history.any {
            it.rowIndex == lead.rowIndex &&
                it.action.equals("Call", ignoreCase = true) &&
                isTimestampFromDate(it.timestamp)
        }
    }

    val filteredLeads: List<LeadRow>
        get() = LeadFilterEngine.filterAndSort(
            rows = payload.rows,
            schema = schema,
            searchQuery = searchQuery,
            selectedStatusFilter = selectedStatusFilter,
            selectedAssigneeFilter = selectedAssigneeFilter,
            selectedFollowUpFilter = selectedFollowUpFilter,
            callerProfile = config.callerProfile,
            sortBy = config.sortBy
        )

    val filteredFollowUps: List<LeadRow>
        get() {
            val base = payload.rows.filter { row ->
                row.displayValue(schema.followUpCol).isNotBlank() ||
                    row.displayValue(schema.statusCol).equals("Qualified", ignoreCase = true) ||
                    row.displayValue(schema.statusCol).equals("Proposal sent", ignoreCase = true)
            }
            return LeadFilterEngine.filterAndSort(
                rows = base,
                schema = schema,
                searchQuery = searchQuery,
                selectedStatusFilter = selectedStatusFilter,
                selectedAssigneeFilter = selectedAssigneeFilter,
                selectedFollowUpFilter = selectedFollowUpFilter,
                callerProfile = config.callerProfile,
                sortBy = config.sortBy
            )
        }

    /**
     * Calls tab shows ONLY leads called TODAY (newest call first), resetting fresh every day!
     */
    val filteredCalls: List<LeadRow>
        get() {
            val todayHistoryRows = payload.history
                .filter { it.action.equals("Call", ignoreCase = true) && isTimestampFromDate(it.timestamp) }
                .map { it.rowIndex }
                .toSet()

            val todayBase = payload.rows.filter { row ->
                todayCalledRowTimes.containsKey(row.rowIndex) ||
                    todayHistoryRows.contains(row.rowIndex) ||
                    isTimestampFromDate(row.displayValue(schema.lastCallCol))
            }

            val filtered = LeadFilterEngine.filterAndSort(
                rows = todayBase,
                schema = schema,
                searchQuery = searchQuery,
                selectedStatusFilter = selectedStatusFilter,
                selectedAssigneeFilter = selectedAssigneeFilter,
                selectedFollowUpFilter = selectedFollowUpFilter,
                callerProfile = config.callerProfile,
                sortBy = config.sortBy
            )

            // Default newest call at the top when using Sheet order
            return if (config.sortBy == "Sheet order") {
                filtered.sortedByDescending { todayCalledRowTimes[it.rowIndex] ?: it.rowIndex.toLong() }
            } else {
                filtered
            }
        }

    private fun dedupeIgnoreCase(items: List<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        items.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotBlank()) {
                val key = trimmed.lowercase()
                if (!seen.containsKey(key)) seen[key] = trimmed
            }
        }
        return seen.values.toList()
    }
}

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = LeadRepository(appContext)
    private val updater = AppUpdater(appContext)
    private val _uiState = MutableStateFlow(LeadsUiState())
    val uiState: StateFlow<LeadsUiState> = _uiState.asStateFlow()

    private var autoSyncJob: Job? = null
    private var pendingCallRowIndex: Int? = null
    private var pendingCallPhone: String = ""
    private var pendingCallLeadName: String = ""
    private var pendingCallStartedAt: Long = 0L
    private var isForeground: Boolean = true

    init {
        val loadedConfig = repository.loadConfig().copy(selectedSheet = "Leads ")
        repository.saveConfig(loadedConfig)
        val cached = repository.loadCachedPayload() ?: SheetPayload()
        val pendingCount = repository.loadPendingOps().size
        val todayCalls = repository.loadTodayCalledRows()

        _uiState.update {
            it.copy(
                config = loadedConfig,
                payload = cached,
                schema = SchemaMapping.detect(cached.headers),
                pendingSyncCount = pendingCount,
                todayCalledRowTimes = todayCalls,
                lastSyncedTime = if (cached.rows.isNotEmpty()) formatShortTime() else "",
                currentVersion = updater.currentVersionName()
            )
        }

        addLog("INFO", "App started v${updater.currentVersionName()} (pending offline: $pendingCount)")
        refreshLeads(silent = false, showToast = false)
        startAutoSyncLoop()
        checkForAppUpdate(silent = true)
    }

    fun onCallLogPermissionResult(granted: Boolean) {
        addLog("INFO", if (granted) "Call Log permission granted (exact duration active)" else "Call Log permission denied (using approx timer)")
    }

    fun onAppResumed() {
        isForeground = true
        // Refresh today's call map so crossing midnight automatically clears Calls tab
        val freshTodayCalls = repository.loadTodayCalledRows()
        _uiState.update { it.copy(todayCalledRowTimes = freshTodayCalls) }

        val calledRowIdx = pendingCallRowIndex
        if (calledRowIdx != null) {
            val phone = pendingCallPhone
            val name = pendingCallLeadName
            val startedAt = pendingCallStartedAt
            pendingCallRowIndex = null

            // Open Post-Call Note sheet IMMEDIATELY (0ms wait), then detect exact duration & sync in background!
            _uiState.update {
                it.copy(
                    lastDetectedCallDuration = "",
                    postCallLeadRowIndex = if (it.config.autoOpenNotesAfterCall) calledRowIdx else it.postCallLeadRowIndex
                )
            }
            viewModelScope.launch {
                delay(350L)
                val duration = CallLogHelper.findRecentCallDuration(appContext, phone, startedAt)
                _uiState.update { it.copy(lastDetectedCallDuration = duration) }
                logCompletedCallInstantly(calledRowIdx, name, phone, duration)
            }
            return
        }
        if (_uiState.value.config.isConnected && !_uiState.value.config.demoMode) {
            refreshLeads(silent = true, showToast = false)
        }
    }

    fun onAppPaused() {
        isForeground = false
    }

    private fun logCompletedCallInstantly(rowIndex: Int, leadName: String, phone: String, duration: String) {
        val state = _uiState.value
        val entry = HistoryEntry(
            timestamp = formatLastCallTimestamp(),
            caller = state.activeCallerName,
            rowIndex = rowIndex,
            leadName = leadName,
            phone = phone,
            action = "Call",
            fieldChanged = "Phone Call",
            oldValue = "",
            newValue = "Talked: $duration",
            callDuration = duration
        )
        val saved = repository.applyCallHistoryLocallyAndQueue(state.payload, entry)
        addLog("OK", "Logged call #$rowIndex ($duration)")
        _uiState.update {
            it.copy(
                payload = saved.payload,
                pendingSyncCount = saved.pendingCount
            )
        }
        triggerBackgroundFlush()
    }

    private fun triggerBackgroundFlush() {
        val config = _uiState.value.config.copy(selectedSheet = "Leads ")
        viewModelScope.launch {
            val flush = repository.flushPendingQueue(config)
            if (flush.flushedCount > 0) {
                addLog("OK", "Background synced ${flush.flushedCount} change(s) to Sheet")
            } else if (flush.lastError != null) {
                addLog("INFO", "Sync queued offline (${flush.remainingCount} pending)")
            }
            _uiState.update {
                it.copy(
                    pendingSyncCount = flush.remainingCount,
                    lastSyncedTime = if (flush.flushedCount > 0) formatShortTime() else it.lastSyncedTime
                )
            }
        }
    }

    fun selectBottomTab(tab: BottomTab) {
        _uiState.update {
            it.copy(
                activeTab = tab,
                detailLeadRowIndex = null,
                postCallLeadRowIndex = null
            )
        }
    }

    fun openLeadDetails(rowIndex: Int, orderedRowIds: List<Int>) = _uiState.update {
        it.copy(
            detailLeadRowIndex = rowIndex,
            detailOrderedRowIds = orderedRowIds,
            postCallLeadRowIndex = null
        )
    }

    fun onDetailPagerPageChanged(rowIndex: Int) = _uiState.update {
        it.copy(detailLeadRowIndex = rowIndex)
    }

    fun closeLeadDetails() = _uiState.update { it.copy(detailLeadRowIndex = null) }
    fun openPostCallNote(rowIndex: Int) = _uiState.update {
        it.copy(postCallLeadRowIndex = rowIndex, lastDetectedCallDuration = "")
    }
    fun closePostCallNote() = _uiState.update {
        it.copy(postCallLeadRowIndex = null, lastDetectedCallDuration = "")
    }

    fun markLeadCalled(lead: LeadRow) {
        pendingCallRowIndex = lead.rowIndex
        pendingCallPhone = lead.displayValue(_uiState.value.schema.phoneCol)
        pendingCallLeadName = lead.displayValue(_uiState.value.schema.nameCol)
        pendingCallStartedAt = System.currentTimeMillis()
        val updatedToday = repository.recordCalledToday(lead.rowIndex)
        _uiState.update { it.copy(todayCalledRowTimes = updatedToday) }
        addLog("INFO", "Dialed lead #${lead.rowIndex} ($pendingCallLeadName)")
    }

    fun onSearchChanged(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun onStatusFilterSelected(status: String) = _uiState.update { it.copy(selectedStatusFilter = status) }
    fun onAssigneeFilterSelected(assignee: String) = _uiState.update { it.copy(selectedAssigneeFilter = assignee) }
    fun onFollowUpFilterSelected(followUp: String) = _uiState.update { it.copy(selectedFollowUpFilter = followUp) }

    fun clearAllFilters() = _uiState.update {
        it.copy(
            searchQuery = "",
            selectedStatusFilter = "All",
            selectedAssigneeFilter = "All",
            selectedFollowUpFilter = "All"
        )
    }

    fun openEditAllSheet() = _uiState.update { it.copy(showEditAllSheet = true) }
    fun dismissEditAllSheet() = _uiState.update { it.copy(showEditAllSheet = false) }
    fun openLogsDialog() = _uiState.update { it.copy(showLogsDialog = true) }
    fun dismissLogsDialog() = _uiState.update { it.copy(showLogsDialog = false) }
    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    fun updateTheme(dark: Boolean) = updateConfig { it.copy(darkTheme = dark) }
    fun updateFollowUpWhatsAppMsg(msg: String) = updateConfig { it.copy(followUpWhatsAppMessage = msg) }
    fun updatePortfolioWhatsAppMsg(msg: String) = updateConfig { it.copy(portfolioWhatsAppMessage = msg) }
    fun updateAutoOpenNotes(enabled: Boolean) = updateConfig { it.copy(autoOpenNotesAfterCall = enabled) }
    fun updateSortBy(sort: String) = updateConfig { it.copy(sortBy = sort) }
    fun updatePreferredNoteColumn(col: String) = updateConfig { it.copy(preferredNoteColumn = col) }

    fun updateCallerProfile(profile: String) {
        updateConfig { it.copy(callerProfile = profile.trim()) }
        if (profile.isBlank() && _uiState.value.selectedAssigneeFilter == "My Leads") {
            _uiState.update { it.copy(selectedAssigneeFilter = "All") }
        }
        addLog("INFO", if (profile.isBlank()) "Caller Profile unlocked" else "Caller Profile locked to $profile")
    }

    private fun updateConfig(transform: (AppConfig) -> AppConfig) {
        val updated = transform(_uiState.value.config).copy(selectedSheet = "Leads ")
        repository.saveConfig(updated)
        _uiState.update { it.copy(config = updated) }
    }

    fun refreshLeads(silent: Boolean = false, showToast: Boolean = !silent) {
        val config = _uiState.value.config.copy(selectedSheet = "Leads ")
        if (!silent) {
            checkForAppUpdate(silent = true)
        }
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            }

            val flush = repository.flushPendingQueue(config)
            if (flush.flushedCount > 0) {
                addLog("OK", "Flushed ${flush.flushedCount} offline item(s) to Sheet")
            }

            val result = repository.fetchLeads(config)
            result.onSuccess { payload ->
                addLog("OK", "Synced ${payload.rows.size} leads + ${payload.history.size} history rows")
                _uiState.update {
                    it.copy(
                        payload = payload,
                        schema = SchemaMapping.detect(payload.headers),
                        isSyncing = false,
                        pendingSyncCount = flush.remainingCount,
                        errorMessage = null,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = when {
                            flush.flushedCount > 0 -> "Synced ${flush.flushedCount} offline edit(s) · ${payload.rows.size} leads"
                            showToast -> "Refreshed · ${payload.rows.size} leads synced"
                            else -> it.snackbarMessage
                        }
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Sync failed: ${err.message}")
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingSyncCount = repository.loadPendingOps().size,
                        errorMessage = if (silent) it.errorMessage else "Offline — showing cached leads",
                        snackbarMessage = if (showToast) "Could not refresh (offline)" else it.snackbarMessage
                    )
                }
            }
        }
    }

    fun saveFollowUpChoice(rowIndex: Int, followUpValue: String) {
        val state = _uiState.value
        val followCol = state.schema.followUpCol ?: return
        saveLeadEdits(
            rowIndex = rowIndex,
            updatedFields = mapOf(followCol to followUpValue),
            closeSheet = false,
            autoStampLastCall = true,
            successMsg = "Follow-up set to $followUpValue"
        )
    }

    fun saveCallerNote(
        rowIndex: Int,
        targetColumn: String?,
        noteText: String,
        newStatus: String? = null,
        newFollowUp: String? = null
    ) {
        val state = _uiState.value
        val schema = state.schema
        val lead = state.payload.rows.firstOrNull { it.rowIndex == rowIndex } ?: return

        val safeCol = targetColumn?.takeIf { it in state.callerNoteColumns } ?: state.activeNoteColumn
        val fields = mutableMapOf<String, String>()

        if (safeCol != null && noteText.trim().isNotEmpty()) {
            val existing = lead.rawValue(safeCol)
            fields[safeCol] = appendNotePreservingCase(existing, noteText)
        }
        if (schema.statusCol != null && !newStatus.isNullOrBlank()) {
            fields[schema.statusCol] = newStatus.trim()
        }
        if (schema.followUpCol != null && !newFollowUp.isNullOrBlank()) {
            fields[schema.followUpCol] = newFollowUp.trim()
        }
        if (fields.isEmpty()) return

        if (schema.lastCallCol != null) {
            fields[schema.lastCallCol] = formatLastCallTimestamp()
        }

        val leadName = lead.displayValue(schema.nameCol)
        val phone = lead.displayValue(schema.phoneCol)
        val duration = state.lastDetectedCallDuration

        // INSTANT (0ms): Save locally, close sheet immediately, and sync in background!
        val instant = repository.applyUpdateLocallyAndQueue(
            currentPayload = state.payload,
            rowIndex = rowIndex,
            updatedFields = fields,
            caller = state.activeCallerName,
            leadName = leadName,
            phone = phone,
            callDuration = duration
        )
        val updatedToday = repository.recordCalledToday(rowIndex)
        addLog("INFO", "Saved note locally for #$rowIndex -> syncing in background")
        _uiState.update {
            it.copy(
                payload = instant.payload,
                isSaving = false,
                postCallLeadRowIndex = null,
                lastDetectedCallDuration = "",
                pendingSyncCount = instant.pendingCount,
                todayCalledRowTimes = updatedToday,
                snackbarMessage = "Saved! Syncing to Sheet..."
            )
        }
        triggerBackgroundFlush()
    }

    fun saveLeadEdits(
        rowIndex: Int,
        updatedFields: Map<String, String>,
        closeSheet: Boolean = true,
        autoStampLastCall: Boolean = true,
        successMsg: String = "Saved!"
    ) {
        val state = _uiState.value
        val schema = state.schema
        val lead = state.payload.rows.firstOrNull { it.rowIndex == rowIndex }
        val sanitized = updatedFields.filterKeys { !schema.isLockedColumn(it) }.toMutableMap()
        if (sanitized.isEmpty()) {
            _uiState.update { it.copy(showEditAllSheet = false) }
            return
        }

        val lastCallCol = schema.lastCallCol
        if (autoStampLastCall && lastCallCol != null) {
            sanitized[lastCallCol] = formatLastCallTimestamp()
        }

        val leadName = lead?.displayValue(schema.nameCol).orEmpty()
        val phone = lead?.displayValue(schema.phoneCol).orEmpty()

        // INSTANT (0ms): Update UI immediately and flush to Google Sheet in background!
        val instant = repository.applyUpdateLocallyAndQueue(
            currentPayload = state.payload,
            rowIndex = rowIndex,
            updatedFields = sanitized,
            caller = state.activeCallerName,
            leadName = leadName,
            phone = phone
        )
        val updatedToday = repository.recordCalledToday(rowIndex)
        addLog("INFO", "Updated #$rowIndex locally (${sanitized.keys.joinToString()}) -> syncing")
        _uiState.update {
            it.copy(
                payload = instant.payload,
                isSaving = false,
                pendingSyncCount = instant.pendingCount,
                todayCalledRowTimes = updatedToday,
                showEditAllSheet = if (closeSheet) false else it.showEditAllSheet,
                snackbarMessage = successMsg
            )
        }
        triggerBackgroundFlush()
    }

    fun checkForAppUpdate(silent: Boolean = false) {
        val repo = _uiState.value.config.githubRepo
        if (repo.isBlank()) return
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isCheckingUpdate = true) }
            val result = updater.checkForUpdate(repo)
            result.onSuccess { update ->
                if (update != null) addLog("INFO", "Update v${update.version} available")
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableUpdate = update,
                        snackbarMessage = if (!silent) {
                            if (update != null) "Update v${update.version} available!" else "Up to date (v${it.currentVersion})"
                        } else it.snackbarMessage
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Update check failed: ${err.message}")
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        snackbarMessage = if (!silent) "Update check failed: ${err.message}" else it.snackbarMessage
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingUpdate = true, downloadProgress = 0f) }
            val result = updater.downloadAndInstall(update.apkUrl) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            result.onSuccess {
                addLog("OK", "Downloaded v${update.version}")
                _uiState.update { it.copy(isDownloadingUpdate = false) }
            }.onFailure { err ->
                addLog("ERROR", "Download failed: ${err.message}")
                _uiState.update {
                    it.copy(isDownloadingUpdate = false, snackbarMessage = "Download failed: ${err.message}")
                }
            }
        }
    }

    private fun startAutoSyncLoop() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            while (isActive) {
                val intervalSec = _uiState.value.config.autoSyncSeconds.coerceIn(5, 300)
                delay(intervalSec * 1000L)
                val s = _uiState.value
                val editing = s.postCallLeadRowIndex != null || s.showEditAllSheet
                if (isForeground && !editing && !s.isSaving && s.config.isConnected && !s.config.demoMode) {
                    refreshLeads(silent = true, showToast = false)
                }
            }
        }
    }

    private fun addLog(level: String, message: String) {
        Log.i("ScaleFlowLeads", "[$level] $message")
        val entry = SyncLogEntry(formatShortTime(), level, message)
        _uiState.update { it.copy(logs = (listOf(entry) + it.logs).take(60)) }
    }

    private fun formatShortTime(): String =
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
}
