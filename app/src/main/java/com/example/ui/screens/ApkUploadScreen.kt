package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AdminSettings
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.ui.components.FormatUtils
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DeveloperAdminViewModel
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUploadScreen(
    existingAppId: String?,
    currentUser: FirebaseUser?,
    categories: List<Category>,
    adminSettings: AdminSettings,
    viewModel: DeveloperAdminViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val devProfile by viewModel.developerProfile.collectAsStateWithLifecycle()
    val devApps by viewModel.developerApps.collectAsStateWithLifecycle()

    val existingApp: ApplicationListing? = remember(existingAppId, devApps) {
        existingAppId?.let { id -> devApps.firstOrNull { it.id == id } }
    }

    var appName by remember(existingApp) { mutableStateOf(existingApp?.appName.orEmpty()) }
    var shortDesc by remember(existingApp) { mutableStateOf(existingApp?.shortDescription.orEmpty()) }
    var fullDesc by remember(existingApp) { mutableStateOf(existingApp?.fullDescription.orEmpty()) }
    var whatsNew by remember { mutableStateOf("") }
    var privacyUrl by remember(existingApp) { mutableStateOf(existingApp?.privacyPolicyUrl ?: "https://") }
    var contactEmail by remember(existingApp, devProfile, currentUser) {
        mutableStateOf(existingApp?.contactEmail ?: devProfile?.contactEmail ?: currentUser?.email.orEmpty())
    }
    var selectedCategory by remember(categories, existingApp) {
        mutableStateOf(
            categories.firstOrNull { it.id == existingApp?.categoryId } ?: categories.firstOrNull()
        )
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var manualApkUrl by remember { mutableStateOf("") }
    var manualIconUrl by remember { mutableStateOf("") }
    var manualScreenshots by remember { mutableStateOf("") }

    // Pre-fill appName once APK manifest is inspected for new apps
    LaunchedEffect(uploadState.inspectedApk) {
        val meta = uploadState.inspectedApk
        if (meta != null && existingApp == null && appName.isBlank()) {
            appName = meta.appName
        }
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.inspectSelectedApk(uri, adminSettings.maxApkSizeMb)
            }
        }
    )

    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) viewModel.uploadIconToStorage(uri)
        }
    )

    val screenshotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) viewModel.uploadScreenshotToStorage(uri)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (existingApp == null) "Upload & Publish APK" else "Publish Update: ${existingApp.appName}")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearUploadState()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Select & Inspect APK File
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Select & Validate APK File", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "AppGalaxy inspects the ZIP archive, AndroidManifest, package name, versionCode, SHA-256 checksum, permissions, and signing certificate (max ${adminSettings.maxApkSizeMb} MB).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                apkPickerLauncher.launch(
                                    arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
                                )
                            },
                            enabled = !uploadState.inspecting && !uploadState.uploading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_apk_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (uploadState.inspectedApk == null) "Select .apk File" else "Choose Another .apk")
                        }

                        if (uploadState.inspecting) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                                Text(uploadState.statusMessage ?: "Inspecting APK…", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        uploadState.inspectedApk?.let { meta ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldSuccess)
                                        Text("APK Verified: ${meta.fileName}", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text("Package: ${meta.packageName}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily))
                                    Text("Version: ${meta.versionName} (Code ${meta.versionCode})", style = MaterialTheme.typography.bodySmall)
                                    Text("Size: ${FormatUtils.formatBytes(context, meta.fileSizeBytes)} • SDK ${meta.minSdkVersion}–${meta.targetSdkVersion}", style = MaterialTheme.typography.bodySmall)
                                    Text("Permissions detected: ${meta.permissions.size}", style = MaterialTheme.typography.bodySmall)
                                    Text("SHA-256: ${meta.sha256Checksum.take(24)}…", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily))
                                    Text("Cert SHA-256: ${meta.signingCertSha256.take(24)}…", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily))
                                }
                            }

                            Button(
                                onClick = { viewModel.uploadInspectedApkToStorage(existingAppId) },
                                enabled = !uploadState.uploading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upload_apk_storage_button")
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (uploadState.uploadedApkUrl.isNotBlank()) "Re-upload APK to Storage" else "Upload APK to Cloud Storage")
                            }
                        }

                        if (uploadState.uploading) {
                            LinearProgressIndicator(
                                progress = { uploadState.uploadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(uploadState.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }

                        if (uploadState.uploadedApkUrl.isNotBlank()) {
                            Text(
                                text = "Hosted APK URL ready: ${uploadState.uploadedApkUrl.take(64)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldSuccess
                            )
                        }

                        if (!uploadState.errorMessage.isNullOrBlank()) {
                            Text(
                                text = uploadState.errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            value = manualApkUrl,
                            onValueChange = { manualApkUrl = it },
                            label = { Text("Or Verified HTTPS APK Release URL (if Storage not enabled)") },
                            placeholder = { Text("https://cdn.example.com/releases/app-v1.apk") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Step 2: Store Listing Metadata (New App only)
            if (existingApp == null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("2. Store Listing & Visual Media", style = MaterialTheme.typography.titleMedium)

                            OutlinedTextField(
                                value = appName,
                                onValueChange = { appName = it },
                                label = { Text("Application Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { categoryExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        selectedCategory?.let { "Category: ${it.name}" }
                                            ?: "No categories yet (Ask Admin to initialize categories)"
                                    )
                                }
                                DropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = {
                                                selectedCategory = cat
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = shortDesc,
                                onValueChange = { shortDesc = it },
                                label = { Text("Short Description (max 240 chars)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = fullDesc,
                                onValueChange = { fullDesc = it },
                                label = { Text("Full Description") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = privacyUrl,
                                onValueChange = { privacyUrl = it },
                                label = { Text("Privacy Policy URL") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = contactEmail,
                                onValueChange = { contactEmail = it },
                                label = { Text("Developer Contact Email") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        iconPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick Icon")
                                }
                                OutlinedButton(
                                    onClick = {
                                        screenshotPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Screenshot (${uploadState.uploadedScreenshots.size})")
                                }
                            }

                            OutlinedTextField(
                                value = manualIconUrl,
                                onValueChange = { manualIconUrl = it },
                                label = { Text("Icon HTTPS URL (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = manualScreenshots,
                                onValueChange = { manualScreenshots = it },
                                label = { Text("Screenshot HTTPS URLs (comma/newline separated, optional)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Step 3: Release Notes & Submit
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (existingApp == null) "3. Release Notes & Submission" else "2. What's New & Submission", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = whatsNew,
                            onValueChange = { whatsNew = it },
                            label = { Text("What's New in this release") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "All submitted APKs enter Pending Review and must be approved by an administrator before appearing in the public marketplace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (existingApp == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        val cat = selectedCategory ?: return@OutlinedButton
                                        viewModel.submitNewApplication(
                                            developerName = devProfile?.developerName ?: currentUser?.displayName ?: "AppGalaxy Developer",
                                            appName = appName,
                                            category = cat,
                                            shortDescription = shortDesc,
                                            fullDescription = fullDesc,
                                            whatsNew = whatsNew,
                                            privacyPolicyUrl = privacyUrl,
                                            contactEmail = contactEmail,
                                            manualApkUrl = manualApkUrl,
                                            manualIconUrl = manualIconUrl,
                                            manualScreenshotsText = manualScreenshots,
                                            submitAsDraft = true,
                                            onComplete = onBack
                                        )
                                    },
                                    enabled = uploadState.inspectedApk != null && selectedCategory != null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save Draft")
                                }
                                Button(
                                    onClick = {
                                        val cat = selectedCategory ?: return@Button
                                        viewModel.submitNewApplication(
                                            developerName = devProfile?.developerName ?: currentUser?.displayName ?: "AppGalaxy Developer",
                                            appName = appName,
                                            category = cat,
                                            shortDescription = shortDesc,
                                            fullDescription = fullDesc,
                                            whatsNew = whatsNew,
                                            privacyPolicyUrl = privacyUrl,
                                            contactEmail = contactEmail,
                                            manualApkUrl = manualApkUrl,
                                            manualIconUrl = manualIconUrl,
                                            manualScreenshotsText = manualScreenshots,
                                            submitAsDraft = false,
                                            onComplete = onBack
                                        )
                                    },
                                    enabled = uploadState.inspectedApk != null && selectedCategory != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("submit_app_review_button")
                                ) {
                                    Text("Submit Review")
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.submitApplicationUpdate(
                                        existingApp = existingApp,
                                        whatsNew = whatsNew,
                                        manualApkUrl = manualApkUrl,
                                        onComplete = onBack
                                    )
                                },
                                enabled = uploadState.inspectedApk != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("submit_update_review_button")
                            ) {
                                Text("Submit Update for Review")
                            }
                        }
                    }
                }
            }
        }
    }
}
