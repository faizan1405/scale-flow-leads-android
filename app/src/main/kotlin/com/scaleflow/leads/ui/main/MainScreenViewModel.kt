package com.scaleflow.leads.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scaleflow.leads.data.AppConfig
import com.scaleflow.leads.data.AppUpdater
import com.scaleflow.leads.data.AvailableUpdate
import com.scaleflow.leads.data.LeadRepository
import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.SheetPayload
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

data class LeadsUiState(
    val config: AppConfig = AppConfig(),
    val payload: SheetPayload = SheetPayload(),
    val schema: SchemaMapping = SchemaMapping.detect(emptyList()),
    val searchQuery: String = "",
    val selectedStatus: String = "All",
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val lastSyncedTime: String = "",
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val editingLead: LeadRow? = null,
    val postCallLead: LeadRow? = null,
    val showAddLeadSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val availableUpdate: AvailableUpdate? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentVersion: String = "1.0.0"
) {
    val availableStatuses: List<String>
        get() {
            val fromSheet = payload.rows
                .map { it.displayValue(schema.statusCol) }
                .filter { it.isNotBlank() }
                .distinct()
            val defaults = listOf("New", "Callback", "Interested", "No Answer", "Not Interested", "Closed")
            return (listOf("All") + (fromSheet + defaults).distinct())
        }

    val quickStatuses: List<String>
        get() = availableStatuses.filter { it != "All" }

    val filteredLeads: List<LeadRow>
        get() {
            val q = searchQuery.trim().lowercase()
            return payload.rows.filter { row ->
                val matchesStatus = if (selectedStatus == "All") {
                    true
                } else {
                    row.displayValue(schema.statusCol).equals(selectedStatus, ignoreCase = true)
                }
                val matchesQuery = if (q.isEmpty()) {
                    true
                } else {
                    row.fields.values.any { it.lowercase().contains(q) }
                }
                matchesStatus && matchesQuery
            }
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
        val config = repository.loadConfig()
        val cached = repository.loadCachedPayload() ?: if (config.demoMode || !config.isConnected) {
            repository.demoPayload()
        } else {
            SheetPayload()
        }

        _uiState.update {
            it.copy(
                config = config,
                payload = cached,
                schema = SchemaMapping.detect(cached.headers),
                lastSyncedTime = if (cached.rows.isNotEmpty()) formatNow() else "",
                currentVersion = updater.currentVersionName()
            )
        }

        refreshLeads(silent = false)
        startAutoSyncLoop()
        checkForAppUpdate(silent = true)
    }

    fun onAppResumed() {
        isForeground = true
        val calledRowIdx = pendingCallRowIndex
        if (calledRowIdx != null) {
            pendingCallRowIndex = null
            val lead = _uiState.value.payload.rows.firstOrNull { it.rowIndex == calledRowIdx }
            if (lead != null) {
                _uiState.update { it.copy(postCallLead = lead) }
            }
        }
        if (_uiState.value.config.isConnected && !_uiState.value.config.demoMode) {
            refreshLeads(silent = true)
        }
    }

    fun onAppPaused() {
        isForeground = false
    }

    fun markLeadCalled(rowIndex: Int) {
        pendingCallRowIndex = rowIndex
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusSelected(status: String) {
        _uiState.update { it.copy(selectedStatus = status) }
    }

    fun openEditLead(lead: LeadRow) {
        _uiState.update { it.copy(editingLead = lead) }
    }

    fun dismissEditLead() {
        _uiState.update { it.copy(editingLead = null) }
    }

    fun openPostCallLog(lead: LeadRow) {
        _uiState.update { it.copy(postCallLead = lead) }
    }

    fun dismissPostCallLog() {
        _uiState.update { it.copy(postCallLead = null) }
    }

    fun openAddLead() {
        _uiState.update { it.copy(showAddLeadSheet = true) }
    }

    fun dismissAddLead() {
        _uiState.update { it.copy(showAddLeadSheet = false) }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    fun dismissSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun selectSheetTab(sheetName: String) {
        val updated = _uiState.value.config.copy(selectedSheet = sheetName)
        repository.saveConfig(updated)
        _uiState.update { it.copy(config = updated, selectedStatus = "All") }
        refreshLeads(silent = false)
    }

    fun saveSettings(
        scriptUrl: String,
        apiKey: String,
        autoSyncSeconds: Int,
        demoMode: Boolean,
        githubRepo: String
    ) {
        val cleanUrl = scriptUrl.trim()
        val updated = _uiState.value.config.copy(
            scriptUrl = cleanUrl,
            apiKey = apiKey.trim(),
            autoSyncSeconds = autoSyncSeconds.coerceIn(5, 300),
            demoMode = demoMode || !cleanUrl.startsWith("https://"),
            selectedSheet = "Leads ",
            githubRepo = githubRepo.trim()
        )
        repository.saveConfig(updated)
        _uiState.update {
            it.copy(
                config = updated,
                showSettingsSheet = false,
                selectedStatus = "All",
                errorMessage = null
            )
        }
        startAutoSyncLoop()
        refreshLeads(silent = false)
        checkForAppUpdate(silent = true)
    }

    fun checkForAppUpdate(silent: Boolean = false) {
        val repo = _uiState.value.config.githubRepo
        if (repo.isBlank()) return
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isCheckingUpdate = true) }
            val result = updater.checkForUpdate(repo)
            result.onSuccess { update ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        availableUpdate = update,
                        snackbarMessage = if (!silent) {
                            if (update != null) "Update v${update.version} available!" else "App is up to date (v${it.currentVersion})"
                        } else it.snackbarMessage
                    )
                }
            }.onFailure { err ->
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
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        snackbarMessage = "Installer opened for v${update.version}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        snackbarMessage = "Download failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(availableUpdate = null) }
    }

    fun refreshLeads(silent: Boolean = false) {
        val config = _uiState.value.config
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            }
            val result = repository.fetchLeads(config)
            result.onSuccess { payload ->
                _uiState.update {
                    it.copy(
                        payload = payload,
                        schema = SchemaMapping.detect(payload.headers),
                        isSyncing = false,
                        errorMessage = null,
                        lastSyncedTime = formatNow()
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = err.message ?: "Sync failed"
                    )
                }
            }
        }
    }

    fun saveLeadEdits(rowIndex: Int, updatedFields: Map<String, String>) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.updateLead(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                updatedFields = updatedFields
            )
            result.onSuccess { newPayload ->
                _uiState.update {
                    it.copy(
                        payload = newPayload,
                        isSaving = false,
                        editingLead = null,
                        lastSyncedTime = formatNow(),
                        snackbarMessage = "Lead synced to Google Sheet"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Could not save changes",
                        snackbarMessage = "Save failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun savePostCallLog(rowIndex: Int, noteText: String, newStatus: String?) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.appendCallNote(
                config = state.config,
                currentPayload = state.payload,
                rowIndex = rowIndex,
                noteColumn = state.schema.notesCol,
                noteText = noteText,
                statusColumn = state.schema.statusCol,
                statusValue = newStatus
            )
            result.onSuccess { newPayload ->
                _uiState.update {
                    it.copy(
                        payload = newPayload,
                        isSaving = false,
                        postCallLead = null,
                        lastSyncedTime = formatNow(),
                        snackbarMessage = "Call note saved & synced"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Could not save call note",
                        snackbarMessage = "Sync failed: ${err.message}"
                    )
                }
            }
        }
    }

    fun addNewLead(fields: Map<String, String>) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.addLead(
                config = state.config,
                currentPayload = state.payload,
                fields = fields
            )
            result.onSuccess { newPayload ->
                _uiState.update {
                    it.copy(
                        payload = newPayload,
                        isSaving = false,
                        showAddLeadSheet = false,
                        lastSyncedTime = formatNow(),
                        snackbarMessage = "New lead added to Google Sheet"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Could not add lead",
                        snackbarMessage = "Add failed: ${err.message}"
                    )
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
                val modalOpen = s.editingLead != null || s.postCallLead != null || s.showAddLeadSheet || s.showSettingsSheet
                if (isForeground && !modalOpen && !s.isSaving && s.config.isConnected && !s.config.demoMode) {
                    refreshLeads(silent = true)
                }
            }
        }
    }

    private fun formatNow(): String =
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
}
