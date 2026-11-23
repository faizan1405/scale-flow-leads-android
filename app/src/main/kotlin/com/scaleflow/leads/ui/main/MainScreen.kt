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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
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
            isSaving = state.isSaving,
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
            onScheduleFollowUpDate = { formattedDate ->
                viewModel.saveInstantFollowUpDate(detailLead.rowIndex, formattedDate)
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

    // Post-Call Quick Note Bottom Sheet (auto-opens after a call ends)
    val postCallLead = state.postCallLead
    if (postCallLead != null) {
        PostCallQuickNoteSheet(
            lead = postCallLead,
            schema = state.schema,
            activeNoteColumn = state.activeNoteColumn,
            statusOptions = state.statusFilterOptions.filter { it != "All" && it != "Today" },
            isSaving = state.isSaving,
            onSelectNoteColumn = { viewModel.updatePreferredNoteColumn(it) },
            onDismiss = { viewModel.closePostCallNote() },
            onSave = { col, note, status ->
                viewModel.saveCallerNote(postCallLead.rowIndex, col, note, status)
            }
        )
    }

    // Edit All Editable Sheet Fields (Name & Number strictly locked!)
    if (state.showEditAllSheet && detailLead != null) {
        EditEditableFieldsSheet(
            schema = state.schema,
            initialFields = detailLead.fields,
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
    var sortMenuOpen by remember { mutableStateOf(false) }
    val sortOptions = listOf("Sheet order", "Follow-up date", "Name (A-Z)", "Last Call")
    val hasActiveFilters = state.searchQuery.isNotBlank() ||
        state.selectedStatusFilter != "All" ||
        state.selectedAssigneeFilter != "All"

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
                                        .background(if (state.errorMessage == null) CallGreen else StatusRed)
                                )
                                Text(
                                    text = if (state.isSyncing) {
                                        "Syncing Leads tab..."
                                    } else {
                                        "Leads tab only · ${leads.size} shown"
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

                if (state.errorMessage != null) {
                    Surface(color = StatusRedContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.errorMessage,
                            color = StatusRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // Search bar (present on EVERY list tab!)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    placeholder = { Text("Search leads by name, number, notes...") },
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

                // Instant Assignee Filter + Sort Bar (directly on home / every list!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Assigned-to dropdown ("Show me only Saurabh / Faizan / Unassigned")
                    Box {
                        val isAssignedFiltered = state.selectedAssigneeFilter != "All"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAssignedFiltered) BrandBlueContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isAssignedFiltered) BorderStroke(1.dp, BrandBlue) else null,
                            modifier = Modifier.clickable { assigneeMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isAssignedFiltered) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAssignedFiltered) {
                                        "Assigned: ${state.selectedAssigneeFilter}"
                                    } else {
                                        "Assigned: All"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAssignedFiltered) BrandBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = assigneeMenuOpen,
                            onDismissRequest = { assigneeMenuOpen = false }
                        ) {
                            state.assigneeFilterOptions.forEach { person ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (person == "All") "All Assignees" else "Only $person",
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

                    // Sort dropdown (moved from Settings directly onto the list!)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { sortMenuOpen = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = state.config.sortBy,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                            }
                        }
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

                    if (hasActiveFilters) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Clear",
                            color = BrandBlue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.clearAllFilters() }
                        )
                    }
                }

                // Status / Lead Type filter chips (instant on EVERY list!)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.statusFilterOptions) { chip ->
                        val selected = state.selectedStatusFilter == chip
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
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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

                Spacer(Modifier.height(3.dp))

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

                    // Quick Action Buttons: Call, WA Follow-up, WA Portfolio, Note
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
// LEAD DETAILS SCREEN (Instant Calendar + Dual WhatsApp + Future-Proof Fields)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LeadDetailsScreen(
    lead: LeadRow,
    schema: SchemaMapping,
    activeNoteColumn: String?,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onWhatsAppFollowUp: () -> Unit,
    onWhatsAppPortfolio: () -> Unit,
    onScheduleFollowUpDate: (String) -> Unit,
    onCopyPhone: (String) -> Unit,
    onSelectNoteColumn: (String) -> Unit,
    onSaveCallerNote: (targetColumn: String?, note: String) -> Unit,
    onOpenEditAll: () -> Unit
) {
    val name = lead.displayValue(schema.nameCol).ifBlank { "Row #${lead.rowIndex}" }
    val phone = lead.displayValue(schema.phoneCol)
    val leadType = lead.displayValue(schema.statusCol)

    var callerNoteInput by remember(lead.rowIndex) { mutableStateOf("") }
    var showInstantCalendar by remember { mutableStateOf(false) }
    val (_, badgeBg, badgeFg) = statusPalette(leadType)

    // Instant Calendar DatePickerDialog when tapping "Schedule Follow-up"!
    if (showInstantCalendar) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showInstantCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
                        onScheduleFollowUpDate(formatted)
                    }
                    showInstantCalendar = false
                }) { Text("Save Date", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showInstantCalendar = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Lead Details", fontWeight = FontWeight.Bold) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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

            // 2x2 Action Buttons: Call Now | Schedule Follow-up (Instant Calendar)
            //                     WA Follow-up | WA Portfolio
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
                Button(
                    onClick = { showInstantCalendar = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = BrandBlue)
                        Text("Schedule Follow-up", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

            // Dynamic Future-Proof Info Card: Renders EVERY column from the Google Sheet!
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
                            text = "Lead Information",
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

            // Caller Notes Card with Explicit Target Column Selector
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CallGreen.copy(alpha = 0.35f))
            ) {
                Column {
                    Surface(color = CallGreenContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "Add Call Note",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CallGreenDarkText
                            )
                            Text(
                                text = "Choose which column to save your note into:",
                                style = MaterialTheme.typography.labelSmall,
                                color = CallGreenDarkText.copy(alpha = 0.85f)
                            )
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

                    OutlinedTextField(
                        value = callerNoteInput,
                        onValueChange = { callerNoteInput = it },
                        placeholder = {
                            Text("Writing to '${activeNoteColumn ?: "Notes"}' column...")
                        },
                        minLines = 3,
                        maxLines = 6,
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
    statusOptions: List<String>,
    isSaving: Boolean,
    onSelectNoteColumn: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (targetCol: String?, noteText: String, newStatus: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val leadName = lead.displayValue(schema.nameCol).ifBlank { "Lead #${lead.rowIndex}" }
    var selectedStatus by remember(lead.rowIndex) { mutableStateOf(lead.displayValue(schema.statusCol)) }
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
            Text(leadName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))

            if (schema.noteColumnOptions.isNotEmpty()) {
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
                Spacer(Modifier.height(10.dp))
            }

            if (schema.statusCol != null && statusOptions.isNotEmpty()) {
                Text("Update ${schema.statusCol}:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusOptions.forEach { st ->
                        FilterChip(
                            selected = selectedStatus.equals(st, ignoreCase = true),
                            onClick = { selectedStatus = st },
                            label = { Text(st) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("What happened on the call?") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Skip")
                }
                Button(
                    onClick = { onSave(activeNoteColumn, noteText, selectedStatus.ifBlank { null }) },
                    modifier = Modifier.weight(1.5f),
                    enabled = !isSaving && (noteText.isNotBlank() || selectedStatus.isNotBlank()),
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
// EDIT SHEET FIELDS (Name & Phone Number strictly locked!)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEditableFieldsSheet(
    schema: SchemaMapping,
    initialFields: Map<String, String>,
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Edit Lead Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Name and Phone Number are locked (Google Sheet only). All other fields can be edited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            schema.allHeaders.forEach { header ->
                val isLocked = schema.isLockedColumn(header)
                OutlinedTextField(
                    value = values[header].orEmpty(),
                    onValueChange = { if (!isLocked) values[header] = it },
                    enabled = !isLocked,
                    readOnly = isLocked,
                    label = { Text(header) },
                    trailingIcon = if (isLocked) {
                        { Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp)) }
                    } else null,
                    supportingText = if (isLocked) {
                        { Text("Locked — edit in Google Sheet only") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
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

// ============================================================================
// SETTINGS SCREEN (Cleaned Up: Dual WhatsApp Templates, No Confirm Call, No Sort)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    state: LeadsUiState,
    onSelectTheme: (Boolean) -> Unit,
    onUpdateFollowUpMsg: (String) -> Unit,
    onUpdatePortfolioMsg: (String) -> Unit,
    onToggleAutoOpenNotes: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLogs: () -> Unit
) {
    var editingTemplateType by remember { mutableStateOf<String?>(null) }

    if (editingTemplateType != null) {
        val isFollowUp = editingTemplateType == "followup"
        var draft by remember(editingTemplateType) {
            mutableStateOf(
                if (isFollowUp) state.config.followUpWhatsAppMessage else state.config.portfolioWhatsAppMessage
            )
        }
        AlertDialog(
            onDismissRequest = { editingTemplateType = null },
            title = {
                Text(if (isFollowUp) "WhatsApp Follow-up Message" else "WhatsApp Portfolio Message")
            },
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

            // Calling & Communication (Dual WhatsApp + Auto-open notes after call)
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
                                "${state.logs.size} recent events · Tap to inspect or copy",
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
        lower.contains("follow") || lower.contains("callback") || lower.contains("hot") ->
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
