package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.apk.ActiveDownloadState
import com.example.data.apk.ApkDownloadManager
import com.example.data.apk.PackageInstallHelper
import com.example.data.firebase.unwrapFirestoreError
import com.example.data.local.SearchHistoryDataStore
import com.example.data.model.AdminSettings
import com.example.data.model.AppNotification
import com.example.data.model.AppVersion
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import com.example.data.model.FavoriteItem
import com.example.data.model.InstalledAppState
import com.example.data.model.Review
import com.example.data.model.SearchSortOption
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.MarketplaceRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModel(
    private val appContext: Context,
    private val repository: MarketplaceRepository,
    val downloadManager: ApkDownloadManager,
    private val prefs: SearchHistoryDataStore
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    // Public catalog state
    val publishedAppsState: StateFlow<UiState<List<ApplicationListing>>> =
        repository.observePublishedApps()
            .map<List<ApplicationListing>, UiState<List<ApplicationListing>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing published apps", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load applications"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val categoriesState: StateFlow<List<Category>> =
        repository.observeCategories()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminSettings: StateFlow<AdminSettings> =
        repository.observeAdminSettings()
            .catch { emit(AdminSettings()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminSettings())

    // User-scoped state (gated strictly on non-blank UID)
    val userProfile: StateFlow<UserProfile?> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null)
            else repository.observeUserProfile(uid).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val isAdmin: StateFlow<Boolean> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(false)
            else repository.observeIsAdmin(uid).catch { emit(false) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val downloadHistory: StateFlow<List<DownloadRecord>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeDownloads(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val favorites: StateFlow<List<FavoriteItem>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeFavorites(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeNotifications(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userReviews: StateFlow<List<Review>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else repository.observeUserReviews(uid).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeDownloads: StateFlow<Map<String, ActiveDownloadState>> = downloadManager.downloads

    // OS package state refresh counter
    val packageChangeSignal: StateFlow<Int> = PackageInstallHelper.observePackageChanges(appContext)
        .map { System.identityHashCode(Any()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    // Preferences & Search State
    val recentSearches: StateFlow<List<String>> = prefs.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemeMode.SYSTEM)

    val updateAlertsEnabled: StateFlow<Boolean> = prefs.updateAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val downloadAlertsEnabled: StateFlow<Boolean> = prefs.downloadAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchCategoryId = MutableStateFlow<String?>(null)
    val searchCategoryId: StateFlow<String?> = _searchCategoryId.asStateFlow()

    private val _searchSortOption = MutableStateFlow(SearchSortOption.POPULARITY)
    val searchSortOption: StateFlow<SearchSortOption> = _searchSortOption.asStateFlow()

    private val _appsCategoryFilter = MutableStateFlow<String?>(null)
    val appsCategoryFilter: StateFlow<String?> = _appsCategoryFilter.asStateFlow()

    private val _gamesCategoryFilter = MutableStateFlow<String?>(null)
    val gamesCategoryFilter: StateFlow<String?> = _gamesCategoryFilter.asStateFlow()

    fun onAuthStateChanged(user: FirebaseUser?) {
        val nextUid = user?.uid
        val changed = _currentUserId.value != nextUid
        _currentUserId.value = nextUid
        if (user != null && changed) {
            viewModelScope.launch {
                repository.syncUserProfileOnLogin(user)
            }
        }
    }

    fun observeAppDetail(appId: String): Flow<ApplicationListing?> =
        repository.observeAppById(appId).catch { emit(null) }

    fun observeAppVersions(appId: String): Flow<List<AppVersion>> =
        repository.observePublishedVersionsForApp(appId).catch { emit(emptyList()) }

    fun observeAppReviews(appId: String): Flow<List<Review>> =
        repository.observeApprovedReviewsForApp(appId).catch { emit(emptyList()) }

    fun getInstallState(app: ApplicationListing): InstalledAppState =
        PackageInstallHelper.resolveAppInstallState(appContext, app.packageName, app.latestVersionCode)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchCategoryFilter(categoryId: String?) {
        _searchCategoryId.value = categoryId
    }

    fun setSearchSortOption(option: SearchSortOption) {
        _searchSortOption.value = option
    }

    fun setAppsCategoryFilter(categoryId: String?) {
        _appsCategoryFilter.value = categoryId
    }

    fun setGamesCategoryFilter(categoryId: String?) {
        _gamesCategoryFilter.value = categoryId
    }

    fun commitSearchTerm(term: String) {
        viewModelScope.launch { prefs.addSearchTerm(term) }
    }

    fun removeSearchTerm(term: String) {
        viewModelScope.launch { prefs.removeSearchTerm(term) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { prefs.clearSearchHistory() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setUpdateAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setUpdateAlertsEnabled(enabled) }
    }

    fun setDownloadAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDownloadAlertsEnabled(enabled)
            if (_currentUserId.value != null) {
                repository.updateNotificationsEnabled(enabled)
            }
        }
    }

    fun startOrResumeDownload(app: ApplicationListing) {
        val apkUrl = app.apkUrl.orEmpty()
        if (apkUrl.isBlank()) {
            _snackbarEvents.tryEmit("No downloadable APK URL is attached to ${app.appName}")
            return
        }
        downloadManager.startOrResumeDownload(
            appId = app.id,
            versionId = app.latestVersionId ?: "v_${app.latestVersionCode}",
            appName = app.appName,
            packageName = app.packageName,
            versionName = app.latestVersionName,
            versionCode = app.latestVersionCode,
            apkUrl = apkUrl,
            expectedSha256 = app.apkSha256,
            knownTotalBytes = app.apkSizeBytes
        ) { state ->
            viewModelScope.launch {
                repository.upsertDownloadRecord(
                    app = app,
                    status = state.status,
                    bytesDownloaded = state.bytesDownloaded,
                    totalBytes = state.totalBytes,
                    localFilePath = state.localFilePath,
                    errorMessage = state.errorMessage
                )
                if (state.status == DownloadStatus.COMPLETED) {
                    repository.incrementAppDownloadCount(app.id)
                    val uid = _currentUserId.value
                    if (uid != null && downloadAlertsEnabled.value) {
                        repository.createUserNotification(
                            targetUserId = uid,
                            title = "${app.appName} downloaded",
                            body = "Version ${app.latestVersionName} passed SHA-256 verification and is ready to install.",
                            type = "download_completed",
                            targetAppId = app.id
                        )
                    }
                    _snackbarEvents.tryEmit("${app.appName} ready to install")
                } else if (state.status == DownloadStatus.FAILED && state.errorMessage != null) {
                    _snackbarEvents.tryEmit("Download failed: ${state.errorMessage}")
                }
            }
        }
    }

    fun pauseDownload(app: ApplicationListing) {
        downloadManager.pauseDownload(app.id) { state ->
            viewModelScope.launch {
                repository.upsertDownloadRecord(
                    app = app,
                    status = state.status,
                    bytesDownloaded = state.bytesDownloaded,
                    totalBytes = state.totalBytes,
                    errorMessage = state.errorMessage
                )
            }
        }
    }

    fun cancelDownload(app: ApplicationListing) {
        downloadManager.cancelDownload(app.id, app.latestVersionCode) { state ->
            viewModelScope.launch {
                repository.upsertDownloadRecord(
                    app = app,
                    status = state.status,
                    bytesDownloaded = state.bytesDownloaded,
                    totalBytes = state.totalBytes
                )
            }
        }
    }

    fun installDownloadedApk(app: ApplicationListing) {
        val file = downloadManager.getDownloadedApkFile(app.id, app.latestVersionCode)
        if (file == null) {
            _snackbarEvents.tryEmit("Downloaded APK not found. Tap Download to fetch it.")
            return
        }
        val res = PackageInstallHelper.launchApkInstaller(appContext, file)
        res.onFailure { err ->
            _snackbarEvents.tryEmit(err.message ?: "Unable to launch package installer")
        }
    }

    fun openInstalledApp(packageName: String) {
        val launched = PackageInstallHelper.launchInstalledApp(appContext, packageName)
        if (!launched) {
            _snackbarEvents.tryEmit("Unable to launch $packageName")
        }
    }

    fun toggleFavorite(app: ApplicationListing, listType: String, currentlySaved: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(app, listType, currentlySaved)
                .onSuccess {
                    val label = if (listType == "wishlist") "Wishlist" else "Favorites"
                    _snackbarEvents.tryEmit(if (currentlySaved) "Removed from $label" else "Saved to $label")
                }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Sign in required") }
        }
    }

    fun submitReview(app: ApplicationListing, rating: Long, comment: String) {
        viewModelScope.launch {
            repository.submitOrUpdateReview(app, rating, comment)
                .onSuccess { _snackbarEvents.tryEmit("Review published") }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Unable to submit review") }
        }
    }

    fun deleteOwnReview(appId: String) {
        viewModelScope.launch {
            repository.deleteOwnReview(appId)
                .onSuccess { _snackbarEvents.tryEmit("Review deleted") }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Unable to delete review") }
        }
    }

    fun markReviewHelpful(reviewId: String) {
        viewModelScope.launch {
            repository.markReviewHelpful(reviewId)
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Sign in required") }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, reason, details)
                .onSuccess { _snackbarEvents.tryEmit("Report submitted for moderation review") }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Unable to submit report") }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    private companion object {
        const val TAG = "MarketplaceVM"
    }
}
