package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.apk.ApkDownloadManager
import com.example.data.apk.ApkStorageUploader
import com.example.data.auth.AuthManager
import com.example.data.auth.authStateFlow
import com.example.data.local.SearchHistoryDataStore
import com.example.data.model.ThemeMode
import com.example.data.repository.DeveloperAdminRepository
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.AppGalaxyTopBar
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ApkUploadScreen
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.AppsHomeScreen
import com.example.ui.screens.DeveloperPortalScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.GamesHomeScreen
import com.example.ui.screens.LegalPoliciesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.AppGalaxyTheme
import com.example.ui.viewmodel.DeveloperAdminViewModel
import com.example.ui.viewmodel.MarketplaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object AppGalaxyRoutes {
    const val GAMES = "games"
    const val APPS = "apps"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"
    const val APP_DETAIL = "app_detail/{appId}"
    const val DEVELOPER_PORTAL = "developer_portal"
    const val APK_UPLOAD = "apk_upload"
    const val APK_UPLOAD_UPDATE = "apk_upload/{appId}"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val NOTIFICATIONS = "notifications"
    const val LEGAL = "legal"

    fun appDetail(appId: String): String = "app_detail/$appId"
    fun apkUploadUpdate(appId: String): String = "apk_upload/$appId"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val MAIN_TABS = listOf(
    BottomNavTab(
        route = AppGalaxyRoutes.GAMES,
        label = "Games",
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports,
        testTag = "nav_tab_games"
    ),
    BottomNavTab(
        route = AppGalaxyRoutes.APPS,
        label = "Apps",
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        testTag = "nav_tab_apps"
    ),
    BottomNavTab(
        route = AppGalaxyRoutes.SEARCH,
        label = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_tab_search"
    ),
    BottomNavTab(
        route = AppGalaxyRoutes.DOWNLOADS,
        label = "Downloads",
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_tab_downloads"
    ),
    BottomNavTab(
        route = AppGalaxyRoutes.PROFILE,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_profile"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppGalaxyRoot()
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
fun AppGalaxyRoot() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val marketplaceVm: MarketplaceViewModel = viewModel(
        factory = remember(appContext) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = FirebaseFirestore.getInstance(
                        appContext.getString(R.string.firestore_database_id)
                    )
                    return MarketplaceViewModel(
                        appContext = appContext,
                        repository = MarketplaceRepository(db),
                        downloadManager = ApkDownloadManager(appContext),
                        prefs = SearchHistoryDataStore(appContext)
                    ) as T
                }
            }
        }
    )

    val devAdminVm: DeveloperAdminViewModel = viewModel(
        factory = remember(appContext) {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = FirebaseFirestore.getInstance(
                        appContext.getString(R.string.firestore_database_id)
                    )
                    return DeveloperAdminViewModel(
                        appContext = appContext,
                        repository = DeveloperAdminRepository(db),
                        storageUploader = ApkStorageUploader(appContext)
                    ) as T
                }
            }
        }
    )

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    // Attempt silent Credential Manager sign-in on cold start if previously authorized
    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = { user -> marketplaceVm.onAuthStateChanged(user) },
            onUnauthenticated = { marketplaceVm.onAuthStateChanged(null) }
        )
    }

    LaunchedEffect(currentUser) {
        marketplaceVm.onAuthStateChanged(currentUser)
    }

    val isAdmin by marketplaceVm.isAdmin.collectAsStateWithLifecycle()
    LaunchedEffect(currentUser?.uid, isAdmin) {
        devAdminVm.syncIdentity(currentUser?.uid, isAdmin)
    }

    val themeMode by marketplaceVm.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        launch {
            marketplaceVm.snackbarEvents.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            devAdminVm.events.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val appsState by marketplaceVm.publishedAppsState.collectAsStateWithLifecycle()
    val categories by marketplaceVm.categoriesState.collectAsStateWithLifecycle()
    val adminSettings by marketplaceVm.adminSettings.collectAsStateWithLifecycle()
    val userProfile by marketplaceVm.userProfile.collectAsStateWithLifecycle()
    val downloadHistory by marketplaceVm.downloadHistory.collectAsStateWithLifecycle()
    val activeDownloads by marketplaceVm.activeDownloads.collectAsStateWithLifecycle()
    val favorites by marketplaceVm.favorites.collectAsStateWithLifecycle()
    val notifications by marketplaceVm.notifications.collectAsStateWithLifecycle()
    val userReviews by marketplaceVm.userReviews.collectAsStateWithLifecycle()
    val recentSearches by marketplaceVm.recentSearches.collectAsStateWithLifecycle()
    val searchQuery by marketplaceVm.searchQuery.collectAsStateWithLifecycle()
    val searchCategoryId by marketplaceVm.searchCategoryId.collectAsStateWithLifecycle()
    val searchSortOption by marketplaceVm.searchSortOption.collectAsStateWithLifecycle()
    val appsCategoryFilter by marketplaceVm.appsCategoryFilter.collectAsStateWithLifecycle()
    val gamesCategoryFilter by marketplaceVm.gamesCategoryFilter.collectAsStateWithLifecycle()
    val updateAlertsEnabled by marketplaceVm.updateAlertsEnabled.collectAsStateWithLifecycle()
    val downloadAlertsEnabled by marketplaceVm.downloadAlertsEnabled.collectAsStateWithLifecycle()
    val packageTick by marketplaceVm.packageChangeSignal.collectAsStateWithLifecycle()

    val unreadNotificationsCount = remember(notifications) {
        notifications.count { !it.read }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevelRoutes = remember { MAIN_TABS.map { it.route }.toSet() }
    val showMainChrome = currentRoute in topLevelRoutes

    val navigateToTopTab: (String) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    AppGalaxyTheme(darkTheme = useDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (showMainChrome && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        MAIN_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        if (showMainChrome) {
                            Box(modifier = Modifier.statusBarsPadding()) {
                                AppGalaxyTopBar(
                                    currentUser = currentUser,
                                    unreadNotificationsCount = unreadNotificationsCount,
                                    onSearchClick = { navigateToTopTab(AppGalaxyRoutes.SEARCH) },
                                    onNotificationsClick = { navController.navigate(AppGalaxyRoutes.NOTIFICATIONS) },
                                    onProfileClick = { navigateToTopTab(AppGalaxyRoutes.PROFILE) }
                                )
                            }
                        }
                    },
                    bottomBar = {
                        if (showMainChrome && !useNavigationRail) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                MAIN_TABS.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTopTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = AppGalaxyRoutes.APPS,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(AppGalaxyRoutes.GAMES) {
                                GamesHomeScreen(
                                    appsState = appsState,
                                    categories = categories,
                                    selectedCategoryId = gamesCategoryFilter,
                                    adminSettings = adminSettings,
                                    onSelectCategory = marketplaceVm::setGamesCategoryFilter,
                                    onAppClick = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    },
                                    onOpenDeveloperPortal = {
                                        navController.navigate(AppGalaxyRoutes.DEVELOPER_PORTAL)
                                    },
                                    onOpenSearch = { navigateToTopTab(AppGalaxyRoutes.SEARCH) }
                                )
                            }

                            composable(AppGalaxyRoutes.APPS) {
                                AppsHomeScreen(
                                    appsState = appsState,
                                    categories = categories,
                                    selectedCategoryId = appsCategoryFilter,
                                    adminSettings = adminSettings,
                                    onSelectCategory = marketplaceVm::setAppsCategoryFilter,
                                    onAppClick = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    },
                                    onOpenDeveloperPortal = {
                                        navController.navigate(AppGalaxyRoutes.DEVELOPER_PORTAL)
                                    },
                                    onOpenSearch = { navigateToTopTab(AppGalaxyRoutes.SEARCH) }
                                )
                            }

                            composable(AppGalaxyRoutes.SEARCH) {
                                SearchScreen(
                                    appsState = appsState,
                                    categories = categories,
                                    query = searchQuery,
                                    selectedCategoryId = searchCategoryId,
                                    sortOption = searchSortOption,
                                    recentSearches = recentSearches,
                                    onQueryChange = marketplaceVm::setSearchQuery,
                                    onCommitSearch = marketplaceVm::commitSearchTerm,
                                    onRemoveSearchTerm = marketplaceVm::removeSearchTerm,
                                    onClearHistory = marketplaceVm::clearSearchHistory,
                                    onSelectCategory = marketplaceVm::setSearchCategoryFilter,
                                    onSelectSort = marketplaceVm::setSearchSortOption,
                                    onAppClick = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    }
                                )
                            }

                            composable(AppGalaxyRoutes.DOWNLOADS) {
                                DownloadsScreen(
                                    currentUser = currentUser,
                                    appsState = appsState,
                                    activeDownloads = activeDownloads,
                                    downloadHistory = downloadHistory,
                                    packageTick = packageTick,
                                    onStartOrResume = marketplaceVm::startOrResumeDownload,
                                    onPause = marketplaceVm::pauseDownload,
                                    onCancel = marketplaceVm::cancelDownload,
                                    onInstallApk = marketplaceVm::installDownloadedApk,
                                    onAppClick = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    },
                                    onAuthSuccess = { user ->
                                        marketplaceVm.onAuthStateChanged(user)
                                    },
                                    onAuthError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(AppGalaxyRoutes.PROFILE) {
                                ProfileScreen(
                                    currentUser = currentUser,
                                    userProfile = userProfile,
                                    isAdmin = isAdmin,
                                    appsState = appsState,
                                    favorites = favorites,
                                    downloadHistory = downloadHistory,
                                    userReviews = userReviews,
                                    themeMode = themeMode,
                                    updateAlertsEnabled = updateAlertsEnabled,
                                    downloadAlertsEnabled = downloadAlertsEnabled,
                                    packageTick = packageTick,
                                    onAuthSuccess = { user ->
                                        marketplaceVm.onAuthStateChanged(user)
                                    },
                                    onAuthError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    },
                                    onThemeModeChange = marketplaceVm::setThemeMode,
                                    onUpdateAlertsChange = marketplaceVm::setUpdateAlertsEnabled,
                                    onDownloadAlertsChange = marketplaceVm::setDownloadAlertsEnabled,
                                    onOpenDeveloperPortal = {
                                        navController.navigate(AppGalaxyRoutes.DEVELOPER_PORTAL)
                                    },
                                    onOpenAdminDashboard = {
                                        navController.navigate(AppGalaxyRoutes.ADMIN_DASHBOARD)
                                    },
                                    onOpenNotifications = {
                                        navController.navigate(AppGalaxyRoutes.NOTIFICATIONS)
                                    },
                                    onOpenLegal = {
                                        navController.navigate(AppGalaxyRoutes.LEGAL)
                                    },
                                    onAppClick = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    }
                                )
                            }

                            composable(
                                route = AppGalaxyRoutes.APP_DETAIL,
                                arguments = listOf(navArgument("appId") { type = NavType.StringType })
                            ) { entry ->
                                val appId = entry.arguments?.getString("appId").orEmpty()
                                AppDetailScreen(
                                    appId = appId,
                                    viewModel = marketplaceVm,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToApp = { nextId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(nextId))
                                    },
                                    onOpenSignIn = {
                                        navController.navigate(AppGalaxyRoutes.PROFILE)
                                    }
                                )
                            }

                            composable(AppGalaxyRoutes.DEVELOPER_PORTAL) {
                                DeveloperPortalScreen(
                                    currentUser = currentUser,
                                    isAdmin = isAdmin,
                                    viewModel = devAdminVm,
                                    onBack = { navController.popBackStack() },
                                    onUploadNewApp = {
                                        navController.navigate(AppGalaxyRoutes.APK_UPLOAD)
                                    },
                                    onUploadUpdateForApp = { appId ->
                                        navController.navigate(AppGalaxyRoutes.apkUploadUpdate(appId))
                                    },
                                    onAuthSuccess = { user ->
                                        marketplaceVm.onAuthStateChanged(user)
                                    },
                                    onAuthError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(AppGalaxyRoutes.APK_UPLOAD) {
                                ApkUploadScreen(
                                    existingAppId = null,
                                    currentUser = currentUser,
                                    categories = categories,
                                    adminSettings = adminSettings,
                                    viewModel = devAdminVm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = AppGalaxyRoutes.APK_UPLOAD_UPDATE,
                                arguments = listOf(navArgument("appId") { type = NavType.StringType })
                            ) { entry ->
                                val appId = entry.arguments?.getString("appId").orEmpty()
                                ApkUploadScreen(
                                    existingAppId = appId,
                                    currentUser = currentUser,
                                    categories = categories,
                                    adminSettings = adminSettings,
                                    viewModel = devAdminVm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppGalaxyRoutes.ADMIN_DASHBOARD) {
                                AdminDashboardScreen(
                                    isAdmin = isAdmin,
                                    categories = categories,
                                    adminSettings = adminSettings,
                                    viewModel = devAdminVm,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppGalaxyRoutes.NOTIFICATIONS) {
                                NotificationsScreen(
                                    currentUser = currentUser,
                                    notifications = notifications,
                                    onBack = { navController.popBackStack() },
                                    onMarkRead = marketplaceVm::markNotificationRead,
                                    onOpenApp = { appId ->
                                        navController.navigate(AppGalaxyRoutes.appDetail(appId))
                                    },
                                    onAuthSuccess = { user ->
                                        marketplaceVm.onAuthStateChanged(user)
                                    },
                                    onAuthError = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }

                            composable(AppGalaxyRoutes.LEGAL) {
                                LegalPoliciesScreen(
                                    currentUser = currentUser,
                                    onBack = { navController.popBackStack() },
                                    onSubmitContactOrReport = { targetId, details ->
                                        marketplaceVm.submitReport(
                                            targetType = "legal_support",
                                            targetId = targetId,
                                            reason = "Legal / Support / DMCA Inquiry",
                                            details = details
                                        )
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
