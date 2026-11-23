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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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

    // Back navigation for sub-screens
    BackHandler(enabled = state.followUpLead != null || state.detailLead != null) {
        when {
            state.followUpLead != null -> viewModel.closeUpdateFollowUp()
            state.detailLead != null -> viewModel.closeLeadDetails()
        }
    }

    val followUpLead = state.followUpLead
    val detailLead = state.detailLead

    when {
        followUpLead != null -> {
            UpdateFollowUpScreen(
                lead = followUpLead,
                schema = state.schema,
                statusOptions = state.statusOptions,
                isSaving = state.isSaving,
                onBack = { viewModel.closeUpdateFollowUp() },
                onSave = { date, time, status, note ->
                    viewModel.saveFollowUpUpdate(followUpLead.rowIndex, date, time, status, note)
                }
            )
        }

        detailLead != null -> {
            LeadDetailsScreen(
                lead = detailLead,
                schema = state.schema,
                isSaving = state.isSaving,
                snackbarHostState = snackbarHostState,
                onBack = { viewModel.closeLeadDetails() },
                onCall = {
                    viewModel.requestCall(detailLead) { phone -> launchDialer(context, phone) }
                },
                onWhatsApp = {
                    val phone = detailLead.displayValue(state.schema.phoneCol)
                    val name = detailLead.displayValue(state.schema.nameCol)
                    launchWhatsApp(context, phone, state.config.defaultWhatsAppMessage, name)
                },
                onMarkCalled = { viewModel.markCalledNow(detailLead.rowIndex) },
                onScheduleFollowUp = { viewModel.openUpdateFollowUp(detailLead.rowIndex) },
                onCopyPhone = { phone -> copyToClipboard(context, phone) },
                onSaveCallerNote = { note -> viewModel.saveCallerNoteOnly(detailLead.rowIndex, note) },
                onOpenEditAll = { viewModel.openEditAllSheet() }
            )
        }

        else -> {
            HomeScaffold(
                state = state,
                snackbarHostState = snackbarHostState,
                viewModel = viewModel,
                onCallLead = { lead ->
                    viewModel.requestCall(lead) { phone -> launchDialer(context, phone) }
                },
                onWhatsAppLead = { lead ->
                    val phone = lead.displayValue(state.schema.phoneCol)
                    val name = lead.displayValue(state.schema.nameCol)
                    launchWhatsApp(context, phone, state.config.defaultWhatsAppMessage, name)
                }
            )
        }
    }

    // Confirm before call dialog
    val confirmLead = state.confirmCallLead
    if (confirmLead != null) {
        val name = confirmLead.displayValue(state.schema.nameCol).ifBlank { "Lead" }
        val phone = confirmLead.displayValue(state.schema.phoneCol)
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmCall() },
            title = { Text("Call $name?") },
            text = { Text("Dial $phone now?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissConfirmCall()
                        viewModel.markLeadCalled(confirmLead.rowIndex)
                        launchDialer(context, phone)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Call Now") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmCall() }) { Text("Cancel") }
            }
        )
    }

    // Add New Lead Sheet
    if (state.showAddLeadSheet) {
        val headers = state.schema.allHeaders.ifEmpty {
            listOf("Date", "Last Call", "Name", "Numbers", "Lead Type", "Follow-Ups", "Assined to")
        }
        val initial = buildMap {
            headers.forEach { put(it, "") }
            state.schema.statusCol?.let { put(it, "New") }
            state.schema.dateCol?.let {
                put(it, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))
            }
        }
        EditAllFieldsSheet(
            title = "Add New Lead",
            headers = headers,
            initialFields = initial,
            isSaving = state.isSaving,
            onDismiss = { viewModel.dismissAddLead() },
            onSave = { viewModel.addNewLead(it) }
        )
    }

    // Edit All Columns Sheet (from Lead Details ⋮ menu)
    if (state.showEditAllSheet && detailLead != null) {
        EditAllFieldsSheet(
            title = "Edit All Sheet Fields",
            headers = state.schema.allHeaders,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    state: LeadsUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: MainScreenViewModel,
    onCallLead: (LeadRow) -> Unit,
    onWhatsAppLead: (LeadRow) -> Unit
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
        },
        floatingActionButton = {
            if (state.activeTab == BottomTab.LEADS || state.activeTab == BottomTab.FOLLOW_UPS) {
                FloatingActionButton(
                    onClick = { viewModel.openAddLead() },
                    containerColor = BrandBlue,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Lead")
                }
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
                    state = state,
                    leads = state.filteredLeads,
                    showSearchAndFilters = true,
                    emptyTitle = "No leads found",
                    onRefresh = { viewModel.refreshLeads(silent = false) },
                    onSearchChange = { viewModel.onSearchChanged(it) },
                    onFilterSelect = { viewModel.onFilterSelected(it) },
                    onOpenLead = { viewModel.openLeadDetails(it.rowIndex) },
                    onCall = onCallLead,
                    onWhatsApp = onWhatsAppLead,
                    onQuickNote = { viewModel.openUpdateFollowUp(it.rowIndex) },
                    onDownloadUpdate = { viewModel.downloadAndInstallUpdate() }
                )

                BottomTab.FOLLOW_UPS -> LeadsListTab(
                    state = state,
                    leads = state.followUpLeads,
                    showSearchAndFilters = false,
                    emptyTitle = "No follow-ups scheduled",
                    onRefresh = { viewModel.refreshLeads(silent = false) },
                    onSearchChange = {},
                    onFilterSelect = {},
                    onOpenLead = { viewModel.openLeadDetails(it.rowIndex) },
                    onCall = onCallLead,
                    onWhatsApp = onWhatsAppLead,
                    onQuickNote = { viewModel.openUpdateFollowUp(it.rowIndex) },
                    onDownloadUpdate = { viewModel.downloadAndInstallUpdate() }
                )

                BottomTab.CALLS -> LeadsListTab(
                    state = state,
                    leads = state.calledLeads,
                    showSearchAndFilters = false,
                    emptyTitle = "No calls logged yet",
                    onRefresh = { viewModel.refreshLeads(silent = false) },
                    onSearchChange = {},
                    onFilterSelect = {},
                    onOpenLead = { viewModel.openLeadDetails(it.rowIndex) },
                    onCall = onCallLead,
                    onWhatsApp = onWhatsAppLead,
                    onQuickNote = { viewModel.openUpdateFollowUp(it.rowIndex) },
                    onDownloadUpdate = { viewModel.downloadAndInstallUpdate() }
                )

                BottomTab.SETTINGS -> SettingsTabScreen(
                    state = state,
                    onSelectTheme = { viewModel.updateTheme(it) },
                    onUpdateWhatsAppMsg = { viewModel.updateWhatsAppTemplate(it) },
                    onToggleConfirmCall = { viewModel.updateConfirmBeforeCall(it) },
                    onToggleAutoOpenNotes = { viewModel.updateAutoOpenNotes(it) },
                    onSelectSortBy = { viewModel.updateSortBy(it) },
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
    state: LeadsUiState,
    leads: List<LeadRow>,
    showSearchAndFilters: Boolean,
    emptyTitle: String,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterSelect: (String) -> Unit,
    onOpenLead: (LeadRow) -> Unit,
    onCall: (LeadRow) -> Unit,
    onWhatsApp: (LeadRow) -> Unit,
    onQuickNote: (LeadRow) -> Unit,
    onDownloadUpdate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Branded Header matching Mockup Screen 1
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.5.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Scale Flow Leads",
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
                                        "Leads tab only · ${state.payload.rows.size} leads"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    IconButton(onClick = onRefresh) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync now",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Auto-Update Banner (GitHub Releases)
                val update = state.availableUpdate
                if (update != null) {
                    Surface(
                        color = CallGreenContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    "Update v${update.version} available"
                                },
                                color = CallGreenDarkText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onDownloadUpdate,
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

                if (showSearchAndFilters) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = {
                            Text(
                                "Search leads by name or number",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filterChips) { chip ->
                            val selected = state.selectedFilter == chip
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onFilterSelect(chip) }
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
        }

        // Pull-to-Refresh Lead Cards
        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (leads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(leads, key = { it.rowIndex }) { lead ->
                        LeadListCard(
                            lead = lead,
                            schema = state.schema,
                            onClick = { onOpenLead(lead) },
                            onCall = { onCall(lead) },
                            onWhatsApp = { onWhatsApp(lead) },
                            onQuickNote = { onQuickNote(lead) }
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
    onWhatsApp: () -> Unit,
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
            // Left colored accent stripe (exact mockup style)
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
                        Surface(
                            color = badgeBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
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
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
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

                    // Compact quick-action pill buttons (Blue Call, Green WhatsApp, Note)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (phone.isNotBlank()) BrandBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = phone.isNotBlank()) { onCall() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Call",
                                tint = if (phone.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (phone.isNotBlank()) CallGreen else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = phone.isNotBlank()) { onWhatsApp() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = "WhatsApp",
                                tint = if (phone.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onQuickNote() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Notes,
                                contentDescription = "Follow-up & Notes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
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
// SCREEN 2: LEAD DETAILS (Matching Mockup Screen 2)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadDetailsScreen(
    lead: LeadRow,
    schema: SchemaMapping,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onMarkCalled: () -> Unit,
    onScheduleFollowUp: () -> Unit,
    onCopyPhone: (String) -> Unit,
    onSaveCallerNote: (String) -> Unit,
    onOpenEditAll: () -> Unit
) {
    val name = lead.displayValue(schema.nameCol).ifBlank { "Row #${lead.rowIndex}" }
    val phone = lead.displayValue(schema.phoneCol)
    val leadType = lead.displayValue(schema.statusCol)
    val assigned = lead.displayValue(schema.assignedCol)
    val lastCall = lead.displayValue(schema.lastCallCol)
    val followUp = lead.displayValue(schema.followUpCol)
    val existingNote = lead.displayValue(schema.notesCol)

    var callerNoteInput by remember(lead.rowIndex) { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val (_, badgeBg, badgeFg) = statusPalette(leadType)

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
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit all Sheet fields") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenEditAll()
                                }
                            )
                        }
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
                    if (leadType.isNotBlank()) {
                        Text(
                            text = leadType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // 2x2 Action Buttons Grid (exact mockup layout)
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
                    onClick = onWhatsApp,
                    enabled = phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CallGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onMarkCalled() }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("Mark Called", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onScheduleFollowUp() }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("Schedule Follow-up", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Key-Value Info Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow(Icons.Default.Person, "Name", name)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            InfoRow(Icons.Default.Call, "Number", phone.ifBlank { "—" })
                        }
                        if (phone.isNotBlank()) {
                            IconButton(
                                onClick = { onCopyPhone(phone) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy number",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    InfoRow(Icons.Default.LocalOffer, "Lead Type", leadType.ifBlank { "—" })
                    InfoRow(Icons.Default.Person, "Assigned To", assigned.ifBlank { "—" })
                    InfoRow(Icons.Default.Schedule, "Last Call", lastCall.ifBlank { "—" })
                    InfoRow(Icons.Default.CalendarToday, "Follow-up Date", followUp.ifBlank { "—" })
                }
            }

            // Existing Note (from Sheet) Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Existing Note (from Sheet)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = existingNote.ifBlank { "No existing note in sheet yet." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Separate Caller Notes Card (green header accent matching mockup)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CallGreen.copy(alpha = 0.35f))
            ) {
                Column {
                    Surface(
                        color = CallGreenContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = CallGreenDarkText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Caller Notes",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CallGreenDarkText
                                )
                                Text(
                                    text = "Separate caller notes (not mixed with above)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CallGreenDarkText.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = callerNoteInput,
                        onValueChange = { callerNoteInput = it },
                        placeholder = {
                            Text("Spoke today. Write your call note here...")
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

            // Full-width Save Note button
            Button(
                onClick = {
                    val text = callerNoteInput.trim()
                    if (text.isNotEmpty()) {
                        onSaveCallerNote(text)
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
                    Text("Save Note", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(105.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================================
// SCREEN 3: UPDATE FOLLOW-UP (Matching Mockup Screen 3)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateFollowUpScreen(
    lead: LeadRow,
    schema: SchemaMapping,
    statusOptions: List<String>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (date: String, time: String, status: String, callerNote: String) -> Unit
) {
    val defaultDate = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    }
    var followUpDate by remember(lead.rowIndex) { mutableStateOf(defaultDate) }
    var followUpTime by remember(lead.rowIndex) { mutableStateOf("11:00 AM") }
    var selectedStatus by remember(lead.rowIndex) {
        mutableStateOf(lead.displayValue(schema.statusCol).ifBlank { "Follow-up" })
    }
    var setReminder by remember(lead.rowIndex) { mutableStateOf(true) }
    var callerNotes by remember(lead.rowIndex) { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    val timeSlots = listOf("09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "02:00 PM", "04:00 PM", "06:00 PM")

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        followUpDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Update Follow-up", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Green info banner (matching mockup)
            Surface(
                color = CallGreenContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = CallGreenDarkText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Separate caller notes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = CallGreenDarkText
                        )
                        Text(
                            text = "Add your call notes and set the next follow-up quickly for ${lead.displayValue(schema.nameCol)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CallGreenDarkText.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Follow-up Date
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Follow-up Date *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = followUpDate,
                    onValueChange = { followUpDate = it },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Follow-up Time dropdown
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Follow-up Time", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = timeExpanded,
                    onExpandedChange = { timeExpanded = !timeExpanded }
                ) {
                    OutlinedTextField(
                        value = followUpTime,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = timeExpanded,
                        onDismissRequest = { timeExpanded = false }
                    ) {
                        timeSlots.forEach { slot ->
                            DropdownMenuItem(
                                text = { Text(slot) },
                                onClick = {
                                    followUpTime = slot
                                    timeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Status dropdown
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Status *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = !statusExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStatus,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statusOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    selectedStatus = opt
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Set Reminder toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Set Reminder", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Highlight on the follow-up date & time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = setReminder,
                    onCheckedChange = { setReminder = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = BrandBlue)
                )
            }

            // Caller Notes
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Caller Notes *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = callerNotes,
                    onValueChange = { callerNotes = it },
                    placeholder = { Text("Discussed website pricing. Will confirm after talking to their team...") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Save Update Button
            Button(
                onClick = { onSave(followUpDate, followUpTime, selectedStatus, callerNotes) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Update", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================================
// SCREEN 4: SETTINGS (Matching Mockup Screen 4)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    state: LeadsUiState,
    onSelectTheme: (Boolean) -> Unit,
    onUpdateWhatsAppMsg: (String) -> Unit,
    onToggleConfirmCall: (Boolean) -> Unit,
    onToggleAutoOpenNotes: (Boolean) -> Unit,
    onSelectSortBy: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLogs: () -> Unit
) {
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortOptions = listOf(
        "Sheet order",
        "Follow-up date (earliest first)",
        "Name (A-Z)",
        "Last Call (newest first)"
    )

    if (showWhatsAppDialog) {
        var draft by remember { mutableStateOf(state.config.defaultWhatsAppMessage) }
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = false },
            title = { Text("Default WhatsApp Message") },
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
                        onUpdateWhatsAppMsg(draft)
                        showWhatsAppDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = false }) { Text("Cancel") }
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
            // Appearance Section
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

            // Calling & Communication Section
            SettingsSectionHeader(Icons.Default.PhoneInTalk, "Calling & Communication")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWhatsAppDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(20.dp), tint = CallGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default WhatsApp Message", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = state.config.defaultWhatsAppMessage,
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
                        Icon(Icons.Default.Call, null, Modifier.size(20.dp), tint = BrandBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Confirm before call", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Show a confirmation dialog before making a call",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.config.confirmBeforeCall,
                            onCheckedChange = onToggleConfirmCall,
                            colors = SwitchDefaults.colors(checkedTrackColor = BrandBlue)
                        )
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
                                "Open Caller Notes screen automatically after a call ends",
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

            // Leads & List Section
            SettingsSectionHeader(Icons.Default.Sort, "Leads & List")
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = sortExpanded,
                        onExpandedChange = { sortExpanded = !sortExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.config.sortBy,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sort leads by") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            sortOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onSelectSortBy(opt)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // About & Diagnostics Section
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAllFieldsSheet(
    title: String,
    headers: List<String>,
    initialFields: Map<String, String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember(initialFields) {
        mutableStateMapOf<String, String>().apply {
            headers.forEach { h -> put(h, initialFields[h].orEmpty()) }
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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            headers.forEach { h ->
                OutlinedTextField(
                    value = values[h].orEmpty(),
                    onValueChange = { values[h] = it },
                    label = { Text(h) },
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
                    onClick = { onSave(values.toMap()) },
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
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCopyAll,
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) { Text("Copy Logs") }
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
