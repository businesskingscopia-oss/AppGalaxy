package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.ui.components.FormatUtils
import com.example.ui.components.GoogleSignInButton
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    currentUser: FirebaseUser?,
    notifications: List<AppNotification>,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    Icon(Icons.Filled.NotificationsNone, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("Sign in to view notifications", style = MaterialTheme.typography.titleMedium)
                    GoogleSignInButton(onAuthSuccess = onAuthSuccess, onAuthError = onAuthError)
                }
            }
            return@Scaffold
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No notifications yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Card(
                        onClick = {
                            onMarkRead(item.id)
                            item.targetAppId?.takeIf { it.isNotBlank() }?.let(onOpenApp)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.read) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!item.read) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(item.body, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    FormatUtils.formatTimestamp(item.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LegalSection(val title: String, val body: String) {
    PRIVACY(
        "Privacy Policy",
        "AppGalaxy collects only the Google account profile fields required for authentication (name, email, photo URL, UID), user-authored reviews, favorites, and download history. Personal data is stored securely in Cloud Firestore with owner-only access rules. We never sell user data or expose private credentials."
    ),
    TERMS(
        "Terms & Conditions",
        "By using AppGalaxy to discover, download, or publish Android applications, you agree to comply with applicable laws, respect intellectual property rights, and refrain from distributing malicious, deceptive, or unauthorized APK binaries."
    ),
    COPYRIGHT(
        "Copyright & DMCA Policy",
        "AppGalaxy respects the intellectual property of developers and rights holders. Verified copyright infringement notices submitted through the Contact & Report mechanism are reviewed by administrators and may result in immediate suspension or removal of infringing listings."
    ),
    DEVELOPER_AGREEMENT(
        "Developer Agreement",
        "Developers must register accurate studio contact details, maintain consistent APK signing keys across application updates, declare an accurate Privacy Policy URL, and submit only applications they are authorized to distribute."
    ),
    SUBMISSION_POLICY(
        "App Submission Policy",
        "Every new APK and version update enters Pending Review before public listing. Submissions undergo manifest inspection, package name validation, signing certificate verification, SHA-256 checksum computation, and administrator approval."
    ),
    CONTENT_GUIDELINES(
        "Content Guidelines",
        "Applications and reviews must not contain malware, deceptive impersonation, hate speech, harassment, illegal content, or unauthorized data harvesting."
    ),
    SECURITY_POLICY(
        "Security Policy",
        "AppGalaxy enforces Firebase Security Rules with zero-trust ownership checks, verified administrator roles, APK SHA-256 integrity checks prior to installation, and Android's official PackageInstaller permission flow without bypassing OS security."
    ),
    CONTACT(
        "Contact Us & Infringement Report",
        "Need support or want to report copyright infringement or a harmful application? Submit a verified report below for immediate administrator review."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPoliciesScreen(
    currentUser: FirebaseUser?,
    onBack: () -> Unit,
    onSubmitContactOrReport: (String, String) -> Unit
) {
    var selectedSection by remember { mutableStateOf(LegalSection.PRIVACY) }
    var targetId by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Legal, Security & Policies") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LegalSection.entries) { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.title) }
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selectedSection.title, style = MaterialTheme.typography.titleLarge)
                        Text(selectedSection.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Submit Support / Copyright / Safety Notice", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = targetId,
                            onValueChange = { targetId = it },
                            label = { Text("Package Name or Subject") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = details,
                            onValueChange = { details = it },
                            label = { Text("Details") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onSubmitContactOrReport(targetId.ifBlank { "general_support" }, details)
                                targetId = ""
                                details = ""
                            },
                            enabled = currentUser != null && details.trim().length >= 3,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentUser == null) "Sign in to Submit Notice" else "Submit Notice")
                        }
                    }
                }
            }
        }
    }
}
