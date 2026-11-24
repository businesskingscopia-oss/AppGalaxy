package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.DownloadStatus
import com.example.data.model.InstalledAppState
import com.example.data.model.Review
import com.example.data.model.ReviewSortOption
import com.example.data.model.UiState
import com.example.ui.components.AppCardHorizontal
import com.example.ui.components.AppIconAvatar
import com.example.ui.components.FormatUtils
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.MarketplaceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appId: String,
    viewModel: MarketplaceViewModel,
    onBack: () -> Unit,
    onNavigateToApp: (String) -> Unit,
    onOpenSignIn: () -> Unit
) {
    val context = LocalContext.current
    val app by remember(appId) { viewModel.observeAppDetail(appId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val versions by remember(appId) { viewModel.observeAppVersions(appId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val reviews by remember(appId) { viewModel.observeAppReviews(appId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val publishedState by viewModel.publishedAppsState.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val packageTick by viewModel.packageChangeSignal.collectAsStateWithLifecycle()

    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reviewSort by remember { mutableStateOf(ReviewSortOption.MOST_HELPFUL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.appName ?: "Application Details", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    app?.let { item ->
                        val favEntry = favorites.firstOrNull { it.appId == item.id }
                        val isFav = favEntry?.listType == "favorite"
                        val isWish = favEntry?.listType == "wishlist"
                        IconButton(onClick = { viewModel.toggleFavorite(item, "favorite", isFav) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (isFav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(item, "wishlist", isWish) }) {
                            Icon(
                                imageVector = if (isWish) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Toggle wishlist",
                                tint = if (isWish) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showReportDialog = "application" to item.id }) {
                            Icon(Icons.Filled.Flag, contentDescription = "Report application")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentApp = app
        if (currentApp == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val installState = remember(currentApp, packageTick) {
            viewModel.getInstallState(currentApp)
        }
        val activeDownload = activeDownloads[currentApp.id]
        val downloadedApkReady = remember(currentApp.id, currentApp.latestVersionCode, activeDownload) {
            viewModel.downloadManager.getDownloadedApkFile(currentApp.id, currentApp.latestVersionCode) != null
        }

        val similarApps = remember(publishedState, currentApp) {
            (publishedState as? UiState.Success)?.data
                ?.filter { it.id != currentApp.id && it.categoryId == currentApp.categoryId }
                ?.take(8)
                .orEmpty()
        }

        val sortedReviews = remember(reviews, reviewSort) {
            when (reviewSort) {
                ReviewSortOption.MOST_HELPFUL -> reviews.sortedByDescending { it.helpfulCount }
                ReviewSortOption.NEWEST -> reviews.sortedByDescending { it.updatedAt?.seconds ?: 0L }
                ReviewSortOption.HIGHEST_RATED -> reviews.sortedByDescending { it.rating }
                ReviewSortOption.LOWEST_RATED -> reviews.sortedBy { it.rating }
            }
        }
        val myReview = remember(reviews, currentUserId) {
            reviews.firstOrNull { it.userId == currentUserId }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Identity Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconAvatar(
                        appName = currentApp.appName,
                        iconUrl = currentApp.iconUrl,
                        size = 82.dp,
                        cornerRadius = 20.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentApp.appName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = currentApp.developerName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentApp.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = currentApp.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Key Stats Strip
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCell(
                            value = if (currentApp.totalRatings > 0) String.format(Locale.US, "%.1f ★", currentApp.averageRating) else "New",
                            label = "${currentApp.totalRatings} reviews"
                        )
                        StatCell(
                            value = FormatUtils.formatDownloads(currentApp.downloadCount),
                            label = "Downloads"
                        )
                        StatCell(
                            value = FormatUtils.formatBytes(context, currentApp.apkSizeBytes),
                            label = "APK Size"
                        )
                        StatCell(
                            value = "v${currentApp.latestVersionName}",
                            label = FormatUtils.formatTimestamp(currentApp.updatedAt)
                        )
                    }
                }
            }

            // Smart Install / Update / Open / Download Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (activeDownload?.status) {
                        DownloadStatus.WAITING, DownloadStatus.DOWNLOADING -> {
                            val progress = if (activeDownload.totalBytes > 0L) {
                                (activeDownload.bytesDownloaded.toFloat() / activeDownload.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}% • ${FormatUtils.formatSpeed(activeDownload.bytesPerSecond)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.pauseDownload(currentApp) }) {
                                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pause")
                                    }
                                    OutlinedButton(onClick = { viewModel.cancelDownload(currentApp) }) {
                                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel")
                                    }
                                }
                            }
                        }

                        DownloadStatus.PAUSED -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.startOrResumeDownload(currentApp) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Resume Download")
                                }
                                OutlinedButton(onClick = { viewModel.cancelDownload(currentApp) }) {
                                    Text("Cancel")
                                }
                            }
                        }

                        else -> {
                            if (activeDownload?.status == DownloadStatus.FAILED && activeDownload.errorMessage != null) {
                                Text(
                                    text = "Download error: ${activeDownload.errorMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                when {
                                    installState == InstalledAppState.INSTALLED_LATEST -> {
                                        Button(
                                            onClick = { viewModel.openInstalledApp(currentApp.packageName) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("detail_open_button")
                                        ) {
                                            Text("Open")
                                        }
                                    }
                                    downloadedApkReady -> {
                                        Button(
                                            onClick = { viewModel.installDownloadedApk(currentApp) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("detail_install_apk_button")
                                        ) {
                                            Text(if (installState == InstalledAppState.UPDATE_AVAILABLE) "Install Update" else "Install APK")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = { viewModel.startOrResumeDownload(currentApp) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("detail_download_button")
                                        ) {
                                            Text(
                                                when {
                                                    activeDownload?.status == DownloadStatus.FAILED -> "Retry Download"
                                                    installState == InstalledAppState.UPDATE_AVAILABLE -> "Update (v${currentApp.latestVersionName})"
                                                    else -> "Download & Install"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Screenshots Gallery
            if (currentApp.screenshotUrls.isNotEmpty()) {
                item {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(currentApp.screenshotUrls) { url ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "${currentApp.appName} screenshot",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 180.dp, height = 300.dp)
                                )
                            }
                        }
                    }
                }
            }

            // What's New
            if (!currentApp.whatsNew.isNullOrBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("What's New in v${currentApp.latestVersionName}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(currentApp.whatsNew, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // About This App
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("About this app", style = MaterialTheme.typography.titleMedium)
                    Text(currentApp.shortDescription, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(currentApp.fullDescription, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Security, Integrity & Permissions Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldSuccess)
                            Text("APK Integrity & Permissions", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = "SHA-256: ${currentApp.apkSha256.ifBlank { "Pending" }}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Signing Cert: ${currentApp.signingCertSha256.ifBlank { "Pending" }}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { showPermissionsDialog = true }) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Permissions (${currentApp.permissions.size})")
                            }
                            if (currentApp.privacyPolicyUrl.startsWith("http")) {
                                OutlinedButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentApp.privacyPolicyUrl)))
                                    }
                                }) {
                                    Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Privacy Policy")
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Developer Support: ${currentApp.contactEmail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Version History
            if (versions.isNotEmpty()) {
                item {
                    Text("Version History", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    versions.take(5).forEach { ver ->
                        VersionRowItem(ver)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }

            // Ratings & Reviews
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ratings & Reviews (${reviews.size})", style = MaterialTheme.typography.titleMedium)
                        if (currentUserId != null) {
                            Button(onClick = { showReviewDialog = true }, modifier = Modifier.testTag("write_review_button")) {
                                Text(if (myReview == null) "Write Review" else "Edit Review")
                            }
                        } else {
                            OutlinedButton(onClick = onOpenSignIn) {
                                Text("Sign in to Review")
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReviewSortOption.entries) { opt ->
                            androidx.compose.material3.FilterChip(
                                selected = reviewSort == opt,
                                onClick = { reviewSort = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }

                    if (sortedReviews.isEmpty()) {
                        Text(
                            text = "No reviews yet. Be the first to review ${currentApp.appName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sortedReviews.forEach { rev ->
                            ReviewCardItem(
                                review = rev,
                                isOwnReview = rev.userId == currentUserId,
                                onHelpful = { viewModel.markReviewHelpful(rev.id) },
                                onDelete = { viewModel.deleteOwnReview(currentApp.id) },
                                onReport = { showReportDialog = "review" to rev.id }
                            )
                        }
                    }
                }
            }

            // Similar Applications
            if (similarApps.isNotEmpty()) {
                item {
                    Text("Similar Applications", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(similarApps, key = { it.id }) { sim ->
                            AppCardHorizontal(app = sim, onClick = { onNavigateToApp(sim.id) })
                        }
                    }
                }
            }
        }

        if (showPermissionsDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionsDialog = false },
                title = { Text("Requested Permissions") },
                text = {
                    if (currentApp.permissions.isEmpty()) {
                        Text("This application declares no special permissions.")
                    } else {
                        LazyColumn(modifier = Modifier.height(260.dp)) {
                            items(currentApp.permissions) { perm ->
                                Text(
                                    text = "• $perm",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPermissionsDialog = false }) { Text("Close") }
                }
            )
        }

        if (showReviewDialog) {
            WriteReviewDialog(
                initialRating = myReview?.rating ?: 5L,
                initialComment = myReview?.comment.orEmpty(),
                onDismiss = { showReviewDialog = false },
                onSubmit = { rating, comment ->
                    viewModel.submitReview(currentApp, rating, comment)
                    showReviewDialog = false
                }
            )
        }

        showReportDialog?.let { (targetType, targetId) ->
            ReportDialog(
                targetType = targetType,
                onDismiss = { showReportDialog = null },
                onSubmit = { reason, details ->
                    viewModel.submitReport(targetType, targetId, reason, details)
                    showReportDialog = null
                }
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VersionRowItem(version: AppVersion) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "v${version.versionName} (Code ${version.versionCode})",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = version.whatsNew,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = FormatUtils.formatTimestamp(version.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReviewCardItem(
    review: Review,
    isOwnReview: Boolean,
    onHelpful: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(review.userName, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(review.rating.toInt().coerceIn(1, 5)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(FormatUtils.formatTimestamp(review.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(review.comment, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onHelpful) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Helpful (${review.helpfulCount})")
                }
                Row {
                    if (isOwnReview) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete review", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = onReport) {
                            Icon(Icons.Filled.Flag, contentDescription = "Report review")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteReviewDialog(
    initialRating: Long,
    initialComment: String,
    onDismiss: () -> Unit,
    onSubmit: (Long, String) -> Unit
) {
    var rating by remember { mutableLongStateOf(initialRating) }
    var comment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate & Review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1L..5L).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "$star stars",
                                tint = if (star <= rating) AmberWarning else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Share your experience") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(rating, comment) },
                enabled = comment.isNotBlank()
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReportDialog(
    targetType: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var reason by remember { mutableStateOf("Policy violation / Harmful content") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report ${targetType.replaceFirstChar { it.uppercase() }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (e.g. Copyright, Malware, Spam)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Details") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason, details) },
                enabled = reason.trim().length >= 3 && details.trim().length >= 3
            ) { Text("Submit Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
