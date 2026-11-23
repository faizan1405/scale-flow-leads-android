package com.scaleflow.leads.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.SyncLogEntry
import com.scaleflow.leads.data.displayValue
import com.scaleflow.leads.data.initials
import com.scaleflow.leads.ui.theme.BrandBlue
import com.scaleflow.leads.ui.theme.BrandBlueContainer
import com.scaleflow.leads.ui.theme.CallGreen
import com.scaleflow.leads.ui.theme.CallGreenContainer
import com.scaleflow.leads.ui.theme.CallGreenDarkText
import com.scaleflow.leads.ui.theme.StatusAmber
import com.scaleflow.leads.ui.theme.StatusAmberContainer
import com.scaleflow.leads.ui.theme.StatusBlue
import com.scaleflow.leads.ui.theme.StatusBlueContainer
import com.scaleflow.leads.ui.theme.StatusRed
import com.scaleflow.leads.ui.theme.StatusRedContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PortfolioTeal = Color(0xFF0D9488)

@Composable
fun MainScreen(viewModel: MainScreenViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    BackHandler(enabled = state.detailLead != null) {
        viewModel.closeLeadDetails()
    }

    val detailLead = state.detailLead
    if (detailLead != null) {
        LeadDetailsScreen(
            lead = detailLead,
            schema = state.schema,
            activeNoteColumn = state.activeNoteColumn,
            isNoteColumnLockedByProfile = state.isNoteColumnLockedByProfile,
            callerProfile = state.config.callerProfile,
            followUpOptions = state.followUpDropdownOptions,
            isSaving = state.isSaving,
            pendingSyncCount = state.pendingSyncCount,
            snackbarHostState = snackbarHostState,
            onBack = { viewModel.closeLeadDetails() },
            onCall = {
                val phone = detailLead.displayValue(state.schema.phoneCol)
                if (phone.isNotBlank()) {
                    viewModel.markLeadCalled(detailLead.rowIndex)
                    launchDialer(context, phone)
                }
            },
            onWhatsAppFollowUp = {
                val phone = detailLead.displayValue(state.schema.phoneCol)
                val name = detailLead.displayValue(state.schema.nameCol)
                launchWhatsApp(context, phone, state.config.followUpWhatsAppMessage, name)
            },
            onWhatsAppPortfolio = {
                val phone = detailLead.displayValue(state.schema.phoneCol)
                val name = detailLead.displayValue(state.schema.nameCol)
                launchWhatsApp(context, phone, state.config.portfolioWhatsAppMessage, name)
            },
            onSelectFollowUp = { choice ->
                viewModel.saveFollowUpChoice(detailLead.rowIndex, choice)
            },
            onCopyPhone = { phone -> copyToClipboard(context, phone) },
            onSelectNoteColumn = { col -> viewModel.updatePreferredNoteColumn(col) },
            onSaveCallerNote = { col, note -> viewModel.saveCallerNote(detailLead.rowIndex, col, note) },
            onOpenEditAll = { viewModel.openEditAllSheet() }
        )
    } else {
        HomeScaffold(
            state = state,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            onCallLead = { lead ->
                val phone = lead.displayValue(state.schema.phoneCol)
                if (phone.isNotBlank()) {
                    viewModel.markLeadCalled(lead.rowIndex)
                    launchDialer(context, phone)
                }
            },
            onWhatsAppFollowUp = { lead ->
                val phone = lead.displayValue(state.schema.phoneCol)
                val name = lead.displayValue(state.schema.nameCol)
                launchWhatsApp(context, phone, state.config.followUpWhatsAppMessage, name)
            },
            onWhatsAppPortfolio = { lead ->
                val phone = lead.displayValue(state.schema.phoneCol)
                val name = lead.displayValue(state.schema.nameCol)
                launchWhatsApp(context, phone, state.config.portfolioWhatsAppMessage, name)
            }
        )
    }

    // Post-Call Quick Note Bottom Sheet (auto-opens after call ends)
    val postCallLead = state.postCallLead
    if (postCallLead != null) {
        PostCallQuickNoteSheet(
            lead = postCallLead,
            schema = state.schema,
            activeNoteColumn = state.activeNoteColumn,
            isNoteColumnLockedByProfile = state.isNoteColumnLockedByProfile,
            statusOptions = state.leadTypeOptions,
            followUpOptions = state.followUpDropdownOptions,
            isSaving = state.isSaving,
            onSelectNoteColumn = { viewModel.updatePreferredNoteColumn(it) },
            onDismiss = { viewModel.closePostCallNote() },
            onSave = { col, note, status, followUp ->
                viewModel.saveCallerNote(postCallLead.rowIndex, col, note, status, followUp)
            }
        )
    }

    // Edit Lead Details Sheet with Smart Pickers (Calendar for Dates, Dropdowns for Type/Follow-Ups/Assigned, Locked Name & Number!)
    if (state.showEditAllSheet && detailLead != null) {
        EditEditableFieldsSheet(
            schema = state.schema,
            initialFields = detailLead.fields,
            leadTypeOptions = state.leadTypeOptions,
            followUpOptions = state.followUpDropdownOptions,
            assigneeOptions = state.assigneeDropdownOptions,
            isSaving = state.isSaving,
            onDismiss = { viewModel.dismissEditAllSheet() },
            onSave = { viewModel.saveLeadEdits(detailLead.rowIndex, it) }
        )
    }

    // Sync & Activity Logs Dialog
    if (state.showLogsDialog) {
        SyncLogsDialog(
            logs = state.logs,
            onCopyAll = {
                val text = state.logs.joinToString("\n") { "${it.timestamp} [${it.level}] ${it.message}" }
                copyToClipboard(context, text)
            },
            onDismiss = { viewModel.dismissLogsDialog() }
        )
    }
}

@Composable
private fun HomeScaffold(
    state: LeadsUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: MainScreenViewModel,
    onCallLead: (LeadRow) -> Unit,
    onWhatsAppFollowUp: (LeadRow) -> Unit,
    onWhatsAppPortfolio: (LeadRow) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = state.activeTab == BottomTab.LEADS,
                    onClick = { viewModel.selectBottomTab(BottomTab.LEADS) },
                    icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                    label = { Text("Leads") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlueContainer
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == BottomTab.FOLLOW_UPS,
                    onClick = { viewModel.selectBottomTab(BottomTab.FOLLOW_UPS) },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Follow-ups") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlueContainer
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == BottomTab.CALLS,
                    onClick = { viewModel.selectBottomTab(BottomTab.CALLS) },
                    icon = { Icon(Icons.Default.Call, null) },
                    label = { Text("Calls") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlueContainer
                    )
                )
                NavigationBarItem(
                    selected = state.activeTab == BottomTab.SETTINGS,
                    onClick = { viewModel.selectBottomTab(BottomTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandBlue,
                        selectedTextColor = BrandBlue,
                        indicatorColor = BrandBlueContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                BottomTab.LEADS -> LeadsListTab(
                    title = "Scale Flow Leads",
                    state = state,
                    leads = state.filteredLeads,
                    emptyTitle = "No leads match your filters",
                    viewModel = viewModel,
                    onCall = onCallLead,
                    onWhatsAppFollowUp = onWhatsAppFollowUp,
                    onWhatsAppPortfolio = onWhatsAppPortfolio
                )

                BottomTab.FOLLOW_UPS -> LeadsListTab(
                    title = "Follow-ups",
                    state = state,
                    leads = state.filteredFollowUps,
                    emptyTitle = "No follow-ups match your filters",
                    viewModel = viewModel,
                    onCall = onCallLead,
                    onWhatsAppFollowUp = onWhatsAppFollowUp,
                    onWhatsAppPortfolio = onWhatsAppPortfolio
                )

                BottomTab.CALLS -> LeadsListTab(
                    title = "Recent Calls",
                    state = state,
                    leads = state.filteredCalls,
                    emptyTitle = "No calls match your filters",
                    viewModel = viewModel,
                    onCall = onCallLead,
                    onWhatsAppFollowUp = onWhatsAppFollowUp,
                    onWhatsAppPortfolio = onWhatsAppPortfolio
                )

                BottomTab.SETTINGS -> SettingsTabScreen(
                    state = state,
                    onSelectTheme = { viewModel.updateTheme(it) },
                    onSelectCallerProfile = { viewModel.updateCallerProfile(it) },
                    onUpdateFollowUpMsg = { viewModel.updateFollowUpWhatsAppMsg(it) },
                    onUpdatePortfolioMsg = { viewModel.updatePortfolioWhatsAppMsg(it) },
                    onToggleAutoOpenNotes = { viewModel.updateAutoOpenNotes(it) },
                    onCheckUpdate = { viewModel.checkForAppUpdate(silent = false) },
                    onOpenLogs = { viewModel.openLogsDialog() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadsListTab(
    title: String,
    state: LeadsUiState,
    leads: List<LeadRow>,
    emptyTitle: String,
    viewModel: MainScreenViewModel,
    onCall: (LeadRow) -> Unit,
    onWhatsAppFollowUp: (LeadRow) -> Unit,
    onWhatsAppPortfolio: (LeadRow) -> Unit
) {
    var assigneeMenuOpen by remember { mutableStateOf(false) }
    var followUpMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val sortOptions = listOf("Sheet order", "Row # (Newest first)", "Name (A-Z)", "Follow-Ups", "Last Call")
    val hasActiveFilters = state.searchQuery.isNotBlank() ||
        state.selectedStatusFilter != "All" ||
        state.selectedAssigneeFilter != "All" ||
        state.selectedFollowUpFilter != "All"

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.5.dp
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                state.pendingSyncCount > 0 -> StatusAmber
                                                state.errorMessage != null -> StatusRed
                                                else -> CallGreen
                                            }
                                        )
                                )
                                val profileLabel = if (state.config.callerProfile.isNotBlank()) {
                                    " · ${state.config.callerProfile}"
                                } else ""
                                Text(
                                    text = if (state.isSyncing) {
                                        "Syncing Leads tab..."
                                    } else {
                                        "Leads tab · ${leads.size} shown$profileLabel"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.refreshLeads(silent = false) }) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                        }
                    }
                }

                // Offline Pending Sync Queue Banner
                if (state.pendingSyncCount > 0) {
                    Surface(color = StatusAmberContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudOff, null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${state.pendingSyncCount} edit(s) saved offline · Waiting to sync",
                                    color = Color(0xFF92400E),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Sync Now",
                                color = BrandBlue,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.refreshLeads(silent = false) }
                            )
                        }
                    }
                }

                // Auto-Update Banner
                val update = state.availableUpdate
                if (update != null) {
                    Surface(color = CallGreenContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (state.isDownloadingUpdate) {
                                    "Downloading v${update.version} (${(state.downloadProgress * 100).toInt()}%)..."
                                } else {
                                    "Update v${update.version} ready"
                                },
                                color = CallGreenDarkText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate() },
                                enabled = !state.isDownloadingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Update", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (state.errorMessage != null && state.pendingSyncCount == 0) {
                    Surface(color = StatusRedContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.errorMessage,
                            color = StatusRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // Search bar (matches #row, Name, Phone, Notes, Assigned, Status)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    placeholder = { Text("Search #row, name, number, notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // Combined Filter & Sort Row: Assigned | Follow-up | Sort | Clear
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Assigned filter dropdown
                    item {
                        Box {
                            val active = state.selectedAssigneeFilter != "All"
                            FilterDropdownPill(
                                icon = Icons.Default.FilterList,
                                label = if (active) "Assigned: ${state.selectedAssigneeFilter}" else "Assigned: All",
                                active = active,
                                onClick = { assigneeMenuOpen = true }
                            )
                            DropdownMenu(
                                expanded = assigneeMenuOpen,
                                onDismissRequest = { assigneeMenuOpen = false }
                            ) {
                                state.assigneeFilterOptions.forEach { person ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (person) {
                                                    "All" -> "All Assignees"
                                                    "My Leads" -> "My Leads (${state.config.callerProfile})"
                                                    "Unassigned" -> "Unassigned Only"
                                                    else -> "Only $person"
                                                },
                                                fontWeight = if (person == state.selectedAssigneeFilter) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = if (person == state.selectedAssigneeFilter) {
                                            { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                        } else null,
                                        onClick = {
                                            viewModel.onAssigneeFilterSelected(person)
                                            assigneeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Follow-Up count filter dropdown (1 done. .. 5 done. / Lost / None)
                    item {
                        Box {
                            val active = state.selectedFollowUpFilter != "All"
                            FilterDropdownPill(
                                icon = Icons.Default.Repeat,
                                label = if (active) "Follow-up: ${state.selectedFollowUpFilter}" else "Follow-up: All",
                                active = active,
                                onClick = { followUpMenuOpen = true }
                            )
                            DropdownMenu(
                                expanded = followUpMenuOpen,
                                onDismissRequest = { followUpMenuOpen = false }
                            ) {
                                state.followUpFilterOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (opt == "All") "All Follow-ups" else opt,
                                                fontWeight = if (opt == state.selectedFollowUpFilter) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        trailingIcon = if (opt == state.selectedFollowUpFilter) {
                                            { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                        } else null,
                                        onClick = {
                                            viewModel.onFollowUpFilterSelected(opt)
                                            followUpMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sort dropdown
                    item {
                        Box {
                            FilterDropdownPill(
                                icon = Icons.AutoMirrored.Filled.Sort,
                                label = state.config.sortBy,
                                active = state.config.sortBy != "Sheet order",
                                onClick = { sortMenuOpen = true }
                            )
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                sortOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        trailingIcon = if (opt == state.config.sortBy) {
                                            { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                        } else null,
                                        onClick = {
                                            viewModel.updateSortBy(opt)
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (hasActiveFilters) {
                        item {
                            Text(
                                text = "Clear All",
                                color = BrandBlue,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.clearAllFilters() }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Lead Type chips (combine cleanly with Search + Assigned + Follow-Up!)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.statusFilterOptions) { chip ->
                        val selected = state.selectedStatusFilter.equals(chip, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { viewModel.onStatusFilterSelected(chip) }
                        ) {
                            Text(
                                text = chip,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // Pull-to-Refresh Lead Cards
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = { viewModel.refreshLeads(silent = false) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (leads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasActiveFilters) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.clearAllFilters() }) {
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(leads, key = { it.rowIndex }) { lead ->
                        LeadListCard(
                            lead = lead,
                            schema = state.schema,
                            onClick = { viewModel.openLeadDetails(lead.rowIndex) },
                            onCall = { onCall(lead) },
                            onWhatsAppFollowUp = { onWhatsAppFollowUp(lead) },
                            onWhatsAppPortfolio = { onWhatsAppPortfolio(lead) },
                            onQuickNote = { viewModel.openPostCallNote(lead.rowIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDropdownPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) BrandBlueContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (active) BorderStroke(1.dp, BrandBlue) else null,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (active) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) BrandBlue else MaterialTheme.colorScheme.onSurface
            )
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LeadListCard(
    lead: LeadRow,
    schema: SchemaMapping,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsAppFollowUp: () -> Unit,
    onWhatsAppPortfolio: () -> Unit,
    onQuickNote: () -> Unit
) {
    val name = lead.displayValue(schema.nameCol).ifBlank { "Row #${lead.rowIndex}" }
    val phone = lead.displayValue(schema.phoneCol)
    val status = lead.displayValue(schema.statusCol)
    val assigned = lead.displayValue(schema.assignedCol)
    val lastCall = lead.displayValue(schema.lastCallCol)
    val followUp = lead.displayValue(schema.followUpCol)

    val badgeText = status.ifBlank { followUp }
    val (accentColor, badgeBg, badgeFg) = statusPalette(badgeText)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Exact Google Sheet Row Number Badge (#2, #3, #4...)
                        Surface(
                            color = BrandBlueContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#${lead.rowIndex}",
                                color = BrandBlue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (badgeText.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                text = badgeText,
                                color = badgeFg,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (phone.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        val meta = buildList {
                            if (status.isNotBlank()) add(status)
                            if (assigned.isNotBlank()) add("Assigned: $assigned")
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (phone.isNotBlank()) BrandBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = phone.isNotBlank()) { onCall() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (phone.isNotBlank()) CallGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = phone.isNotBlank()) { onWhatsAppFollowUp() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, "WA Follow-up", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (phone.isNotBlank()) PortfolioTeal else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = phone.isNotBlank()) { onWhatsAppPortfolio() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WorkOutline, "WA Portfolio", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onQuickNote() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Notes,
                                "Note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                val footer = buildList {
                    if (lastCall.isNotBlank()) add("Last Call: $lastCall")
                    if (followUp.isNotBlank()) add("Follow-up: $followUp")
                }.joinToString(" · ")
                if (footer.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = footer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// LEAD DETAILS SCREEN
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LeadDetailsScreen(
    lead: LeadRow,
    schema: SchemaMapping,
    activeNoteColumn: String?,
    isNoteColumnLockedByProfile: Boolean,
    callerProfile: String,
    followUpOptions: List<String>,
    isSaving: Boolean,
    pendingSyncCount: Int,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onWhatsAppFollowUp: () -> Unit,
    onWhatsAppPortfolio: () -> Unit,
    onSelectFollowUp: (String) -> Unit,
    onCopyPhone: (String) -> Unit,
    onSelectNoteColumn: (String) -> Unit,
    onSaveCallerNote: (targetColumn: String?, note: String) -> Unit,
    onOpenEditAll: () -> Unit
) {
    val name = lead.displayValue(schema.nameCol).ifBlank { "Row #${lead.rowIndex}" }
    val phone = lead.displayValue(schema.phoneCol)
    val leadType = lead.displayValue(schema.statusCol)
    val currentFollowUp = lead.displayValue(schema.followUpCol)

    var callerNoteInput by remember(lead.rowIndex) { mutableStateOf("") }
    var followUpDropdownExpanded by remember { mutableStateOf(false) }
    val (_, badgeBg, badgeFg) = statusPalette(leadType)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lead #${lead.rowIndex}", fontWeight = FontWeight.Bold)
                        if (pendingSyncCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(color = StatusAmberContainer, shape = RoundedCornerShape(999.dp)) {
                                Text(
                                    text = "Offline ($pendingSyncCount)",
                                    color = StatusAmber,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenEditAll) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Profile Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BrandBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lead.initials(schema.nameCol),
                        color = BrandBlue,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (phone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onCopyPhone(phone) },
                                tint = BrandBlue
                            )
                        }
                    }
                }
                if (leadType.isNotBlank()) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = leadType,
                            color = badgeFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2x2 Instant Action Grid:
            // Call Now | Follow-up Dropdown (1 done. .. 5 done. / Lost — NO DatePicker!)
            // WA Follow-up | WA Portfolio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCall,
                    enabled = phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Call, null, Modifier.size(18.dp))
                        Text("Call Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { followUpDropdownExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, null, Modifier.size(16.dp), tint = BrandBlue)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp), tint = BrandBlue)
                            }
                            Text(
                                text = currentFollowUp.ifBlank { "Follow-up" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = followUpDropdownExpanded,
                        onDismissRequest = { followUpDropdownExpanded = false }
                    ) {
                        followUpOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        fontWeight = if (option.equals(currentFollowUp, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                trailingIcon = if (option.equals(currentFollowUp, ignoreCase = true)) {
                                    { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                } else null,
                                onClick = {
                                    followUpDropdownExpanded = false
                                    onSelectFollowUp(option)
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onWhatsAppFollowUp,
                    enabled = phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                        Text("WA Follow-up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = onWhatsAppPortfolio,
                    enabled = phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PortfolioTeal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WorkOutline, null, Modifier.size(18.dp))
                        Text("WA Portfolio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Dynamic Future-Proof Info Card (shows every column in the Sheet!)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lead Information (Row #${lead.rowIndex})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Edit fields",
                            color = BrandBlue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onOpenEditAll() }
                        )
                    }
                    HorizontalDivider()
                    schema.allHeaders.forEach { header ->
                        val value = lead.fields[header]?.trim().orEmpty()
                        val isLocked = schema.isLockedColumn(header)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.width(115.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLocked) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = value.ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Add Call Note Card (Follow-Ups strictly excluded, preserves exact casing, NO timestamp prefix, auto-updates Last Call!)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CallGreen.copy(alpha = 0.35f))
            ) {
                Column {
                    Surface(color = CallGreenContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Add Call Note",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CallGreenDarkText
                                )
                                if (isNoteColumnLockedByProfile) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, null, tint = CallGreenDarkText, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = callerProfile,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CallGreenDarkText
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Adds on a new line (keeps old notes) & auto-updates Last Call.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CallGreenDarkText.copy(alpha = 0.85f)
                            )
                            if (!isNoteColumnLockedByProfile && schema.noteColumnOptions.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    schema.noteColumnOptions.forEach { col ->
                                        val selected = col == activeNoteColumn
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (selected) CallGreen else Color.White,
                                            modifier = Modifier.clickable { onSelectNoteColumn(col) }
                                        ) {
                                            Text(
                                                text = col,
                                                color = if (selected) Color.White else CallGreenDarkText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = callerNoteInput,
                        onValueChange = { callerNoteInput = it },
                        placeholder = { Text("Write note for '${activeNoteColumn ?: "Notes"}'...") },
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val text = callerNoteInput.trim()
                    if (text.isNotEmpty()) {
                        onSaveCallerNote(activeNoteColumn, text)
                        callerNoteInput = ""
                    }
                },
                enabled = !isSaving && callerNoteInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Note to ${activeNoteColumn ?: "Sheet"}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================================
// POST-CALL QUICK NOTE SHEET (Auto-opens after call ends)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PostCallQuickNoteSheet(
    lead: LeadRow,
    schema: SchemaMapping,
    activeNoteColumn: String?,
    isNoteColumnLockedByProfile: Boolean,
    statusOptions: List<String>,
    followUpOptions: List<String>,
    isSaving: Boolean,
    onSelectNoteColumn: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (targetCol: String?, noteText: String, newStatus: String?, newFollowUp: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val leadName = lead.displayValue(schema.nameCol).ifBlank { "Lead #${lead.rowIndex}" }
    var selectedStatus by remember(lead.rowIndex) { mutableStateOf(lead.displayValue(schema.statusCol)) }
    var selectedFollowUp by remember(lead.rowIndex) { mutableStateOf(lead.displayValue(schema.followUpCol)) }
    var noteText by remember(lead.rowIndex) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Post-Call Note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "#${lead.rowIndex} · $leadName (Last Call auto-updates on save)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            if (!isNoteColumnLockedByProfile && schema.noteColumnOptions.isNotEmpty()) {
                Text("Save note to column:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    schema.noteColumnOptions.forEach { col ->
                        FilterChip(
                            selected = col == activeNoteColumn,
                            onClick = { onSelectNoteColumn(col) },
                            label = { Text(col) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (schema.statusCol != null && statusOptions.isNotEmpty()) {
                Text("Lead Type:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusOptions.forEach { st ->
                        FilterChip(
                            selected = selectedStatus.equals(st, ignoreCase = true),
                            onClick = { selectedStatus = st },
                            label = { Text(st) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (schema.followUpCol != null && followUpOptions.isNotEmpty()) {
                Text("Follow-Ups:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    followUpOptions.forEach { fu ->
                        FilterChip(
                            selected = selectedFollowUp.equals(fu, ignoreCase = true),
                            onClick = { selectedFollowUp = fu },
                            label = { Text(fu) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Call note (saved to ${activeNoteColumn ?: "Notes"})") },
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Skip")
                }
                Button(
                    onClick = {
                        onSave(
                            activeNoteColumn,
                            noteText,
                            selectedStatus.ifBlank { null },
                            selectedFollowUp.ifBlank { null }
                        )
                    },
                    modifier = Modifier.weight(1.5f),
                    enabled = !isSaving && (noteText.isNotBlank() || selectedStatus.isNotBlank() || selectedFollowUp.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text(if (isSaving) "Saving..." else "Save & Sync")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================================
// EDIT LEAD DETAILS SHEET — Smart Pickers (NO unwanted keyboard!)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEditableFieldsSheet(
    schema: SchemaMapping,
    initialFields: Map<String, String>,
    leadTypeOptions: List<String>,
    followUpOptions: List<String>,
    assigneeOptions: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember(initialFields) {
        mutableStateMapOf<String, String>().apply {
            schema.allHeaders.forEach { h -> put(h, initialFields[h].orEmpty()) }
        }
    }

    // Track which Date column is currently picking a date
    var activeDatePickerColumn by remember { mutableStateOf<String?>(null) }

    if (activeDatePickerColumn != null) {
        val targetCol = activeDatePickerColumn!!
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { activeDatePickerColumn = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val pickedDate = Date(millis)
                        val formatted = if (targetCol == schema.lastCallCol) {
                            val timeNow = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                                .format(Date())
                                .uppercase(Locale.ENGLISH)
                            val datePart = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(pickedDate)
                            "$timeNow, $datePart"
                        } else {
                            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(pickedDate)
                        }
                        values[targetCol] = formatted
                    }
                    activeDatePickerColumn = null
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePickerColumn = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Edit Lead Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Name & Phone are locked. Tap Date/Last Call for Calendar, or Lead Type/Follow-Ups/Assigned for dropdown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            schema.allHeaders.forEach { header ->
                val currentVal = values[header].orEmpty()
                when {
                    schema.isLockedColumn(header) -> {
                        OutlinedTextField(
                            value = currentVal,
                            onValueChange = {},
                            enabled = false,
                            readOnly = true,
                            label = { Text(header) },
                            trailingIcon = { Icon(Icons.Default.Lock, "Locked", Modifier.size(16.dp)) },
                            supportingText = { Text("Locked — edit in Google Sheet only") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    schema.isDateColumn(header) -> {
                        // Non-focusable Calendar Picker field — NEVER opens keyboard!
                        NonKeyboardPickerField(
                            label = header,
                            value = currentVal.ifBlank { "Tap to pick date" },
                            isPlaceholder = currentVal.isBlank(),
                            icon = Icons.Default.CalendarToday,
                            onClick = { activeDatePickerColumn = header }
                        )
                    }

                    header == schema.statusCol -> {
                        NonKeyboardDropdownField(
                            label = header,
                            value = currentVal,
                            options = leadTypeOptions,
                            onSelect = { values[header] = it }
                        )
                    }

                    header == schema.followUpCol -> {
                        NonKeyboardDropdownField(
                            label = header,
                            value = currentVal,
                            options = followUpOptions,
                            onSelect = { values[header] = it }
                        )
                    }

                    header == schema.assignedCol -> {
                        NonKeyboardDropdownField(
                            label = header,
                            value = currentVal,
                            options = assigneeOptions,
                            onSelect = { values[header] = it }
                        )
                    }

                    else -> {
                        // Notes & any future custom columns (preserves exact uppercase/lowercase!)
                        OutlinedTextField(
                            value = currentVal,
                            onValueChange = { values[header] = it },
                            label = { Text(header) },
                            minLines = 2,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(values.filterKeys { !schema.isLockedColumn(it) }) },
                    modifier = Modifier.weight(1.4f),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text(if (isSaving) "Saving..." else "Save to Sheet")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NonKeyboardPickerField(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Medium
                )
            }
            Icon(icon, contentDescription = label, tint = BrandBlue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun NonKeyboardDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        NonKeyboardPickerField(
            label = label,
            value = value.ifBlank { "Select $label" },
            isPlaceholder = value.isBlank(),
            icon = Icons.Default.ArrowDropDown,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt,
                            fontWeight = if (opt.equals(value.trim(), ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (opt.equals(value.trim(), ignoreCase = true)) {
                        { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                    } else null,
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ============================================================================
// SETTINGS SCREEN (with Caller Profile Lock!)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    state: LeadsUiState,
    onSelectTheme: (Boolean) -> Unit,
    onSelectCallerProfile: (String) -> Unit,
    onUpdateFollowUpMsg: (String) -> Unit,
    onUpdatePortfolioMsg: (String) -> Unit,
    onToggleAutoOpenNotes: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLogs: () -> Unit
) {
    var editingTemplateType by remember { mutableStateOf<String?>(null) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    if (editingTemplateType != null) {
        val isFollowUp = editingTemplateType == "followup"
        var draft by remember(editingTemplateType) {
            mutableStateOf(
                if (isFollowUp) state.config.followUpWhatsAppMessage else state.config.portfolioWhatsAppMessage
            )
        }
        AlertDialog(
            onDismissRequest = { editingTemplateType = null },
            title = { Text(if (isFollowUp) "WhatsApp Follow-up Message" else "WhatsApp Portfolio Message") },
            text = {
                Column {
                    Text(
                        "Use {{name}} to automatically insert the lead's name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isFollowUp) onUpdateFollowUpMsg(draft) else onUpdatePortfolioMsg(draft)
                        editingTemplateType = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplateType = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Caller Profile Lock Section
            SettingsSectionHeader(Icons.Default.Badge, "Caller Profile Lock")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Lock this phone to a caller so notes automatically save to their column and 'My Leads' filter activates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentProfile = state.config.callerProfile
                        NonKeyboardPickerField(
                            label = "Active Caller Profile",
                            value = if (currentProfile.isBlank()) "Unlocked (Choose column manually)" else "Locked to: $currentProfile",
                            isPlaceholder = currentProfile.isBlank(),
                            icon = Icons.Default.ArrowDropDown,
                            onClick = { profileMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = profileMenuExpanded,
                            onDismissRequest = { profileMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unlocked (Choose manually)") },
                                trailingIcon = if (currentProfile.isBlank()) {
                                    { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                } else null,
                                onClick = {
                                    onSelectCallerProfile("")
                                    profileMenuExpanded = false
                                }
                            )
                            state.callerProfileOptions.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile) },
                                    trailingIcon = if (profile.equals(currentProfile, ignoreCase = true)) {
                                        { Icon(Icons.Default.Check, null, tint = BrandBlue) }
                                    } else null,
                                    onClick = {
                                        onSelectCallerProfile(profile)
                                        profileMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Appearance
            SettingsSectionHeader(Icons.Default.Palette, "Appearance")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeOptionCard(
                            label = "Light",
                            icon = Icons.Default.LightMode,
                            selected = !state.config.darkTheme,
                            onClick = { onSelectTheme(false) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            label = "Dark",
                            icon = Icons.Default.DarkMode,
                            selected = state.config.darkTheme,
                            onClick = { onSelectTheme(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Calling & WhatsApp
            SettingsSectionHeader(Icons.Default.PhoneInTalk, "Calling & WhatsApp")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingTemplateType = "followup" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(20.dp), tint = CallGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WhatsApp Follow-up Message", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = state.config.followUpWhatsAppMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingTemplateType = "portfolio" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WorkOutline, null, Modifier.size(20.dp), tint = PortfolioTeal)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WhatsApp Portfolio / Work Message", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = state.config.portfolioWhatsAppMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(20.dp), tint = BrandBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-open notes after call", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Open Caller Notes automatically after a call ends",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.config.autoOpenNotesAfterCall,
                            onCheckedChange = onToggleAutoOpenNotes,
                            colors = SwitchDefaults.colors(checkedTrackColor = BrandBlue)
                        )
                    }
                }
            }

            // About & Diagnostics
            SettingsSectionHeader(Icons.Default.Info, "About & Diagnostics")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCheckUpdate() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = BrandBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Info & GitHub Updates", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (state.isCheckingUpdate) "Checking GitHub Releases..."
                                else "Scale Flow Leads v${state.currentVersion} · Tap to check update",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLogs() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, null, Modifier.size(20.dp), tint = BrandBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync & Activity Logs", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.logs.size} recent events · Pending offline: ${state.pendingSyncCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = BrandBlue
        )
    }
}

@Composable
private fun ThemeOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) BrandBlueContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, BrandBlue) else null
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncLogsDialog(
    logs: List<SyncLogEntry>,
    onCopyAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync & Activity Logs") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { entry ->
                    val color = when (entry.level) {
                        "OK" -> CallGreen
                        "ERROR" -> StatusRed
                        else -> BrandBlue
                    }
                    Column {
                        Text(
                            text = "${entry.timestamp} · ${entry.level}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Text(text = entry.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCopyAll, colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)) {
                Text("Copy Logs")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun statusPalette(raw: String): Triple<Color, Color, Color> {
    val lower = raw.lowercase()
    return when {
        lower.contains("lost") || lower.contains("overdue") || lower.contains("not") || lower.contains("missed") ->
            Triple(StatusRed, StatusRedContainer, StatusRed)
        lower.contains("today") || lower.contains("done") || lower.contains("qualified") || lower.contains("won") || lower.contains("interest") ->
            Triple(CallGreen, CallGreenContainer, CallGreenDarkText)
        lower.contains("follow") || lower.contains("proposal") || lower.contains("contact") ->
            Triple(StatusAmber, StatusAmberContainer, StatusAmber)
        else ->
            Triple(StatusBlue, StatusBlueContainer, BrandBlue)
    }
}

private fun launchDialer(context: Context, rawPhone: String) {
    val clean = rawPhone.trim()
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}"))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Could not open phone dialer", Toast.LENGTH_SHORT).show()
    }
}

private fun launchWhatsApp(context: Context, rawPhone: String, template: String, leadName: String) {
    val digits = rawPhone.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
        return
    }
    val msg = template.replace("{{name}}", leadName.ifBlank { "there" })
    val url = "https://wa.me/$digits?text=${Uri.encode(msg)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Scale Flow Leads", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
