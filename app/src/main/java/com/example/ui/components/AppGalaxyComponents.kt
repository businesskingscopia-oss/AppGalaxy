package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.auth.AuthManager
import com.example.data.model.ApplicationListing
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GalaxyBlue
import com.example.ui.theme.GalaxyBlueContainer
import com.example.ui.theme.GalaxyBlueDark
import com.example.ui.theme.GalaxyOnBlueContainer
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Locale

object FormatUtils {
    fun formatBytes(context: android.content.Context, bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        return Formatter.formatShortFileSize(context, bytes)
    }

    fun formatDownloads(count: Long): String = when {
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM+", count / 1_000_000.0)
        count >= 1_000L -> String.format(Locale.US, "%.1fK+", count / 1_000.0)
        else -> "$count"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024.0) {
            String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return "Just now"
        return SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    }
}

@Composable
fun AppGalaxyTopBar(
    currentUser: FirebaseUser?,
    unreadNotificationsCount: Int,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brand Emblem
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(GalaxyBlue, GalaxyBlueDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = "AppGalaxy logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Search Pill Trigger
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onSearchClick)
                        .testTag("top_search_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search AppGalaxy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Search AppGalaxy",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.testTag("notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationsCount > 0) {
                                Badge { Text(unreadNotificationsCount.coerceAtMost(99).toString()) }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (unreadNotificationsCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.testTag("top_profile_button")
                ) {
                    val photoUrl = currentUser?.photoUrl?.toString()
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "User profile picture",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Sign in or view profile",
                            tint = if (currentUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSignInButton(
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Sign in with Google"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isLoading = true
            AuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = { user ->
                    isLoading = false
                    onAuthSuccess(user)
                },
                onAuthError = { msg ->
                    isLoading = false
                    onAuthError(msg)
                },
                onAuthCancelled = {
                    isLoading = false
                }
            )
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.testTag("google_sign_in_button")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Signing in…")
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppIconAvatar(
    appName: String,
    iconUrl: String?,
    size: Dp = 56.dp,
    cornerRadius: Dp = 14.dp
) {
    val fallbackBg = remember(appName) {
        val palette = listOf(
            Color(0xFF2563EB),
            Color(0xFF0EA5E9),
            Color(0xFF10B981),
            Color(0xFF6366F1),
            Color(0xFFF59E0B),
            Color(0xFFEC4899)
        )
        palette[(appName.hashCode().let { if (it < 0) -it else it }) % palette.size]
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(fallbackBg.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = "$appName icon",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = appName.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = fallbackBg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AppCardHorizontal(
    app: ApplicationListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(156.dp)
            .testTag("app_card_${app.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppIconAvatar(
                appName = app.appName,
                iconUrl = app.iconUrl,
                size = 64.dp,
                cornerRadius = 16.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.developerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (app.totalRatings > 0) String.format(Locale.US, "%.1f", app.averageRating) else "New",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• ${FormatUtils.formatBytes(context, app.apkSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppListRow(
    app: ApplicationListing,
    onClick: () -> Unit,
    trailingBadge: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_row_${app.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconAvatar(
                appName = app.appName,
                iconUrl = app.iconUrl,
                size = 56.dp,
                cornerRadius = 14.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${app.developerName} • ${app.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = app.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (app.totalRatings > 0) String.format(Locale.US, "%.1f (%d)", app.averageRating, app.totalRatings) else "Unrated",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = "${FormatUtils.formatDownloads(app.downloadCount)} downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FormatUtils.formatBytes(context, app.apkSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingBadge != null) {
                trailingBadge()
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (bg, fg, label) = when (status.lowercase()) {
        "published", "approved", "verified", "completed", "resolved", "active" -> Triple(
            EmeraldContainer,
            EmeraldSuccess,
            status.replace("_", " ").replaceFirstChar { it.uppercase() }
        )
        "pending_review", "pending", "downloading", "validating", "waiting", "open" -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFFB45309),
            status.replace("_", " ").replaceFirstChar { it.uppercase() }
        )
        "rejected", "suspended", "failed", "flagged", "removed" -> Triple(
            RoseContainer,
            RoseError,
            status.replace("_", " ").replaceFirstChar { it.uppercase() }
        )
        else -> Triple(
            GalaxyBlueContainer,
            GalaxyOnBlueContainer,
            status.replace("_", " ").replaceFirstChar { it.uppercase() }
        )
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
