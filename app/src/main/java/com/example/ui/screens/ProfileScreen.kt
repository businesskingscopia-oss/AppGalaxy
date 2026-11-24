package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.apk.PackageInstallHelper
import com.example.data.auth.AuthManager
import com.example.data.model.ApplicationListing
import com.example.data.model.DownloadRecord
import com.example.data.model.FavoriteItem
import com.example.data.model.Review
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.AppIconAvatar
import com.example.ui.components.FormatUtils
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.JetBrainsMonoFamily
import com.google.firebase.auth.FirebaseUser

@Composable
fun ProfileScreen(
    currentUser: FirebaseUser?,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    appsState: UiState<List<ApplicationListing>>,
    favorites: List<FavoriteItem>,
    downloadHistory: List<DownloadRecord>,
    userReviews: List<Review>,
    themeMode: ThemeMode,
    updateAlertsEnabled: Boolean,
    downloadAlertsEnabled: Boolean,
    packageTick: Int,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUpdateAlertsChange: (Boolean) -> Unit,
    onDownloadAlertsChange: (Boolean) -> Unit,
    onOpenDeveloperPortal: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenLegal: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var libraryTab by remember { mutableIntStateOf(0) }

    val publishedApps = remember(appsState) {
        (appsState as? UiState.Success)?.data.orEmpty()
    }
    val installedCatalogApps = remember(publishedApps, packageTick) {
        publishedApps.filter { PackageInstallHelper.getInstalledPackageInfo(context, it.packageName) != null }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account Header / Sign-In Card
        item {
            if (currentUser == null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text("Sign in to AppGalaxy", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Sign in with Google to write reviews, save favorites & wishlist, track downloads, and publish apps via the Developer Portal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GoogleSignInButton(
                            onAuthSuccess = onAuthSuccess,
                            onAuthError = onAuthError,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val photoUrl = currentUser.photoUrl?.toString()
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Google profile photo",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile?.displayName ?: currentUser.displayName ?: "AppGalaxy User",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = userProfile?.email ?: currentUser.email.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusChip(userProfile?.status ?: "active")
                                    if (isAdmin) StatusChip("admin")
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "UID: ${currentUser.uid}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Created: ${FormatUtils.formatTimestamp(userProfile?.createdAt)} • Last login: ${FormatUtils.formatTimestamp(userProfile?.lastLoginAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Portal & Ecosystem Quick Actions
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    ProfileNavRow(
                        icon = Icons.Filled.Code,
                        title = "Developer Portal",
                        subtitle = "Upload APKs, publish updates & view stats",
                        onClick = onOpenDeveloperPortal,
                        testTag = "profile_dev_portal_row"
                    )
                    if (isAdmin) {
                        HorizontalDivider()
                        ProfileNavRow(
                            icon = Icons.Filled.AdminPanelSettings,
                            title = "Admin Dashboard",
                            subtitle = "Moderate apps, developers, users & categories",
                            onClick = onOpenAdminDashboard,
                            testTag = "profile_admin_dashboard_row"
                        )
                    }
                    HorizontalDivider()
                    ProfileNavRow(
                        icon = Icons.Filled.Notifications,
                        title = "Notifications",
                        subtitle = "Approvals, rejections & update alerts",
                        onClick = onOpenNotifications,
                        testTag = "profile_notifications_row"
                    )
                    HorizontalDivider()
                    ProfileNavRow(
                        icon = Icons.Filled.Gavel,
                        title = "Legal, Privacy & Policies",
                        subtitle = "Privacy, Terms, Copyright, Security & Contact",
                        onClick = onOpenLegal,
                        testTag = "profile_legal_row"
                    )
                }
            }
        }

        // User Library (Installed / Favorites / Wishlist / My Reviews)
        if (currentUser != null) {
            item {
                val tabs = listOf(
                    "Installed (${installedCatalogApps.size})",
                    "Favorites (${favorites.count { it.listType == "favorite" }})",
                    "Wishlist (${favorites.count { it.listType == "wishlist" }})",
                    "Reviews (${userReviews.size})"
                )
                TabRow(selectedTabIndex = libraryTab) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = libraryTab == idx,
                            onClick = { libraryTab = idx },
                            text = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        )
                    }
                }
            }

            when (libraryTab) {
                0 -> {
                    if (installedCatalogApps.isEmpty()) {
                        item { EmptyLibraryNote("No AppGalaxy catalog apps currently detected as installed on this device.") }
                    } else {
                        items(installedCatalogApps, key = { "inst_${it.id}" }) { app ->
                            LibraryAppRow(app.appName, app.developerName, app.iconUrl) { onAppClick(app.id) }
                        }
                    }
                }
                1 -> {
                    val favs = favorites.filter { it.listType == "favorite" }
                    if (favs.isEmpty()) {
                        item { EmptyLibraryNote("No favorite apps saved yet.") }
                    } else {
                        items(favs, key = { "fav_${it.appId}" }) { item ->
                            LibraryAppRow(item.appName, item.developerName, item.iconUrl) { onAppClick(item.appId) }
                        }
                    }
                }
                2 -> {
                    val wish = favorites.filter { it.listType == "wishlist" }
                    if (wish.isEmpty()) {
                        item { EmptyLibraryNote("Your wishlist is empty.") }
                    } else {
                        items(wish, key = { "wish_${it.appId}" }) { item ->
                            LibraryAppRow(item.appName, item.developerName, item.iconUrl) { onAppClick(item.appId) }
                        }
                    }
                }
                3 -> {
                    if (userReviews.isEmpty()) {
                        item { EmptyLibraryNote("You haven't submitted any reviews yet.") }
                    } else {
                        items(userReviews, key = { "rev_${it.id}" }) { rev ->
                            Card(
                                onClick = { onAppClick(rev.appId) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        repeat(rev.rating.toInt().coerceIn(1, 5)) {
                                            Icon(Icons.Filled.Star, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(14.dp))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(FormatUtils.formatTimestamp(rev.updatedAt), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(rev.comment, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings & Privacy
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance & Notification Settings", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update notifications", style = MaterialTheme.typography.bodyMedium)
                            Text("Notify only when a newer version exists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = updateAlertsEnabled, onCheckedChange = onUpdateAlertsChange)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download & submission alerts", style = MaterialTheme.typography.bodyMedium)
                            Text("Completed APKs and developer review updates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = downloadAlertsEnabled, onCheckedChange = onDownloadAlertsChange)
                    }

                    if (currentUser != null) {
                        HorizontalDivider()
                        OutlinedButton(
                            onClick = { AuthManager.signOut(credentialManager, scope) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryAppRow(appName: String, devName: String, iconUrl: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
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
            AppIconAvatar(appName, iconUrl, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleSmall)
                Text(devName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyLibraryNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
