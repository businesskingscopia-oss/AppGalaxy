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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.DeveloperStatus
import com.example.ui.components.AppIconAvatar
import com.example.ui.components.FormatUtils
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.StatusChip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DeveloperAdminViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperPortalScreen(
    currentUser: FirebaseUser?,
    isAdmin: Boolean,
    viewModel: DeveloperAdminViewModel,
    onBack: () -> Unit,
    onUploadNewApp: () -> Unit,
    onUploadUpdateForApp: (String) -> Unit,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit
) {
    val devProfile by viewModel.developerProfile.collectAsStateWithLifecycle()
    val devApps by viewModel.developerApps.collectAsStateWithLifecycle()
    val devVersions by viewModel.developerVersions.collectAsStateWithLifecycle()

    var editingApp by remember { mutableStateOf<ApplicationListing?>(null) }
    var historyApp by remember { mutableStateOf<ApplicationListing?>(null) }

    val canPublish = isAdmin || devProfile?.status == DeveloperStatus.APPROVED.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Portal") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("dev_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentUser != null && canPublish) {
                FloatingActionButton(
                    onClick = onUploadNewApp,
                    modifier = Modifier.testTag("dev_upload_new_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Upload new APK")
                }
            }
        }
    ) { innerPadding ->
        if (currentUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in to access Developer Portal", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Register your developer account, upload APK files, and track review approvals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GoogleSignInButton(onAuthSuccess = onAuthSuccess, onAuthError = onAuthError)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Developer Registration / Profile Card
            item {
                var devName by remember(devProfile) { mutableStateOf(devProfile?.developerName ?: currentUser.displayName.orEmpty()) }
                var devBio by remember(devProfile) { mutableStateOf(devProfile?.bio.orEmpty()) }
                var devEmail by remember(devProfile) { mutableStateOf(devProfile?.contactEmail ?: currentUser.email.orEmpty()) }
                var devWebsite by remember(devProfile) { mutableStateOf(devProfile?.websiteUrl.orEmpty()) }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (devProfile == null) "Developer Registration" else "Developer Profile",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (devProfile != null) {
                                StatusChip(devProfile!!.status)
                            } else if (isAdmin) {
                                StatusChip("admin")
                            }
                        }

                        if (devProfile?.status == DeveloperStatus.PENDING.value && !isAdmin) {
                            Text(
                                text = "Your developer registration is pending Admin approval. Once approved in Admin Dashboard, you can submit APK releases.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (devProfile?.status == DeveloperStatus.SUSPENDED.value) {
                            Text(
                                text = "Developer publishing access is currently suspended.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            value = devName,
                            onValueChange = { devName = it },
                            label = { Text("Studio / Developer Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = devEmail,
                            onValueChange = { devEmail = it },
                            label = { Text("Support Email") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = devWebsite,
                            onValueChange = { devWebsite = it },
                            label = { Text("Website URL (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = devBio,
                            onValueChange = { devBio = it },
                            label = { Text("Bio / About") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.registerOrUpdateDeveloper(devName, devBio, devEmail, devWebsite) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dev_save_profile_button")
                        ) {
                            Text(if (devProfile == null) "Register as Developer" else "Update Developer Profile")
                        }
                    }
                }
            }

            // Analytics Summary
            if (canPublish) {
                item {
                    val totalDownloads = devApps.sumOf { it.downloadCount }
                    val publishedCount = devApps.count { it.status == "published" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DevStatCard("Total Apps", devApps.size.toString(), Modifier.weight(1f))
                        DevStatCard("Published", publishedCount.toString(), Modifier.weight(1f))
                        DevStatCard("Downloads", FormatUtils.formatDownloads(totalDownloads), Modifier.weight(1f))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your Applications (${devApps.size})", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onUploadNewApp, modifier = Modifier.testTag("dev_upload_btn")) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Upload APK")
                        }
                    }
                }

                if (devApps.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "You haven't uploaded any applications yet. Tap 'Upload APK' to inspect and submit your first Android release.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(devApps, key = { it.id }) { app ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AppIconAvatar(app.appName, app.iconUrl, size = 48.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${app.packageName} • v${app.latestVersionName} (${app.latestVersionCode})",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusChip(app.status)
                                }

                                if (!app.rejectionReason.isNullOrBlank() && (app.status == "rejected" || app.status == "suspended")) {
                                    Text(
                                        text = "Review Feedback: ${app.rejectionReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Text(
                                    text = "${app.downloadCount} downloads • ${app.totalRatings} reviews",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editingApp = app }) {
                                        Icon(Icons.Filled.Edit, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit Info")
                                    }
                                    OutlinedButton(onClick = { onUploadUpdateForApp(app.id) }) {
                                        Icon(Icons.Filled.Upgrade, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("New APK")
                                    }
                                    OutlinedButton(onClick = { historyApp = app }) {
                                        Icon(Icons.Filled.History, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        editingApp?.let { app ->
            EditDeveloperAppDialog(
                app = app,
                onDismiss = { editingApp = null },
                onSave = { name, shortD, fullD, whatsNew, privacy, email, submit ->
                    viewModel.updateAppMetadata(app, name, shortD, fullD, whatsNew, privacy, email, submit)
                    editingApp = null
                }
            )
        }

        historyApp?.let { app ->
            val versions: List<AppVersion> = devVersions.filter { it.appId == app.id }
            AlertDialog(
                onDismissRequest = { historyApp = null },
                title = { Text("${app.appName} Versions") },
                text = {
                    if (versions.isEmpty()) {
                        Text("No version records found.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            versions.forEach { ver ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("v${ver.versionName} (${ver.versionCode})", style = MaterialTheme.typography.titleSmall)
                                        Text("Scan: ${ver.securityScanStatus}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    StatusChip(ver.status)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { historyApp = null }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
private fun DevStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditDeveloperAppDialog(
    app: ApplicationListing,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(app.appName) }
    var shortDesc by remember { mutableStateOf(app.shortDescription) }
    var fullDesc by remember { mutableStateOf(app.fullDescription) }
    var whatsNew by remember { mutableStateOf(app.whatsNew.orEmpty()) }
    var privacy by remember { mutableStateOf(app.privacyPolicyUrl) }
    var email by remember { mutableStateOf(app.contactEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${app.appName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("App Name") })
                OutlinedTextField(value = shortDesc, onValueChange = { shortDesc = it }, label = { Text("Short Description") })
                OutlinedTextField(value = fullDesc, onValueChange = { fullDesc = it }, label = { Text("Full Description") }, minLines = 2)
                OutlinedTextField(value = whatsNew, onValueChange = { whatsNew = it }, label = { Text("What's New") })
                OutlinedTextField(value = privacy, onValueChange = { privacy = it }, label = { Text("Privacy Policy URL") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Support Email") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, shortDesc, fullDesc, whatsNew, privacy, email, true) }) {
                Text("Submit Review")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(name, shortDesc, fullDesc, whatsNew, privacy, email, false) }) {
                    Text("Save Draft")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
