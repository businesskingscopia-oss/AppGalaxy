package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.ApplicationListing
import com.example.data.model.Category
import com.example.data.model.SearchSortOption
import com.example.data.model.UiState
import com.example.ui.components.AppListRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    appsState: UiState<List<ApplicationListing>>,
    categories: List<Category>,
    query: String,
    selectedCategoryId: String?,
    sortOption: SearchSortOption,
    recentSearches: List<String>,
    onQueryChange: (String) -> Unit,
    onCommitSearch: (String) -> Unit,
    onRemoveSearchTerm: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectSort: (SearchSortOption) -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var visibleCount by remember(query, selectedCategoryId, sortOption) { mutableIntStateOf(20) }

    val allApps = remember(appsState) {
        (appsState as? UiState.Success)?.data.orEmpty()
    }

    val popularQueries = remember(allApps, categories) {
        val fromApps = allApps.sortedByDescending { it.downloadCount }.take(4).map { it.appName }
        val fromCats = categories.take(4).map { it.name }
        (fromApps + fromCats).distinct().take(6)
    }

    val suggestions = remember(query, allApps) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else allApps.filter {
            it.appName.lowercase().contains(q) ||
                it.developerName.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q) ||
                it.categoryName.lowercase().contains(q)
        }.take(5).map { it.appName }.distinct()
    }

    val filteredResults = remember(allApps, query, selectedCategoryId, sortOption) {
        val q = query.trim().lowercase()
        val matched = allApps.filter { app ->
            val matchesCat = selectedCategoryId == null || app.categoryId == selectedCategoryId
            val matchesText = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.developerName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.categoryName.lowercase().contains(q) ||
                app.shortDescription.lowercase().contains(q)
            matchesCat && matchesText
        }
        when (sortOption) {
            SearchSortOption.POPULARITY -> matched.sortedByDescending { it.downloadCount }
            SearchSortOption.RATING -> matched.sortedByDescending { it.averageRating }
            SearchSortOption.NEWEST -> matched.sortedByDescending { it.publishedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            SearchSortOption.ALPHABETICAL -> matched.sortedBy { it.appName.lowercase() }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search apps, developers, package names, categories…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onCommitSearch(query)
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }

        // Instant Suggestions while typing
        if (suggestions.isNotEmpty() && query.isNotBlank()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { suggestion ->
                        AssistChip(
                            onClick = {
                                onQueryChange(suggestion)
                                onCommitSearch(suggestion)
                                focusManager.clearFocus()
                            },
                            label = { Text(suggestion) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }

        // Category Filter + Sort Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredResults.size} result${if (filteredResults.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier.testTag("search_sort_button")
                    ) {
                        Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Sort: ${sortOption.label}")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SearchSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSelectSort(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All Categories") }
                        )
                    }
                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }
        }

        // Recent Searches & Popular Searches when query is empty
        if (query.isBlank()) {
            if (recentSearches.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Searches", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onClearHistory) { Text("Clear all") }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentSearches.forEach { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onQueryChange(term)
                                        onCommitSearch(term)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(term, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(
                                    onClick = { onRemoveSearchTerm(term) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Remove search term", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (popularQueries.isNotEmpty()) {
                item {
                    Text("Popular Searches", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        popularQueries.forEach { tag ->
                            AssistChip(
                                onClick = {
                                    onQueryChange(tag)
                                    onCommitSearch(tag)
                                },
                                label = { Text(tag) },
                                leadingIcon = {
                                    Icon(Icons.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filteredResults.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) "No published applications match the selected filter."
                        else "No real published applications match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val paged = filteredResults.take(visibleCount)
            items(paged, key = { it.id }) { app ->
                AppListRow(
                    app = app,
                    onClick = {
                        if (query.isNotBlank()) onCommitSearch(query)
                        onAppClick(app.id)
                    }
                )
            }
            if (filteredResults.size > visibleCount) {
                item {
                    OutlinedButton(
                        onClick = { visibleCount += 20 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load More (${filteredResults.size - visibleCount} remaining)")
                    }
                }
            }
        }
    }
}
