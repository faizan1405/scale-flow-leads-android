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
    val followUpLeadRowIndex: Int? = null,
    val searchQuery: String = "",
    val selectedFilter: String = "All",
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val lastSyncedTime: String = "",
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val showAddLeadSheet: Boolean = false,
    val showEditAllSheet: Boolean = false,
    val showLogsDialog: Boolean = false,
    val confirmCallLead: LeadRow? = null,
    val availableUpdate: AvailableUpdate? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentVersion: String = "1.1.0",
    val logs: List<SyncLogEntry> = emptyList()
) {
    val detailLead: LeadRow?
        get() = detailLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val followUpLead: LeadRow?
        get() = followUpLeadRowIndex?.let { idx -> payload.rows.firstOrNull { it.rowIndex == idx } }

    val filterChips: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.statusCol) }
                .filter { it.isNotBlank() }
                .distinct()
            val preset = listOf("All", "Today", "Follow-up", "Assigned")
            return (preset + fromSheet).distinct()
        }

    val statusOptions: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.statusCol) }
                .filter { it.isNotBlank() }
                .distinct()
            val defaults = listOf("Follow-up", "New", "Qualified", "Hot Lead", "Enquiry", "Done", "Lost")
            return (fromSheet + defaults).distinct()
        }

    val filteredLeads: List<LeadRow>
        get() {
            val q = searchQuery.trim().lowercase()
            val base = payload.rows.filter { row ->
                val matchesQuery = if (q.isEmpty()) true else {
                    row.fields.values.any { it.lowercase().contains(q) }
                }
                val statusVal = row.displayValue(schema.statusCol)
                val followVal = row.displayValue(schema.followUpCol)
                val assignedVal = row.displayValue(schema.assignedCol)
                val matchesFilter = when (selectedFilter) {
                    "All" -> true
                    "Today" -> followVal.contains("today", ignoreCase = true) ||
                        statusVal.contains("today", ignoreCase = true)
                    "Follow-up" -> followVal.isNotBlank() &&
                        !followVal.equals("lost", ignoreCase = true) &&
                        !followVal.equals("done", ignoreCase = true)
                    "Assigned" -> assignedVal.isNotBlank()
                    else -> statusVal.equals(selectedFilter, ignoreCase = true)
                }
                matchesQuery && matchesFilter
            }
            return sortRows(base)
        }

    val followUpLeads: List<LeadRow>
        get() {
            val list = payload.rows.filter { row ->
                val follow = row.displayValue(schema.followUpCol)
                val status = row.displayValue(schema.statusCol)
                (follow.isNotBlank() && !follow.equals("lost", ignoreCase = true)) ||
                    status.contains("follow", ignoreCase = true) ||
                    status.contains("qualified", ignoreCase = true) ||
                    status.contains("hot", ignoreCase = true)
            }
            return sortRows(list)
        }

    val calledLeads: List<LeadRow>
        get() = payload.rows.filter { it.displayValue(schema.lastCallCol).isNotBlank() }

    private fun sortRows(rows: List<LeadRow>): List<LeadRow> = when (config.sortBy) {
        "Name (A-Z)" -> rows.sortedBy { it.displayValue(schema.nameCol).lowercase() }
        "Follow-up date (earliest first)" -> rows.sortedByDescending {
            it.displayValue(schema.followUpCol).isNotBlank()
        }
        "Last Call (newest first)" -> rows.sortedByDescending {
            it.displayValue(schema.lastCallCol).isNotBlank()
        }
        else -> rows
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
                _uiState.update { it.copy(followUpLeadRowIndex = calledRowIdx) }
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
                followUpLeadRowIndex = null
            )
        }
    }

    fun openLeadDetails(rowIndex: Int) {
        _uiState.update { it.copy(detailLeadRowIndex = rowIndex, followUpLeadRowIndex = null) }
    }

    fun closeLeadDetails() {
        _uiState.update { it.copy(detailLeadRowIndex = null) }
    }

    fun openUpdateFollowUp(rowIndex: Int) {
        _uiState.update { it.copy(followUpLeadRowIndex = rowIndex) }
    }

    fun closeUpdateFollowUp() {
        _uiState.update { it.copy(followUpLeadRowIndex = null) }
    }

    fun requestCall(lead: LeadRow, onProceedToDial: (String) -> Unit) {
        val phone = lead.displayValue(_uiState.value.schema.phoneCol)
        if (phone.isBlank()) return
        if (_uiState.value.config.confirmBeforeCall) {
            _uiState.update { it.copy(confirmCallLead = lead) }
        } else {
            markLeadCalled(lead.rowIndex)
            onProceedToDial(phone)
        }
    }

    fun dismissConfirmCall() {
        _uiState.update { it.copy(confirmCallLead = null) }
    }

    fun markLeadCalled(rowIndex: Int) {
        pendingCallRowIndex = rowIndex
        addLog("INFO", "Dialed lead row #$rowIndex")
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterSelected(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun openAddLead() = _uiState.update { it.copy(showAddLeadSheet = true) }
    fun dismissAddLead() = _uiState.update { it.copy(showAddLeadSheet = false) }
    fun openEditAllSheet() = _uiState.update { it.copy(showEditAllSheet = true) }
    fun dismissEditAllSheet() = _uiState.update { it.copy(showEditAllSheet = false) }
    fun openLogsDialog() = _uiState.update { it.copy(showLogsDialog = true) }
    fun dismissLogsDialog() = _uiState.update { it.copy(showLogsDialog = false) }
    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    fun updateTheme(dark: Boolean) = updateConfig { it.copy(darkTheme = dark) }
    fun updateWhatsAppTemplate(msg: String) = updateConfig { it.copy(defaultWhatsAppMessage = msg) }
    fun updateConfirmBeforeCall(enabled: Boolean) = updateConfig { it.copy(confirmBeforeCall = enabled) }
    fun updateAutoOpenNotes(enabled: Boolean) = updateConfig { it.copy(autoOpenNotesAfterCall = enabled) }
    fun updateSortBy(sort: String) = updateConfig { it.copy(sortBy = sort) }

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
                    it.copy(
                        isSyncing = false,
                        errorMessage = err.message ?: "Sync failed"
                    )
                }
            }
        }
    }

    fun markCalledNow(rowIndex: Int) {
        val state = _uiState.value
        val lastCallCol = state.schema.lastCallCol
        if (lastCallCol == null) {
            _uiState.update { it.copy(snackbarMessage = "Marked called") }
            return
        }
        val stamp = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        saveLeadEdits(rowIndex, mapOf(lastCallCol to stamp), closeSheet = false, successMsg = "Marked called ($stamp)")
    }

    fun saveCallerNoteOnly(rowIndex: Int, noteText: String) {
        val state = _uiState.value
        if (noteText.isBlank()) return
        val targetCol = state.schema.callerNotesCol ?: state.schema.notesCol
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.appendCallNote(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                noteColumn = targetCol,
                noteText = noteText,
                statusColumn = null,
                statusValue = null
            )
            result.onSuccess { updated ->
                addLog("OK", "Saved caller note for row #$rowIndex")
                _uiState.update {
                    it.copy(
                        payload = updated,
                        isSaving = false,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = "Caller note synced to Sheet"
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

    fun saveFollowUpUpdate(
        rowIndex: Int,
        followUpDate: String,
        followUpTime: String,
        status: String,
        callerNote: String
    ) {
        val state = _uiState.value
        val schema = state.schema
        val fields = mutableMapOf<String, String>()

        val combinedFollowUp = listOf(followUpDate.trim(), followUpTime.trim())
            .filter { it.isNotBlank() }
            .joinToString(", ")

        if (schema.followUpCol != null && combinedFollowUp.isNotBlank()) {
            fields[schema.followUpCol] = combinedFollowUp
        }
        if (schema.statusCol != null && status.isNotBlank()) {
            fields[schema.statusCol] = status
        }
        if (schema.lastCallCol != null) {
            fields[schema.lastCallCol] = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            var currentPayload = state.payload

            if (fields.isNotEmpty()) {
                val updateRes = repository.updateLead(state.config, currentPayload, rowIndex, fields)
                updateRes.onSuccess { currentPayload = it }.onFailure { err ->
                    addLog("ERROR", "Follow-up update failed: ${err.message}")
                    _uiState.update { it.copy(isSaving = false, snackbarMessage = "Update failed: ${err.message}") }
                    return@launch
                }
            }

            if (callerNote.isNotBlank()) {
                val noteCol = schema.callerNotesCol ?: schema.notesCol
                val noteRes = repository.appendCallNote(
                    config = state.config,
                    currentPayload = currentPayload,
                    rowIndex = rowIndex,
                    noteColumn = noteCol,
                    noteText = callerNote,
                    statusColumn = null,
                    statusValue = null
                )
                noteRes.onSuccess { currentPayload = it }.onFailure { err ->
                    addLog("ERROR", "Caller note failed: ${err.message}")
                }
            }

            addLog("OK", "Updated follow-up for row #$rowIndex ($status)")
            _uiState.update {
                it.copy(
                    payload = currentPayload,
                    isSaving = false,
                    followUpLeadRowIndex = null,
                    lastSyncedTime = formatShortTime(),
                    snackbarMessage = "Follow-up & notes synced"
                )
            }
        }
    }

    fun saveLeadEdits(
        rowIndex: Int,
        updatedFields: Map<String, String>,
        closeSheet: Boolean = true,
        successMsg: String = "Lead synced to Google Sheet"
    ) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.updateLead(state.config, state.payload, rowIndex, updatedFields)
            result.onSuccess { newPayload ->
                addLog("OK", "Updated row #$rowIndex")
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

    fun addNewLead(fields: Map<String, String>) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.addLead(state.config, state.payload, fields)
            result.onSuccess { newPayload ->
                addLog("OK", "Added new lead to Sheet")
                _uiState.update {
                    it.copy(
                        payload = newPayload,
                        isSaving = false,
                        showAddLeadSheet = false,
                        lastSyncedTime = formatShortTime(),
                        snackbarMessage = "New lead added to Google Sheet"
                    )
                }
            }.onFailure { err ->
                addLog("ERROR", "Add lead failed: ${err.message}")
                _uiState.update {
                    it.copy(isSaving = false, snackbarMessage = "Add failed: ${err.message}")
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
                if (update != null) addLog("INFO", "Update v${update.version} available on GitHub")
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableUpdate = update,
                        snackbarMessage = if (!silent) {
                            if (update != null) "Update v${update.version} available!" else "You're on the latest version (v${it.currentVersion})"
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
                addLog("OK", "Downloaded v${update.version} installer")
                _uiState.update { it.copy(isDownloadingUpdate = false) }
            }.onFailure { err ->
                addLog("ERROR", "Update download failed: ${err.message}")
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
                val editing = s.followUpLeadRowIndex != null || s.showAddLeadSheet || s.showEditAllSheet
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
