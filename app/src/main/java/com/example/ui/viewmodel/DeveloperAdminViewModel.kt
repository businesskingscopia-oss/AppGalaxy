package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.apk.ApkInspector
import com.example.data.apk.ApkStorageUploader
import com.example.data.model.ApkMetadata
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.DeveloperProfile
import com.example.data.model.Report
import com.example.data.model.Review
import com.example.data.model.UploadSession
import com.example.data.model.UserProfile
import com.example.data.repository.DeveloperAdminRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ApkUploadState(
    val inspecting: Boolean = false,
    val uploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val inspectedApk: ApkMetadata? = null,
    val uploadedApkUrl: String = "",
    val uploadedIconUrl: String = "",
    val uploadedScreenshots: List<String> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperAdminViewModel(
    private val appContext: Context,
    private val repository: DeveloperAdminRepository,
    private val storageUploader: ApkStorageUploader
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    private val _isAdmin = MutableStateFlow(false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _uploadState = MutableStateFlow(ApkUploadState())
    val uploadState: StateFlow<ApkUploadState> = _uploadState.asStateFlow()

    val developerProfile: StateFlow<DeveloperProfile?> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null)
            else repository.observeDeveloperProfile(uid).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val developerApps: StateFlow<List<ApplicationListing>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeDeveloperApps(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val developerVersions: StateFlow<List<AppVersion>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeDeveloperVersions(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val uploadSessions: StateFlow<List<UploadSession>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeDeveloperUploadSessions(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Admin Streams (strictly gated behind isAdmin == true)
    private val adminGate = combine(_currentUserId, _isAdmin) { uid, admin ->
        !uid.isNullOrBlank() && admin
    }

    val adminApplications: StateFlow<List<ApplicationListing>> = adminGate
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else repository.observeAllApplicationsForAdmin().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminDevelopers: StateFlow<List<DeveloperProfile>> = adminGate
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else repository.observeAllDevelopersForAdmin().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminUsers: StateFlow<List<UserProfile>> = adminGate
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else repository.observeAllUsersForAdmin().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminReports: StateFlow<List<Report>> = adminGate
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else repository.observeAllReportsForAdmin().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminFlaggedReviews: StateFlow<List<Review>> = adminGate
        .flatMapLatest { allowed ->
            if (!allowed) flowOf(emptyList())
            else repository.observeFlaggedReviewsForAdmin().catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun syncIdentity(uid: String?, isAdmin: Boolean) {
        _currentUserId.value = uid
        _isAdmin.value = isAdmin
        if (uid != null && isAdmin) {
            viewModelScope.launch { repository.ensureBootstrapAdminDocument() }
        }
    }

    // --- Developer Portal Actions ---

    fun registerOrUpdateDeveloper(
        developerName: String,
        bio: String,
        contactEmail: String,
        websiteUrl: String?
    ) {
        viewModelScope.launch {
            repository.registerOrUpdateDeveloper(developerName, bio, contactEmail, websiteUrl)
                .onSuccess { _events.tryEmit("Developer profile saved") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to save developer profile") }
        }
    }

    fun clearUploadState() {
        _uploadState.value = ApkUploadState()
    }

    fun inspectSelectedApk(uri: Uri, maxApkSizeMb: Long) {
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(
                inspecting = true,
                errorMessage = null,
                statusMessage = "Validating APK archive, manifest, and signing certificate…"
            )
            val result = ApkInspector.inspectApkUri(appContext, uri, maxApkSizeMb)
            result.onSuccess { meta ->
                _uploadState.value = _uploadState.value.copy(
                    inspecting = false,
                    inspectedApk = meta,
                    statusMessage = "Verified ${meta.packageName} v${meta.versionName} (${meta.versionCode})",
                    errorMessage = null
                )
            }.onFailure { err ->
                _uploadState.value = _uploadState.value.copy(
                    inspecting = false,
                    inspectedApk = null,
                    statusMessage = null,
                    errorMessage = err.message ?: "Invalid APK file"
                )
            }
        }
    }

    fun uploadInspectedApkToStorage(existingAppId: String? = null) {
        val meta = _uploadState.value.inspectedApk ?: return
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(
                uploading = true,
                uploadProgress = 0f,
                errorMessage = null,
                statusMessage = "Initializing upload session…"
            )
            val sessionResult = repository.createUploadSession(
                appId = existingAppId,
                fileName = meta.fileName,
                packageName = meta.packageName,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                totalBytes = meta.fileSizeBytes,
                sha256 = meta.sha256Checksum,
                signingCertSha256 = meta.signingCertSha256
            )
            val sessionId = sessionResult.getOrNull()
            val file = File(meta.cachedFilePath)
            val objectPath = "apks/$uid/${meta.packageName}/v${meta.versionCode}_${meta.sha256Checksum.take(8)}.apk"

            val uploadRes = storageUploader.uploadApkFile(file, objectPath) { uploaded, total ->
                val progress = if (total > 0L) uploaded.toFloat() / total.toFloat() else 0f
                _uploadState.value = _uploadState.value.copy(
                    uploadProgress = progress,
                    statusMessage = "Uploading APK (${(progress * 100).toInt()}%)…"
                )
            }

            uploadRes.onSuccess { downloadUrl ->
                if (sessionId != null) {
                    repository.updateUploadSession(sessionId, meta.fileSizeBytes, "completed", existingAppId)
                }
                _uploadState.value = _uploadState.value.copy(
                    uploading = false,
                    uploadProgress = 1f,
                    uploadedApkUrl = downloadUrl,
                    statusMessage = "APK uploaded securely to storage",
                    errorMessage = null
                )
            }.onFailure { err ->
                if (sessionId != null) {
                    repository.updateUploadSession(sessionId, 0L, "failed", existingAppId, err.message)
                }
                _uploadState.value = _uploadState.value.copy(
                    uploading = false,
                    uploadProgress = 0f,
                    statusMessage = null,
                    errorMessage = err.message ?: "APK upload failed"
                )
            }
        }
    }

    fun uploadIconToStorage(uri: Uri) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(statusMessage = "Uploading icon…", errorMessage = null)
            val objectPath = "media/$uid/icon_${System.currentTimeMillis()}.jpg"
            storageUploader.uploadImageUri(uri, objectPath)
                .onSuccess { url ->
                    _uploadState.value = _uploadState.value.copy(
                        uploadedIconUrl = url,
                        statusMessage = "Icon uploaded"
                    )
                }
                .onFailure { err ->
                    _uploadState.value = _uploadState.value.copy(
                        errorMessage = err.message ?: "Icon upload failed"
                    )
                }
        }
    }

    fun uploadScreenshotToStorage(uri: Uri) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _uploadState.value = _uploadState.value.copy(statusMessage = "Uploading screenshot…", errorMessage = null)
            val objectPath = "media/$uid/ss_${System.currentTimeMillis()}.jpg"
            storageUploader.uploadImageUri(uri, objectPath)
                .onSuccess { url ->
                    val list = (_uploadState.value.uploadedScreenshots + url).take(8)
                    _uploadState.value = _uploadState.value.copy(
                        uploadedScreenshots = list,
                        statusMessage = "Screenshot uploaded (${list.size})"
                    )
                }
                .onFailure { err ->
                    _uploadState.value = _uploadState.value.copy(
                        errorMessage = err.message ?: "Screenshot upload failed"
                    )
                }
        }
    }

    fun setManualApkUrl(url: String) {
        _uploadState.value = _uploadState.value.copy(uploadedApkUrl = url)
    }

    fun setManualIconUrl(url: String) {
        _uploadState.value = _uploadState.value.copy(uploadedIconUrl = url)
    }

    fun submitNewApplication(
        developerName: String,
        appName: String,
        category: Category,
        shortDescription: String,
        fullDescription: String,
        whatsNew: String,
        privacyPolicyUrl: String,
        contactEmail: String,
        manualApkUrl: String,
        manualIconUrl: String,
        manualScreenshotsText: String,
        submitAsDraft: Boolean,
        onComplete: () -> Unit
    ) {
        val meta = _uploadState.value.inspectedApk
        if (meta == null) {
            _events.tryEmit("Please select and validate a real .apk file first")
            return
        }
        val finalApkUrl = _uploadState.value.uploadedApkUrl.ifBlank { manualApkUrl.trim() }
        if (!submitAsDraft && !finalApkUrl.startsWith("https://")) {
            _events.tryEmit("Upload the APK to Firebase Storage or enter a valid HTTPS APK release URL")
            return
        }
        val finalIconUrl = _uploadState.value.uploadedIconUrl.ifBlank { manualIconUrl.trim() }.ifBlank { null }
        val manualShots = manualScreenshotsText.split("\n", ",").map { it.trim() }.filter { it.startsWith("https://") }
        val finalScreenshots = (_uploadState.value.uploadedScreenshots + manualShots).distinct().take(8)

        viewModelScope.launch {
            repository.submitNewApplication(
                developerName = developerName,
                appName = appName,
                packageName = meta.packageName,
                category = category,
                shortDescription = shortDescription,
                fullDescription = fullDescription,
                iconUrl = finalIconUrl,
                screenshotUrls = finalScreenshots,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                apkUrl = finalApkUrl,
                apkSizeBytes = meta.fileSizeBytes,
                apkSha256 = meta.sha256Checksum,
                signingCertSha256 = meta.signingCertSha256,
                whatsNew = whatsNew,
                permissions = meta.permissions,
                minSdkVersion = meta.minSdkVersion,
                targetSdkVersion = meta.targetSdkVersion,
                privacyPolicyUrl = privacyPolicyUrl,
                contactEmail = contactEmail,
                submitAsDraft = submitAsDraft
            ).onSuccess {
                clearUploadState()
                _events.tryEmit(if (submitAsDraft) "Saved as Draft" else "Submitted for Admin Review!")
                onComplete()
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Submission failed")
            }
        }
    }

    fun submitApplicationUpdate(
        existingApp: ApplicationListing,
        whatsNew: String,
        manualApkUrl: String,
        onComplete: () -> Unit
    ) {
        val meta = _uploadState.value.inspectedApk
        if (meta == null) {
            _events.tryEmit("Please select and validate the updated .apk file first")
            return
        }
        if (meta.packageName != existingApp.packageName) {
            _events.tryEmit("Package mismatch: APK is '${meta.packageName}', expected '${existingApp.packageName}'")
            return
        }
        val finalApkUrl = _uploadState.value.uploadedApkUrl.ifBlank { manualApkUrl.trim() }
        if (!finalApkUrl.startsWith("https://")) {
            _events.tryEmit("Upload the APK to Firebase Storage or enter a valid HTTPS APK release URL")
            return
        }

        viewModelScope.launch {
            repository.submitApplicationUpdate(
                existingApp = existingApp,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                apkUrl = finalApkUrl,
                apkSizeBytes = meta.fileSizeBytes,
                apkSha256 = meta.sha256Checksum,
                signingCertSha256 = meta.signingCertSha256,
                whatsNew = whatsNew,
                permissions = meta.permissions,
                minSdkVersion = meta.minSdkVersion,
                targetSdkVersion = meta.targetSdkVersion
            ).onSuccess {
                clearUploadState()
                _events.tryEmit("Update v${meta.versionName} submitted for review!")
                onComplete()
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Update submission failed")
            }
        }
    }

    fun updateAppMetadata(
        app: ApplicationListing,
        appName: String,
        shortDesc: String,
        fullDesc: String,
        whatsNew: String,
        privacyUrl: String,
        contactEmail: String,
        submitForReview: Boolean
    ) {
        viewModelScope.launch {
            repository.updateDeveloperAppMetadata(
                app, appName, shortDesc, fullDesc, whatsNew, privacyUrl, contactEmail, submitForReview
            ).onSuccess {
                _events.tryEmit(if (submitForReview) "Submitted for review" else "Saved changes")
            }.onFailure {
                _events.tryEmit(it.message ?: "Update failed")
            }
        }
    }

    // --- Admin Actions ---

    fun initializeDefaultCategories() {
        viewModelScope.launch {
            repository.initializeDefaultCategories()
                .onSuccess { _events.tryEmit("20 standard categories initialized") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to initialize categories") }
        }
    }

    fun saveCategory(name: String, gameCategory: Boolean, sortOrder: Long) {
        viewModelScope.launch {
            repository.saveCategory(name, gameCategory, sortOrder)
                .onSuccess { _events.tryEmit("Category saved") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to save category") }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess { _events.tryEmit("Category removed") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to delete category") }
        }
    }

    fun approveAndPublishApp(app: ApplicationListing) {
        viewModelScope.launch {
            repository.approveAndPublishApplication(app)
                .onSuccess { _events.tryEmit("${app.appName} is now Published!") }
                .onFailure { _events.tryEmit(it.message ?: "Approval failed") }
        }
    }

    fun rejectOrSuspendApp(app: ApplicationListing, status: String, reason: String) {
        viewModelScope.launch {
            repository.rejectOrSuspendApplication(app, status, reason)
                .onSuccess { _events.tryEmit("${app.appName} marked $status") }
                .onFailure { _events.tryEmit(it.message ?: "Action failed") }
        }
    }

    fun removeApp(appId: String) {
        viewModelScope.launch {
            repository.removeApplication(appId)
                .onSuccess { _events.tryEmit("Application deleted") }
                .onFailure { _events.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun toggleAppFlags(app: ApplicationListing, featured: Boolean, editorsPick: Boolean, recommended: Boolean) {
        viewModelScope.launch {
            repository.toggleAppCurationFlags(app.id, featured, editorsPick, recommended)
                .onSuccess { _events.tryEmit("Curation flags updated") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to update flags") }
        }
    }

    fun setDeveloperStatus(developerId: String, status: String) {
        viewModelScope.launch {
            repository.setDeveloperStatus(developerId, status)
                .onSuccess { _events.tryEmit("Developer marked $status") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to update developer") }
        }
    }

    fun setUserStatus(userId: String, status: String) {
        viewModelScope.launch {
            repository.setUserStatus(userId, status)
                .onSuccess { _events.tryEmit("User marked $status") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to update user") }
        }
    }

    fun moderateReview(reviewId: String, status: String) {
        viewModelScope.launch {
            repository.moderateReview(reviewId, status)
                .onSuccess { _events.tryEmit("Review marked $status") }
                .onFailure { _events.tryEmit(it.message ?: "Moderation failed") }
        }
    }

    fun resolveReport(reportId: String, status: String, note: String) {
        viewModelScope.launch {
            repository.resolveReport(reportId, status, note)
                .onSuccess { _events.tryEmit("Report $status") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to update report") }
        }
    }

    fun updateMarketplaceSettings(title: String, body: String, active: Boolean, maxApkMb: Long) {
        viewModelScope.launch {
            repository.updateMarketplaceSettings(title, body, active, maxApkMb)
                .onSuccess { _events.tryEmit("Marketplace settings saved") }
                .onFailure { _events.tryEmit(it.message ?: "Failed to save settings") }
        }
    }
}
