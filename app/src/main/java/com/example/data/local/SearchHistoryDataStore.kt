package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appGalaxyPrefs: DataStore<Preferences> by preferencesDataStore(name = "appgalaxy_prefs")

class SearchHistoryDataStore(private val context: Context) {

    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val updateAlertsKey = booleanPreferencesKey("update_alerts_enabled")
    private val downloadAlertsKey = booleanPreferencesKey("download_alerts_enabled")

    val recentSearches: Flow<List<String>> = context.appGalaxyPrefs.data.map { prefs ->
        val raw = prefs[recentSearchesKey].orEmpty()
        if (raw.isBlank()) emptyList()
        else raw.split("\n").filter { it.isNotBlank() }
    }

    val themeMode: Flow<ThemeMode> = context.appGalaxyPrefs.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeModeKey])
    }

    val updateAlertsEnabled: Flow<Boolean> = context.appGalaxyPrefs.data.map { prefs ->
        prefs[updateAlertsKey] ?: true
    }

    val downloadAlertsEnabled: Flow<Boolean> = context.appGalaxyPrefs.data.map { prefs ->
        prefs[downloadAlertsKey] ?: true
    }

    suspend fun addSearchTerm(term: String) {
        val clean = term.trim()
        if (clean.isBlank()) return
        context.appGalaxyPrefs.edit { prefs ->
            val current = prefs[recentSearchesKey]?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) })
                .take(10)
            prefs[recentSearchesKey] = updated.joinToString("\n")
        }
    }

    suspend fun removeSearchTerm(term: String) {
        context.appGalaxyPrefs.edit { prefs ->
            val current = prefs[recentSearchesKey]?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
            prefs[recentSearchesKey] = current.filterNot { it == term }.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        context.appGalaxyPrefs.edit { prefs ->
            prefs.remove(recentSearchesKey)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appGalaxyPrefs.edit { prefs ->
            prefs[themeModeKey] = mode.key
        }
    }

    suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
        context.appGalaxyPrefs.edit { prefs ->
            prefs[updateAlertsKey] = enabled
        }
    }

    suspend fun setDownloadAlertsEnabled(enabled: Boolean) {
        context.appGalaxyPrefs.edit { prefs ->
            prefs[downloadAlertsKey] = enabled
        }
    }
}
