package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AdminSettings
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.UiState
import com.example.ui.components.AppCardHorizontal
import com.example.ui.components.AppListRow
import com.example.ui.theme.GalaxyBlue
import com.example.ui.theme.GalaxyBlueDark

@Composable
fun AppsHomeScreen(
    appsState: UiState<List<ApplicationListing>>,
    categories: List<Category>,
    selectedCategoryId: String?,
    adminSettings: AdminSettings,
    onSelectCategory: (String?) -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDeveloperPortal: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    MarketplaceFeedScreen(
        isGamesTab = false,
        appsState = appsState,
        categories = categories.filter { !it.gameCategory },
        selectedCategoryId = selectedCategoryId,
        adminSettings = adminSettings,
        onSelectCategory = onSelectCategory,
        onAppClick = onAppClick,
        onOpenDeveloperPortal = onOpenDeveloperPortal,
        onOpenSearch = onOpenSearch,
        modifier = modifier
    )
}

@Composable
fun GamesHomeScreen(
    appsState: UiState<List<ApplicationListing>>,
    categories: List<Category>,
    selectedCategoryId: String?,
    adminSettings: AdminSettings,
    onSelectCategory: (String?) -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDeveloperPortal: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    MarketplaceFeedScreen(
        isGamesTab = true,
        appsState = appsState,
        categories = categories.filter { it.gameCategory },
        selectedCategoryId = selectedCategoryId,
        adminSettings = adminSettings,
        onSelectCategory = onSelectCategory,
        onAppClick = onAppClick,
        onOpenDeveloperPortal = onOpenDeveloperPortal,
        onOpenSearch = onOpenSearch,
        modifier = modifier
    )
}

@Composable
private fun MarketplaceFeedScreen(
    isGamesTab: Boolean,
    appsState: UiState<List<ApplicationListing>>,
    categories: List<Category>,
    selectedCategoryId: String?,
    adminSettings: AdminSettings,
    onSelectCategory: (String?) -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDeveloperPortal: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (appsState) {
        UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (isGamesTab) "Loading games catalog…" else "Loading AppGalaxy marketplace…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Unable to load catalog", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(appsState.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        is UiState.Success -> {
            val allPublished = appsState.data
            val scoped = remember(allPublished, isGamesTab, selectedCategoryId) {
                allPublished
                    .filter { if (isGamesTab) it.gameApp else !it.gameApp }
                    .filter { selectedCategoryId == null || it.categoryId == selectedCategoryId }
            }

            val featured = remember(scoped) { scoped.filter { it.featured } }
            val recommended = remember(scoped) {
                scoped.filter { it.recommended }.ifEmpty { scoped.sortedByDescending { it.averageRating }.take(8) }
            }
            val editorsPicks = remember(scoped) { scoped.filter { it.editorsPick } }
            val popular = remember(scoped) { scoped.sortedByDescending { it.downloadCount }.take(10) }
            val trending = remember(scoped) {
                scoped.sortedByDescending { (it.downloadCount * 0.6) + (it.totalRatings * 2.0) }.take(10)
            }
            val newReleases = remember(scoped) {
                scoped.sortedByDescending { it.publishedAt?.seconds ?: it.createdAt?.seconds ?: 0L }.take(10)
            }
            val recentlyUpdated = remember(scoped) {
                scoped.sortedByDescending { it.updatedAt?.seconds ?: 0L }.take(10)
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Hero Banner
                item {
                    HeroBannerCard(
                        isGamesTab = isGamesTab,
                        totalAppsCount = scoped.size,
                        onExploreClick = onOpenSearch,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Admin Announcement Banner (if active)
                if (adminSettings.announcementActive && adminSettings.announcementTitle.isNotBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Campaign,
                                    contentDescription = "Announcement",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Column {
                                    Text(
                                        text = adminSettings.announcementTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (adminSettings.announcementBody.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = adminSettings.announcementBody,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Category Filter Chips
                if (categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryId == null,
                                    onClick = { onSelectCategory(null) },
                                    label = { Text(if (isGamesTab) "All Games" else "All Apps") },
                                    modifier = Modifier.testTag("cat_chip_all")
                                )
                            }
                            items(categories, key = { it.id }) { cat ->
                                FilterChip(
                                    selected = selectedCategoryId == cat.id,
                                    onClick = { onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id) },
                                    label = { Text(cat.name) },
                                    modifier = Modifier.testTag("cat_chip_${cat.id}")
                                )
                            }
                        }
                    }
                }

                if (scoped.isEmpty()) {
                    item {
                        EmptyCatalogCard(
                            isGamesTab = isGamesTab,
                            hasCategoryFilter = selectedCategoryId != null,
                            onClearFilter = { onSelectCategory(null) },
                            onOpenDeveloperPortal = onOpenDeveloperPortal,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    if (featured.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = if (isGamesTab) "Featured Games" else "Featured Applications",
                                subtitle = "Verified highlights curated for AppGalaxy",
                                apps = featured,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (recommended.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = if (isGamesTab) "Recommended Games" else "Recommended for You",
                                subtitle = "Top rated by the community",
                                apps = recommended,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (editorsPicks.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = "Editor's Picks",
                                subtitle = "Selected for quality & craft",
                                apps = editorsPicks,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (popular.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = if (isGamesTab) "Popular Games" else "Popular Applications",
                                subtitle = "Most downloaded releases",
                                apps = popular,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (trending.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = "Trending Now",
                                subtitle = "Rising downloads and reviews",
                                apps = trending,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (newReleases.isNotEmpty()) {
                        item {
                            HorizontalAppSection(
                                title = "New Releases",
                                subtitle = "Freshly approved on AppGalaxy",
                                apps = newReleases,
                                onAppClick = onAppClick
                            )
                        }
                    }

                    if (recentlyUpdated.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isGamesTab) "Top Free & Recently Updated Games" else "Recently Updated & Most Downloaded",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                recentlyUpdated.forEach { app ->
                                    AppListRow(app = app, onClick = { onAppClick(app.id) })
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
private fun HeroBannerCard(
    isGamesTab: Boolean,
    totalAppsCount: Int,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(172.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(
                    id = if (isGamesTab) R.drawable.img_games_spotlight else R.drawable.img_hero_discover
                ),
                contentDescription = if (isGamesTab) "Games spotlight banner" else "AppGalaxy discovery banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xCC0F172A),
                                Color(0x771E3A8A),
                                Color(0x22000000)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isGamesTab) Icons.Filled.SportsEsports else Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isGamesTab) "APPGALAXY ARCADE" else "EXPLORE. DOWNLOAD. DISCOVER.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBFDBFE),
                        fontWeight = FontWeight.Bold
                    )
                }

                Column {
                    Text(
                        text = if (isGamesTab) "Android Games Spotlight" else "Verified Android Marketplace",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalAppsCount verified release${if (totalAppsCount == 1) "" else "s"} ready to install",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0)
                    )
                }

                Surface(
                    onClick = onExploreClick,
                    shape = RoundedCornerShape(999.dp),
                    color = GalaxyBlue,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Browse Catalog",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalAppSection(
    title: String,
    subtitle: String,
    apps: List<ApplicationListing>,
    onAppClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                AppCardHorizontal(app = app, onClick = { onAppClick(app.id) })
            }
        }
    }
}

@Composable
private fun EmptyCatalogCard(
    isGamesTab: Boolean,
    hasCategoryFilter: Boolean,
    onClearFilter: () -> Unit,
    onOpenDeveloperPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isGamesTab) Icons.Filled.SportsEsports else Icons.Filled.DeveloperMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = if (hasCategoryFilter) "No published listings in this category"
                else if (isGamesTab) "No published games yet"
                else "No published applications yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "AppGalaxy displays only real, verified applications published through the Developer Portal and approved by an administrator.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasCategoryFilter) {
                    OutlinedButton(onClick = onClearFilter) {
                        Text("Clear Filter")
                    }
                }
                Button(
                    onClick = onOpenDeveloperPortal,
                    modifier = Modifier.testTag("empty_open_dev_portal_button")
                ) {
                    Text("Open Developer Portal")
                }
            }
        }
    }
}
