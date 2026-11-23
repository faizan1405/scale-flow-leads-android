package com.scaleflow.leads.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaleflow.leads.data.AppConfig
import com.scaleflow.leads.data.LeadRow
import com.scaleflow.leads.data.SchemaMapping
import com.scaleflow.leads.data.displayValue
import com.scaleflow.leads.ui.theme.AmberContainer
import com.scaleflow.leads.ui.theme.AmberStatus
import com.scaleflow.leads.ui.theme.CallGreen
import com.scaleflow.leads.ui.theme.CallGreenContainer
import com.scaleflow.leads.ui.theme.IndigoContainer
import com.scaleflow.leads.ui.theme.IndigoPrimary
import com.scaleflow.leads.ui.theme.RoseContainer
import com.scaleflow.leads.ui.theme.RoseStatus
import com.scaleflow.leads.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainScreenViewModel
) {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.payload.spreadsheetName.ifBlank { "Scale Flow Leads" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isLive = state.config.isConnected && !state.config.demoMode
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isLive) CallGreen else AmberStatus)
                            )
                            Text(
                                text = when {
                                    state.isSyncing -> "Syncing with Google Sheet..."
                                    isLive && state.lastSyncedTime.isNotBlank() ->
                                        "Live · Synced ${state.lastSyncedTime}"
                                    isLive -> "Live · Connected"
                                    else -> "Demo Mode · Tap gear to connect Sheet"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLeads(silent = false) }) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                        }
                    }
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Sheet Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddLead() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Lead") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Update Available banner (GitHub Releases auto-updater)
            val availableUpdate = state.availableUpdate
            if (availableUpdate != null) {
                Surface(
                    color = CallGreenContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update v${availableUpdate.version} Ready",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CallGreen
                            )
                            Text(
                                text = if (state.isDownloadingUpdate) {
                                    "Downloading ${(state.downloadProgress * 100).toInt()}%..."
                                } else {
                                    availableUpdate.notes.ifBlank { "Tap to download & install the latest version." }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF14532D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.downloadAndInstallUpdate() },
                            enabled = !state.isDownloadingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CallGreen,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (state.isDownloadingUpdate) "..." else "Update")
                        }
                    }
                }
            }

            // Demo mode banner
            if (state.config.demoMode || !state.config.isConnected) {
                Surface(
                    color = IndigoContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openSettings() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Previewing demo leads. Tap to connect your Google Sheet.",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                }
            }

            // Error banner
            if (state.errorMessage != null) {
                Surface(
                    color = RoseContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        color = RoseStatus,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Sheet tabs selector (if spreadsheet has multiple tabs)
            if (state.payload.sheetNames.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.payload.sheetNames) { tabName ->
                        FilterChip(
                            selected = tabName == state.payload.sheetName,
                            onClick = { viewModel.selectSheetTab(tabName) },
                            label = { Text(tabName) }
                        )
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search business, phone, notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Status filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.availableStatuses) { status ->
                    val count = if (status == "All") {
                        state.payload.rows.size
                    } else {
                        state.payload.rows.count {
                            it.displayValue(state.schema.statusCol).equals(status, ignoreCase = true)
                        }
                    }
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { viewModel.onStatusSelected(status) },
                        label = {
                            Text(if (count > 0 || status == "All") "$status ($count)" else status)
                        }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Lead list with Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = state.isSyncing,
                onRefresh = { viewModel.refreshLeads(silent = false) },
                modifier = Modifier.fillMaxSize()
            ) {
                val leads = state.filteredLeads
                if (leads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No leads found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Pull down to sync or tap + New Lead to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(leads, key = { it.rowIndex }) { lead ->
                            LeadCard(
                                lead = lead,
                                schema = state.schema,
                                onCall = { phone ->
                                    viewModel.markLeadCalled(lead.rowIndex)
                                    launchDialer(context, phone)
                                },
                                onWhatsApp = { phone ->
                                    launchWhatsApp(context, phone)
                                },
                                onOpenWebsite = { url ->
                                    launchWebsite(context, url)
                                },
                                onQuickNote = { viewModel.openPostCallLog(lead) },
                                onEdit = { viewModel.openEditLead(lead) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Post-Call Quick Note Bottom Sheet
    val postCallLead = state.postCallLead
    if (postCallLead != null) {
        PostCallSheet(
            lead = postCallLead,
            schema = state.schema,
            statuses = state.quickStatuses,
            isSaving = state.isSaving,
            onDismiss = { viewModel.dismissPostCallLog() },
            onSave = { noteText, newStatus ->
                viewModel.savePostCallLog(postCallLead.rowIndex, noteText, newStatus)
            }
        )
    }

    // Full Lead Edit Bottom Sheet
    val editingLead = state.editingLead
    if (editingLead != null) {
        EditLeadSheet(
            title = "Edit Lead",
            headers = state.schema.allHeaders,
            initialFields = editingLead.fields,
            schema = state.schema,
            statuses = state.quickStatuses,
            isSaving = state.isSaving,
            onDismiss = { viewModel.dismissEditLead() },
            onSave = { fields -> viewModel.saveLeadEdits(editingLead.rowIndex, fields) },
            onCall = { phone ->
                viewModel.markLeadCalled(editingLead.rowIndex)
                launchDialer(context, phone)
            }
        )
    }

    // Add New Lead Bottom Sheet
    if (state.showAddLeadSheet) {
        val defaultHeaders = state.schema.allHeaders.ifEmpty {
            listOf("Business Name", "Phone", "Website", "Status", "Call Notes", "Follow-Up")
        }
        val initialMap = buildMap {
            defaultHeaders.forEach { put(it, "") }
            state.schema.statusCol?.let { put(it, "New") }
        }
        EditLeadSheet(
            title = "Add New Lead",
            headers = defaultHeaders,
            initialFields = initialMap,
            schema = SchemaMapping.detect(defaultHeaders),
            statuses = state.quickStatuses,
            isSaving = state.isSaving,
            onDismiss = { viewModel.dismissAddLead() },
            onSave = { fields -> viewModel.addNewLead(fields) },
            onCall = null
        )
    }

    // Connect Google Sheet Settings Bottom Sheet
    if (state.showSettingsSheet) {
        SettingsSheet(
            config = state.config,
            currentVersion = state.currentVersion,
            isCheckingUpdate = state.isCheckingUpdate,
            onDismiss = { viewModel.dismissSettings() },
            onCheckUpdate = { viewModel.checkForAppUpdate(silent = false) },
            onSave = { url, key, interval, demo, repo ->
                viewModel.saveSettings(url, key, interval, demo, repo)
            }
        )
    }
}

@Composable
private fun LeadCard(
    lead: LeadRow,
    schema: SchemaMapping,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onQuickNote: () -> Unit,
    onEdit: () -> Unit
) {
    val name = lead.displayValue(schema.nameCol).ifBlank { "Row #${lead.rowIndex}" }
    val phone = lead.displayValue(schema.phoneCol)
    val website = lead.displayValue(schema.websiteCol)
    val status = lead.displayValue(schema.statusCol)
    val notes = lead.displayValue(schema.notesCol)
    val followUp = lead.displayValue(schema.followUpCol)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (phone.isNotBlank()) {
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (status.isNotBlank()) {
                    StatusBadge(status)
                }
            }

            if (followUp.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AmberStatus
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Follow-up: $followUp",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberStatus
                    )
                }
            }

            if (notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQuickNote() }
                ) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (phone.isNotBlank()) onCall(phone) },
                    enabled = phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CallGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call", fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = { if (phone.isNotBlank()) onWhatsApp(phone) },
                    enabled = phone.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "WhatsApp",
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (website.isNotBlank() && !website.equals("no website", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = { onOpenWebsite(website) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Website",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onQuickNote,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = "Add Note",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Note")
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Lead",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val lower = status.lowercase()
    val (bg, fg) = when {
        lower.contains("interest") || lower.contains("closed") || lower.contains("won") ->
            CallGreenContainer to CallGreen
        lower.contains("callback") || lower.contains("follow") ->
            AmberContainer to AmberStatus
        lower.contains("not") || lower.contains("lost") || lower.contains("dead") || lower.contains("no answer") ->
            RoseContainer to RoseStatus
        else ->
            IndigoContainer to IndigoPrimary
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PostCallSheet(
    lead: LeadRow,
    schema: SchemaMapping,
    statuses: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (noteText: String, newStatus: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val leadName = lead.displayValue(schema.nameCol).ifBlank { "Lead #${lead.rowIndex}" }
    var selectedStatus by remember(lead.rowIndex) {
        mutableStateOf(lead.displayValue(schema.statusCol))
    }
    var noteText by remember(lead.rowIndex) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Log Call Note",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = leadName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            if (schema.statusCol != null) {
                Text(
                    text = "Update Status",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statuses.forEach { st ->
                        FilterChip(
                            selected = selectedStatus.equals(st, ignoreCase = true),
                            onClick = { selectedStatus = st },
                            label = { Text(st) },
                            leadingIcon = if (selectedStatus.equals(st, ignoreCase = true)) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("What happened on the call?") },
                placeholder = { Text("e.g. Owner interested, asked to send website samples on WhatsApp") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = { onSave(noteText, selectedStatus.ifBlank { null }) },
                    modifier = Modifier.weight(1.5f),
                    enabled = !isSaving && (noteText.isNotBlank() || selectedStatus.isNotBlank())
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudDone, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save & Sync")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditLeadSheet(
    title: String,
    headers: List<String>,
    initialFields: Map<String, String>,
    schema: SchemaMapping,
    statuses: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onCall: ((String) -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember(initialFields) {
        mutableStateMapOf<String, String>().apply {
            headers.forEach { h -> put(h, initialFields[h].orEmpty()) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val phone = schema.phoneCol?.let { values[it] }.orEmpty()
                if (onCall != null && phone.isNotBlank()) {
                    Button(
                        onClick = { onCall(phone) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CallGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Call, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Call")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            headers.forEach { header ->
                val isNotes = header == schema.notesCol
                val isStatus = header == schema.statusCol

                if (isStatus) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        statuses.forEach { st ->
                            FilterChip(
                                selected = values[header].equals(st, ignoreCase = true),
                                onClick = { values[header] = st },
                                label = { Text(st) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = values[header].orEmpty(),
                    onValueChange = { values[header] = it },
                    label = { Text(header) },
                    minLines = if (isNotes) 3 else 1,
                    maxLines = if (isNotes) 8 else 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(values.toMap()) },
                    modifier = Modifier.weight(1.5f),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save to Sheet")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    config: AppConfig,
    currentVersion: String,
    isCheckingUpdate: Boolean,
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
    onSave: (scriptUrl: String, apiKey: String, autoSyncSeconds: Int, demoMode: Boolean, githubRepo: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scriptUrl by remember(config) { mutableStateOf(config.scriptUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var syncSeconds by remember(config) { mutableStateOf(config.autoSyncSeconds.toString()) }
    var demoMode by remember(config) { mutableStateOf(config.demoMode) }
    var githubRepo by remember(config) { mutableStateOf(config.githubRepo) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "App & Sheet Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version $currentVersion · Syncs with Google Sheets & auto-updates via GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = scriptUrl,
                onValueChange = {
                    scriptUrl = it
                    if (it.trim().startsWith("https://")) {
                        demoMode = false
                    }
                },
                label = { Text("Apps Script Web App URL") },
                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Secret API Key (optional)") },
                placeholder = { Text("Leave blank if not set in AppSync.gs") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = syncSeconds,
                onValueChange = { input -> syncSeconds = input.filter { it.isDigit() } },
                label = { Text("Auto-sync interval (seconds)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = githubRepo,
                onValueChange = { githubRepo = it },
                label = { Text("GitHub Auto-Update Repo (owner/repo)") },
                placeholder = { Text("faizan1405/scale-flow-leads-android") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Demo Mode", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use sample leads without connecting a Sheet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = demoMode,
                    onCheckedChange = { demoMode = it }
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !isCheckingUpdate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isCheckingUpdate) "Checking..." else "Check Update")
                }
                Button(
                    onClick = {
                        val interval = syncSeconds.toIntOrNull() ?: 15
                        onSave(scriptUrl, apiKey, interval, demoMode, githubRepo)
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Sync")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun launchDialer(context: Context, rawPhone: String) {
    val clean = rawPhone.trim()
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}"))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Could not open phone dialer", Toast.LENGTH_SHORT).show()
    }
}

private fun launchWhatsApp(context: Context, rawPhone: String) {
    val digits = rawPhone.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
    }
}

private fun launchWebsite(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Could not open website", Toast.LENGTH_SHORT).show()
    }
}
