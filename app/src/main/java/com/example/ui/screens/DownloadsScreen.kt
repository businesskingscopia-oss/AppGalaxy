package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.apk.ActiveDownloadState
import com.example.data.apk.PackageInstallHelper
import com.example.data.model.ApplicationListing
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.data.model.InstalledAppState
import com.example.data.model.UiState
import com.example.ui.components.AppIconAvatar
import com.example.ui.components.FormatUtils
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.StatusChip
import com.google.firebase.auth.FirebaseUser

@Composable
fun DownloadsScreen(
    currentUser: FirebaseUser?,
    appsState: UiState<List<ApplicationListing>>,
    activeDownloads: Map<String, ActiveDownloadState>,
    downloadHistory: List<DownloadRecord>,
    packageTick: Int,
    onStartOrResume: (ApplicationListing) -> Unit,
    onPause: (ApplicationListing) -> Unit,
    onCancel: (ApplicationListing) -> Unit,
    onInstallApk: (ApplicationListing) -> Unit,
    onAppClick: (String) -> Unit,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val publishedApps = remember(appsState) {
        (appsState as? UiState.Success)?.data.orEmpty()
    }
    val appById = remember(publishedApps) { publishedApps.associateBy { it.id } }

    // Genuine OS comparison: only show updates when installed package versionCode < latestVersionCode
    val availableUpdates = remember(publishedApps, packageTick) {
        publishedApps.filter { app ->
            PackageInstallHelper.resolveAppInstallState(context, app.packageName, app.latestVersionCode) ==
                InstalledAppState.UPDATE_AVAILABLE
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Downloads & Updates", style = MaterialTheme.typography.headlineSmall)
        }

        if (currentUser == null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sign in to sync your personal download history", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Active local downloads work immediately, and signing in with Google unlocks cloud download history and personalized updates.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        GoogleSignInButton(onAuthSuccess = onAuthSuccess, onAuthError = onAuthError)
                    }
                }
            }
        }

        // Available Updates
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Available Updates (${availableUpdates.size})", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (availableUpdates.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "All installed AppGalaxy apps are up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(availableUpdates, key = { "upd_${it.id}" }) { app ->
                Card(
                    onClick = { onAppClick(app.id) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconAvatar(app.appName, app.iconUrl, size = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "New v${app.latestVersionName} • ${FormatUtils.formatBytes(context, app.apkSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!app.whatsNew.isNullOrBlank()) {
                                Text(app.whatsNew, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                        Button(onClick = { onStartOrResume(app) }) {
                            Text("Update")
                        }
                    }
                }
            }
        }

        // Active Downloads
        if (activeDownloads.isNotEmpty()) {
            item {
                Text("Active Queue", style = MaterialTheme.typography.titleMedium)
            }
            items(activeDownloads.values.toList(), key = { "act_${it.appId}" }) { dl ->
                val app = appById[dl.appId]
                ActiveDownloadCard(
                    dl = dl,
                    onPause = { app?.let(onPause) },
                    onResume = { app?.let(onStartOrResume) },
                    onCancel = { app?.let(onCancel) },
                    onInstall = { app?.let(onInstallApk) },
                    onOpenDetail = { onAppClick(dl.appId) }
                )
            }
        }

        // Cloud Download History
        if (currentUser != null) {
            item {
                Text("Download History", style = MaterialTheme.typography.titleMedium)
            }
            if (downloadHistory.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No recorded downloads yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(downloadHistory, key = { it.id }) { rec ->
                    val app = appById[rec.appId]
                    Card(
                        onClick = { onAppClick(rec.appId) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rec.appName} (v${rec.versionName})", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${FormatUtils.formatBytes(context, rec.totalBytes)} • ${FormatUtils.formatTimestamp(rec.updatedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!rec.errorMessage.isNullOrBlank()) {
                                    Text(rec.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusChip(rec.status)
                                if (app != null && rec.status == DownloadStatus.COMPLETED.value) {
                                    OutlinedButton(onClick = { onInstallApk(app) }) {
                                        Text("Install")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    dl: ActiveDownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (dl.totalBytes > 0L) (dl.bytesDownloaded.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        onClick = onOpenDetail,
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
                    Text("${dl.appName} v${dl.versionName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${FormatUtils.formatBytes(context, dl.bytesDownloaded)} / ${FormatUtils.formatBytes(context, dl.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(dl.status.value)
            }

            if (dl.status == DownloadStatus.DOWNLOADING || dl.status == DownloadStatus.WAITING) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(FormatUtils.formatSpeed(dl.bytesPerSecond), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
            } else if (dl.status == DownloadStatus.PAUSED || dl.status == DownloadStatus.FAILED) {
                if (!dl.errorMessage.isNullOrBlank()) {
                    Text(dl.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResume) {
                        Icon(if (dl.status == DownloadStatus.FAILED) Icons.Filled.Refresh else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (dl.status == DownloadStatus.FAILED) "Retry" else "Resume")
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            } else if (dl.status == DownloadStatus.COMPLETED) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install Verified APK")
                }
            }
        }
    }
}
