package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdminSettings
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.Report
import com.example.ui.components.AppIconAvatar
import com.example.ui.components.FormatUtils
import com.example.ui.components.StatusChip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DeveloperAdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    isAdmin: Boolean,
    categories: List<Category>,
    adminSettings: AdminSettings,
    viewModel: DeveloperAdminViewModel,
    onBack: () -> Unit
) {
    val allApps by viewModel.adminApplications.collectAsStateWithLifecycle()
    val allDevs by viewModel.adminDevelopers.collectAsStateWithLifecycle()
    val allUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
    val allReports by viewModel.adminReports.collectAsStateWithLifecycle()
    val flaggedReviews by viewModel.adminFlaggedReviews.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var rejectDialogApp by remember { mutableStateOf<Pair<ApplicationListing, String>?>(null) }
    var resolveReportDialog by remember { mutableStateOf<Report?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Administrator verification required. Access is enforced by backend Firestore security rules.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val tabs = listOf("Apps", "Developers", "Users & Reports", "Marketplace")
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overview Metrics Strip
                item {
                    val published = allApps.count { it.status == "published" }
                    val pending = allApps.count { it.status == "pending_review" }
                    val rejected = allApps.count { it.status == "rejected" }
                    val downloads = allApps.sumOf { it.downloadCount }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AdminMetricCard("Users", allUsers.size.toString(), Modifier.weight(1f))
                            AdminMetricCard("Developers", allDevs.size.toString(), Modifier.weight(1f))
                            AdminMetricCard("Total Apps", allApps.size.toString(), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            AdminMetricCard("Published", published.toString(), Modifier.weight(1f))
                            AdminMetricCard("Pending", pending.toString(), Modifier.weight(1f))
                            AdminMetricCard("Rejected", rejected.toString(), Modifier.weight(1f))
                            AdminMetricCard("Downloads", FormatUtils.formatDownloads(downloads), Modifier.weight(1f))
                        }
                    }
                }

                when (selectedTab) {
                    // Tab 0: Application Management & Approval Queue
                    0 -> {
                        if (allApps.isEmpty()) {
                            item {
                                Text(
                                    "No submitted applications in the database yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(allApps, key = { it.id }) { app ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AppIconAvatar(app.appName, app.iconUrl, size = 48.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    text = "${app.developerName} • ${app.packageName} v${app.latestVersionName}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            StatusChip(app.status)
                                        }

                                        Text(
                                            text = "SHA-256: ${app.apkSha256.take(20)}… • Cert: ${app.signingCertSha256.take(16)}…",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Curation toggles
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = app.featured,
                                                onClick = { viewModel.toggleAppFlags(app, !app.featured, app.editorsPick, app.recommended) },
                                                label = { Text("Featured") }
                                            )
                                            FilterChip(
                                                selected = app.editorsPick,
                                                onClick = { viewModel.toggleAppFlags(app, app.featured, !app.editorsPick, app.recommended) },
                                                label = { Text("Editor's Pick") }
                                            )
                                            FilterChip(
                                                selected = app.recommended,
                                                onClick = { viewModel.toggleAppFlags(app, app.featured, app.editorsPick, !app.recommended) },
                                                label = { Text("Recommended") }
                                            )
                                        }

                                        // Approval / Reject / Suspend / Delete Controls
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (app.status != "published") {
                                                Button(
                                                    onClick = { viewModel.approveAndPublishApp(app) },
                                                    modifier = Modifier.testTag("admin_approve_${app.id}")
                                                ) {
                                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Approve & Publish")
                                                }
                                            }
                                            OutlinedButton(onClick = { rejectDialogApp = app to "rejected" }) {
                                                Icon(Icons.Filled.Close, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Reject")
                                            }
                                            OutlinedButton(onClick = { rejectDialogApp = app to "suspended" }) {
                                                Icon(Icons.Filled.Block, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Suspend")
                                            }
                                            IconButton(onClick = { viewModel.removeApp(app.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete app", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 1: Developer Management
                    1 -> {
                        if (allDevs.isEmpty()) {
                            item { Text("No registered developers yet.") }
                        } else {
                            items(allDevs, key = { it.id }) { dev ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(dev.developerName, style = MaterialTheme.typography.titleSmall)
                                                Text(dev.contactEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            StatusChip(dev.status)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.setDeveloperStatus(dev.developerId, "approved") },
                                                enabled = dev.status != "approved"
                                            ) {
                                                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Approve")
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.setDeveloperStatus(dev.developerId, "suspended") },
                                                enabled = dev.status != "suspended"
                                            ) {
                                                Text("Suspend")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 2: Users, Reports & Flagged Reviews
                    2 -> {
                        item { Text("Reports (${allReports.size})", style = MaterialTheme.typography.titleMedium) }
                        items(allReports, key = { it.id }) { rep ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${rep.targetType.uppercase()}: ${rep.reason}", style = MaterialTheme.typography.titleSmall)
                                        StatusChip(rep.status)
                                    }
                                    Text(rep.details, style = MaterialTheme.typography.bodySmall)
                                    if (rep.status == "open") {
                                        Button(onClick = { resolveReportDialog = rep }) {
                                            Text("Review & Resolve")
                                        }
                                    }
                                }
                            }
                        }

                        if (flaggedReviews.isNotEmpty()) {
                            item { Text("Flagged Reviews (${flaggedReviews.size})", style = MaterialTheme.typography.titleMedium) }
                            items(flaggedReviews, key = { it.id }) { rev ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${rev.userName}: ${rev.comment}", style = MaterialTheme.typography.bodyMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { viewModel.moderateReview(rev.id, "approved") }) { Text("Restore") }
                                            OutlinedButton(onClick = { viewModel.moderateReview(rev.id, "removed") }) { Text("Remove") }
                                        }
                                    }
                                }
                            }
                        }

                        item { Text("Registered Users (${allUsers.size})", style = MaterialTheme.typography.titleMedium) }
                        items(allUsers, key = { it.id }) { usr ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(usr.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(usr.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        StatusChip(usr.status)
                                        OutlinedButton(
                                            onClick = {
                                                val next = if (usr.status == "active") "suspended" else "active"
                                                viewModel.setUserStatus(usr.userId, next)
                                            }
                                        ) {
                                            Text(if (usr.status == "active") "Suspend" else "Restore")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tab 3: Marketplace Categories & Announcements
                    3 -> {
                        item {
                            var newCatName by remember { mutableStateOf("") }
                            var newCatIsGame by remember { mutableStateOf(false) }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Category Management (${categories.size})", style = MaterialTheme.typography.titleMedium)
                                    Button(
                                        onClick = { viewModel.initializeDefaultCategories() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_init_categories_button")
                                    ) {
                                        Text("Initialize 20 Standard Categories")
                                    }
                                    OutlinedTextField(
                                        value = newCatName,
                                        onValueChange = { newCatName = it },
                                        label = { Text("Add Custom Category") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Game category", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = newCatIsGame, onCheckedChange = { newCatIsGame = it })
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.saveCategory(newCatName, newCatIsGame, (categories.size + 1).toLong())
                                            newCatName = ""
                                        },
                                        enabled = newCatName.trim().length >= 2,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save Category")
                                    }
                                }
                            }
                        }

                        items(categories, key = { it.id }) { cat ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cat.name, style = MaterialTheme.typography.titleSmall)
                                        Text(if (cat.gameCategory) "Games" else "Apps", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        item {
                            var annTitle by remember(adminSettings) { mutableStateOf(adminSettings.announcementTitle) }
                            var annBody by remember(adminSettings) { mutableStateOf(adminSettings.announcementBody) }
                            var annActive by remember(adminSettings) { mutableStateOf(adminSettings.announcementActive) }
                            var maxMb by remember(adminSettings) { mutableStateOf(adminSettings.maxApkSizeMb.toString()) }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Marketplace Announcements & Limits", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(
                                        value = annTitle,
                                        onValueChange = { annTitle = it },
                                        label = { Text("Announcement Title") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = annBody,
                                        onValueChange = { annBody = it },
                                        label = { Text("Announcement Message") },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Show announcement on Home", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = annActive, onCheckedChange = { annActive = it })
                                    }
                                    OutlinedTextField(
                                        value = maxMb,
                                        onValueChange = { maxMb = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Max APK Upload Size (MB)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.updateMarketplaceSettings(
                                                annTitle,
                                                annBody,
                                                annActive,
                                                maxMb.toLongOrNull() ?: 250L
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save Marketplace Settings")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        rejectDialogApp?.let { (app, targetStatus) ->
            var reason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { rejectDialogApp = null },
                title = { Text("${targetStatus.replaceFirstChar { it.uppercase() }} ${app.appName}") },
                text = {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason sent to developer") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.rejectOrSuspendApp(app, targetStatus, reason)
                        rejectDialogApp = null
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { rejectDialogApp = null }) { Text("Cancel") }
                }
            )
        }

        resolveReportDialog?.let { rep ->
            var note by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { resolveReportDialog = null },
                title = { Text("Resolve Report") },
                text = {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Resolution note") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.resolveReport(rep.id, "resolved", note)
                        resolveReportDialog = null
                    }) { Text("Resolve") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.resolveReport(rep.id, "dismissed", note)
                            resolveReportDialog = null
                        }) { Text("Dismiss") }
                        TextButton(onClick = { resolveReportDialog = null }) { Text("Cancel") }
                    }
                }
            )
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
