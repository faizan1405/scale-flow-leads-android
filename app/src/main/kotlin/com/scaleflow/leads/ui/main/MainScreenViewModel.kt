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
import com.scaleflow.leads.data.displayValue
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
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val lastSyncedTime: String = "",
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val showEditAllSheet: Boolean = false,
    val showLogsDialog: Boolean = false,
    val availableUpdate: AvailableUpdate? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentVersion: String = "1.2.0",
    val logs: List<SyncLogEntry> = emptyList()
) {
    val detailLead: LeadRow?
        get() = detailLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val postCallLead: LeadRow?
        get() = postCallLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val statusFilterOptions: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.statusCol) }
                .filter { it.isNotBlank() }
                .distinct()
            return (listOf("All", "Today") + fromSheet).distinct()
        }

    val assigneeFilterOptions: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.assignedCol) }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            return listOf("All") + fromSheet + listOf("Unassigned")
        }

    val activeNoteColumn: String?
        get() {
            val saved = config.preferredNoteColumn
            if (saved.isNotBlank() && saved in schema.allHeaders && !schema.isLockedColumn(saved)) {
                return saved
            }
            return schema.notesCol ?: schema.noteColumnOptions.firstOrNull()
        }

    fun applyFilters(source: List<LeadRow>): List<LeadRow> {
        val q = searchQuery.trim().lowercase()
        val filtered = source.filter { row ->
            val matchesQuery = if (q.isEmpty()) true else {
                row.fields.values.any { it.lowercase().contains(q) }
            }
            val statusVal = row.displayValue(schema.statusCol)
            val followVal = row.displayValue(schema.followUpCol)
            val matchesStatus = when (selectedStatusFilter) {
                "All" -> true
                "Today" -> followVal.contains("today", ignoreCase = true) ||
                    statusVal.contains("today", ignoreCase = true)
                else -> statusVal.equals(selectedStatusFilter, ignoreCase = true)
            }
            val assignedVal = row.displayValue(schema.assignedCol)
            val matchesAssignee = when (selectedAssigneeFilter) {
                "All" -> true
                "Unassigned" -> assignedVal.isBlank()
                else -> assignedVal.equals(selectedAssigneeFilter, ignoreCase = true)
            }
            matchesQuery && matchesStatus && matchesAssignee
        }
        return when (config.sortBy) {
            "Name (A-Z)" -> filtered.sortedBy { it.displayValue(schema.nameCol).lowercase() }
            "Follow-up date" -> filtered.sortedByDescending {
                it.displayValue(schema.followUpCol).isNotBlank()
            }
            "Last Call" -> filtered.sortedByDescending {
                it.displayValue(schema.lastCallCol).isNotBlank()
            }
            else -> filtered
        }
    }

    val filteredLeads: List<LeadRow>
        get() = applyFilters(payload.rows)

    val filteredFollowUps: List<LeadRow>
        get() {
            val base = payload.rows.filter { row ->
                val follow = row.displayValue(schema.followUpCol)
                val status = row.displayValue(schema.statusCol)
                (follow.isNotBlank() && !follow.equals("lost", ignoreCase = true)) ||
                    status.contains("follow", ignoreCase = true) ||
                    status.contains("qualified", ignoreCase = true) ||
                    status.contains("hot", ignoreCase = true)
            }
            return applyFilters(base)
        }

    val filteredCalls: List<LeadRow>
        get() = applyFilters(payload.rows.filter { it.displayValue(schema.lastCallCol).isNotBlank() })
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

        _uiState.update {
            it.copy(
                config = loadedConfig,
                payload = cached,
                schema = SchemaMapping.detect(cached.headers),
                lastSyncedTime = if (cached.rows.isNotEmpty()) formatShortTime() else "",
                currentVersion = updater.currentVersionName()
            )
        }

        addLog("INFO", "App started v${updater.currentVersionName()} (Leads tab only)")
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

    fun openLeadDetails(rowIndex: Int) {
        _uiState.update { it.copy(detailLeadRowIndex = rowIndex, postCallLeadRowIndex = null) }
    }

    fun closeLeadDetails() {
        _uiState.update { it.copy(detailLeadRowIndex = null) }
    }

    fun openPostCallNote(rowIndex: Int) {
        _uiState.update { it.copy(postCallLeadRowIndex = rowIndex) }
    }

    fun closePostCallNote() {
        _uiState.update { it.copy(postCallLeadRowIndex = null) }
    }

    fun markLeadCalled(rowIndex: Int) {
        pendingCallRowIndex = rowIndex
        addLog("INFO", "Dialed lead row #$rowIndex")
    }

    fun onSearchChanged(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun onStatusFilterSelected(status: String) = _uiState.update { it.copy(selectedStatusFilter = status) }
    fun onAssigneeFilterSelected(assignee: String) = _uiState.update { it.copy(selectedAssigneeFilter = assignee) }
    fun clearAllFilters() = _uiState.update {
        it.copy(searchQuery = "", selectedStatusFilter = "All", selectedAssigneeFilter = "All")
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
            val result = repository.fetchLeads(config)
            result.onSuccess { payload ->
                addLog("OK", "Synced ${payload.rows.size} leads from '${payload.sheetName}'")
                _uiState.update {
                    it.copy(
                        payload = payload,
                        schema = SchemaMapping.detect(payload.headers),
                        isSyncing = false,
                        errorMessage = null,
                        lastSyncedTime = formatShortTime()
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Sync failed: ${err.message}")
                _uiState.update {
                    it.copy(isSyncing = false, errorMessage = err.message ?: "Sync failed")
                }
            }
        }
    }

    /**
     * Instant 1-tap follow-up date update directly from Calendar DatePickerDialog.
     */
    fun saveInstantFollowUpDate(rowIndex: Int, formattedDate: String) {
        val state = _uiState.value
        val followCol = state.schema.followUpCol
        if (followCol == null) {
            _uiState.update { it.copy(snackbarMessage = "No Follow-up column found in Sheet") }
            return
        }
        val fields = mutableMapOf(followCol to formattedDate)
        state.schema.lastCallCol?.let {
            fields[it] = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        }
        saveLeadEdits(
            rowIndex = rowIndex,
            updatedFields = fields,
            closeSheet = false,
            successMsg = "Follow-up set to $formattedDate"
        )
    }

    fun saveCallerNote(rowIndex: Int, targetColumn: String?, noteText: String, newStatus: String? = null) {
        val state = _uiState.value
        val safeCol = targetColumn?.takeIf { !state.schema.isLockedColumn(it) }
            ?: state.activeNoteColumn
        if (noteText.isBlank() && newStatus.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.appendCallNote(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                noteColumn = safeCol,
                noteText = noteText,
                statusColumn = state.schema.statusCol,
                statusValue = newStatus
            )
            result.onSuccess { updated ->
                addLog("OK", "Saved note to '${safeCol ?: "Sheet"}' for row #$rowIndex")
                _uiState.update {
                    it.copy(
                        payload = updated,
                        isSaving = false,
                        postCallLeadRowIndex = null,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = "Saved to ${safeCol ?: "Sheet"}"
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Note save failed: ${err.message}")
                _uiState.update {
                    it.copy(isSaving = false, snackbarMessage = "Save failed: ${err.message}")
                }
            }
        }
    }

    /**
     * Updates lead fields while strictly enforcing read-only Name & Phone Number protection.
     */
    fun saveLeadEdits(
        rowIndex: Int,
        updatedFields: Map<String, String>,
        closeSheet: Boolean = true,
        successMsg: String = "Lead synced to Google Sheet"
    ) {
        val state = _uiState.value
        val sanitized = updatedFields.filterKeys { !state.schema.isLockedColumn(it) }
        if (sanitized.isEmpty()) {
            _uiState.update { it.copy(showEditAllSheet = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.updateLead(state.config, state.payload, rowIndex, sanitized)
            result.onSuccess { newPayload ->
                addLog("OK", "Updated row #$rowIndex (${sanitized.keys.joinToString()})")
                _uiState.update {
                    it.copy(
                        payload = newPayload,
                        isSaving = false,
                        showEditAllSheet = if (closeSheet) false else it.showEditAllSheet,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = successMsg
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Update failed: ${err.message}")
                _uiState.update {
                    it.copy(isSaving = false, snackbarMessage = "Save failed: ${err.message}")
                }
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
