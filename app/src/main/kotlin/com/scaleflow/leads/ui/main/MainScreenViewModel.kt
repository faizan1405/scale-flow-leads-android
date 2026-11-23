package com.scaleflow.leads.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scaleflow.leads.data.AppConfig
import com.scaleflow.leads.data.AppUpdater
import com.scaleflow.leads.data.AvailableUpdate
import com.scaleflow.leads.data.LeadRepository
import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.SheetPayload
import com.scaleflow.leads.data.SyncLogEntry
import com.scaleflow.leads.data.appendNotePreservingCase
import com.scaleflow.leads.data.displayValue
import com.scaleflow.leads.data.formatLastCallTimestamp
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
    val postCallLeadRowIndex: Int? = null,
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
    val currentVersion: String = "1.3.0",
    val logs: List<SyncLogEntry> = emptyList()
) {
    val detailLead: LeadRow?
        get() = detailLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val postCallLead: LeadRow?
        get() = postCallLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val leadTypeOptions: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.statusCol) }
                .filter { it.isNotBlank() }
            val defaults = listOf("New", "Contacted", "Qualified", "Proposal sent", "Won", "lost")
            return dedupeIgnoreCase(fromSheet + defaults)
        }

    val statusFilterOptions: List<String>
        get() = listOf("All") + leadTypeOptions

    val followUpDropdownOptions: List<String>
        get() {
            val defaults = listOf("1 done.", "2 done.", "3 done.", "4 done.", "5 done.", "Lost")
            val fromSheet = payload.rows
                .map { it.displayValue(schema.followUpCol) }
                .filter { it.isNotBlank() }
            return dedupeIgnoreCase(defaults + fromSheet)
        }

    val followUpFilterOptions: List<String>
        get() = listOf("All") + followUpDropdownOptions + listOf("None")

    val assigneeDropdownOptions: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.assignedCol) }
                .filter { it.isNotBlank() }
            val defaults = listOf("Faizan", "Saurabh", "Open")
            return dedupeIgnoreCase(fromSheet + defaults)
        }

    val assigneeFilterOptions: List<String>
        get() = buildList {
            add("All")
            if (config.callerProfile.isNotBlank()) add("My Leads")
            addAll(assigneeDropdownOptions)
            add("Unassigned")
        }

    val callerProfileOptions: List<String>
        get() = dedupeIgnoreCase(schema.noteColumnOptions + assigneeDropdownOptions)

    /**
     * Resolves which note column to write into:
     * 1. Caller Profile Lock (if locked to a profile that matches a real note column)
     * 2. User's last picked note column
     * 3. Default detected note column (strictly never Follow-Ups)
     */
    val activeNoteColumn: String?
        get() {
            val lockedProfile = config.callerProfile.trim()
            if (lockedProfile.isNotBlank()) {
                val matchedCol = schema.noteColumnOptions.firstOrNull {
                    it.trim().equals(lockedProfile, ignoreCase = true)
                }
                if (matchedCol != null) return matchedCol
            }
            val saved = config.preferredNoteColumn
            if (saved.isNotBlank() && saved in schema.noteColumnOptions) {
                return saved
            }
            return schema.notesCol ?: schema.noteColumnOptions.firstOrNull()
        }

    val isNoteColumnLockedByProfile: Boolean
        get() {
            val locked = config.callerProfile.trim()
            if (locked.isBlank()) return false
            return schema.noteColumnOptions.any { it.trim().equals(locked, ignoreCase = true) }
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

    val filteredCalls: List<LeadRow>
        get() {
            val base = payload.rows.filter { it.displayValue(schema.lastCallCol).isNotBlank() }
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

    private val repository = LeadRepository(application.applicationContext)
    private val updater = AppUpdater(application.applicationContext)
    private val _uiState = MutableStateFlow(LeadsUiState())
    val uiState: StateFlow<LeadsUiState> = _uiState.asStateFlow()

    private var autoSyncJob: Job? = null
    private var pendingCallRowIndex: Int? = null
    private var isForeground: Boolean = true

    init {
        val loadedConfig = repository.loadConfig().copy(selectedSheet = "Leads ")
        repository.saveConfig(loadedConfig)
        val cached = repository.loadCachedPayload() ?: SheetPayload()
        val pendingCount = repository.loadPendingOps().size

        _uiState.update {
            it.copy(
                config = loadedConfig,
                payload = cached,
                schema = SchemaMapping.detect(cached.headers),
                pendingSyncCount = pendingCount,
                lastSyncedTime = if (cached.rows.isNotEmpty()) formatShortTime() else "",
                currentVersion = updater.currentVersionName()
            )
        }

        addLog("INFO", "App started v${updater.currentVersionName()} (pending offline: $pendingCount)")
        refreshLeads(silent = false)
        startAutoSyncLoop()
        checkForAppUpdate(silent = true)
    }

    fun onAppResumed() {
        isForeground = true
        val calledRowIdx = pendingCallRowIndex
        if (calledRowIdx != null) {
            pendingCallRowIndex = null
            if (_uiState.value.config.autoOpenNotesAfterCall) {
                _uiState.update { it.copy(postCallLeadRowIndex = calledRowIdx) }
            }
        }
        if (_uiState.value.config.isConnected && !_uiState.value.config.demoMode) {
            refreshLeads(silent = true)
        }
    }

    fun onAppPaused() {
        isForeground = false
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

    fun openLeadDetails(rowIndex: Int) = _uiState.update {
        it.copy(detailLeadRowIndex = rowIndex, postCallLeadRowIndex = null)
    }

    fun closeLeadDetails() = _uiState.update { it.copy(detailLeadRowIndex = null) }
    fun openPostCallNote(rowIndex: Int) = _uiState.update { it.copy(postCallLeadRowIndex = rowIndex) }
    fun closePostCallNote() = _uiState.update { it.copy(postCallLeadRowIndex = null) }

    fun markLeadCalled(rowIndex: Int) {
        pendingCallRowIndex = rowIndex
        addLog("INFO", "Dialed lead #$rowIndex")
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

    fun refreshLeads(silent: Boolean = false) {
        val config = _uiState.value.config.copy(selectedSheet = "Leads ")
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            }

            // First flush any queued offline edits!
            val flush = repository.flushPendingQueue(config)
            if (flush.flushedCount > 0) {
                addLog("OK", "Flushed ${flush.flushedCount} offline change(s) to Sheet")
            }

            val result = repository.fetchLeads(config)
            result.onSuccess { payload ->
                addLog("OK", "Synced ${payload.rows.size} leads from '${payload.sheetName}'")
                _uiState.update {
                    it.copy(
                        payload = payload,
                        schema = SchemaMapping.detect(payload.headers),
                        isSyncing = false,
                        pendingSyncCount = flush.remainingCount,
                        errorMessage = null,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = if (flush.flushedCount > 0) {
                            "Synced ${flush.flushedCount} offline edit(s) to Sheet"
                        } else it.snackbarMessage
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Sync failed (offline?): ${err.message}")
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        pendingSyncCount = repository.loadPendingOps().size,
                        errorMessage = if (silent) it.errorMessage else "Offline — showing saved leads"
                    )
                }
            }
        }
    }

    /**
     * Updates Follow-Ups dropdown (1 done. .. 5 done. / Lost) and auto-updates Last Call!
     */
    fun saveFollowUpChoice(rowIndex: Int, followUpValue: String) {
        val state = _uiState.value
        val followCol = state.schema.followUpCol ?: return
        val fields = mutableMapOf(followCol to followUpValue)
        state.schema.lastCallCol?.let { fields[it] = formatLastCallTimestamp() }
        saveLeadEdits(
            rowIndex = rowIndex,
            updatedFields = fields,
            closeSheet = false,
            autoStampLastCall = false,
            successMsg = "Follow-up set to $followUpValue"
        )
    }

    /**
     * Appends exact user note on a new line (NO timestamp prefix, exact casing preserved)
     * and AUTOMATICALLY writes current time/date to Column B ("Last Call") in format:
     * "02:45 PM, 24 Nov 2026".
     */
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

        val safeCol = targetColumn?.takeIf { it in schema.noteColumnOptions } ?: state.activeNoteColumn
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

        // Automatically update Column B ("Last Call") in exact format: 02:45 PM, 24 Nov 2026
        if (schema.lastCallCol != null) {
            fields[schema.lastCallCol] = formatLastCallTimestamp()
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val outcome = repository.updateLeadWithOfflineQueue(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                updatedFields = fields
            )
            if (outcome.syncedImmediately) {
                addLog("OK", "Saved note to '${safeCol ?: "Sheet"}' + Last Call on #$rowIndex")
            } else {
                addLog("INFO", "Queued offline note for #$rowIndex (${outcome.pendingCount} pending)")
            }
            _uiState.update {
                it.copy(
                    payload = outcome.payload,
                    isSaving = false,
                    postCallLeadRowIndex = null,
                    pendingSyncCount = outcome.pendingCount,
                    lastSyncedTime = if (outcome.syncedImmediately) formatShortTime() else it.lastSyncedTime,
                    snackbarMessage = if (outcome.syncedImmediately) {
                        "Saved to ${safeCol ?: "Sheet"} · Last Call updated"
                    } else {
                        "Saved offline — will auto-sync when online"
                    }
                )
            }
        }
    }

    /**
     * Updates lead fields (strictly locks Name & Numbers, auto-updates Last Call, and queues offline if needed).
     */
    fun saveLeadEdits(
        rowIndex: Int,
        updatedFields: Map<String, String>,
        closeSheet: Boolean = true,
        autoStampLastCall: Boolean = true,
        successMsg: String = "Lead synced to Google Sheet"
    ) {
        val state = _uiState.value
        val lead = state.payload.rows.firstOrNull { it.rowIndex == rowIndex }
        val sanitized = updatedFields.filterKeys { !state.schema.isLockedColumn(it) }.toMutableMap()
        if (sanitized.isEmpty()) {
            _uiState.update { it.copy(showEditAllSheet = false) }
            return
        }

        val lastCallCol = state.schema.lastCallCol
        if (autoStampLastCall && lastCallCol != null) {
            val prevLastCall = lead?.rawValue(lastCallCol).orEmpty()
            val submittedLastCall = sanitized[lastCallCol]
            // If user didn't manually pick a different Last Call date, auto-stamp exact current time & date!
            if (submittedLastCall == null || submittedLastCall == prevLastCall) {
                sanitized[lastCallCol] = formatLastCallTimestamp()
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val outcome = repository.updateLeadWithOfflineQueue(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                updatedFields = sanitized
            )
            if (outcome.syncedImmediately) {
                addLog("OK", "Updated #$rowIndex (${sanitized.keys.joinToString()})")
            } else {
                addLog("INFO", "Queued offline edit for #$rowIndex (${outcome.pendingCount} pending)")
            }
            _uiState.update {
                it.copy(
                    payload = outcome.payload,
                    isSaving = false,
                    pendingSyncCount = outcome.pendingCount,
                    showEditAllSheet = if (closeSheet) false else it.showEditAllSheet,
                    lastSyncedTime = if (outcome.syncedImmediately) formatShortTime() else it.lastSyncedTime,
                    snackbarMessage = if (outcome.syncedImmediately) {
                        successMsg
                    } else {
                        "Saved offline — will auto-sync when online"
                    }
                )
            }
        }
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
                    refreshLeads(silent = true)
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
