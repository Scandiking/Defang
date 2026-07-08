package com.defang.launcher.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "defang_prefs")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // ── Onboarding ──────────────────────────────────────────────────────────
    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    val isOnboardingDone: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() = store.edit { it[KEY_ONBOARDING_DONE] = true }

    // ── Daily extension ──────────────────────────────────────────────────────
    // We store the calendar date string (yyyy-MM-dd) on which the extension was used.
    private val KEY_EXTENSION_DATE = stringPreferencesKey("extension_date")

    val extensionUsedDate: Flow<String?> = store.data.map { it[KEY_EXTENSION_DATE] }

    suspend fun markExtensionUsed(date: String) = store.edit { it[KEY_EXTENSION_DATE] = date }

    // ── Last known foreground package ────────────────────────────────────────
    private val KEY_LAST_FG_PKG = stringPreferencesKey("last_fg_pkg")

    val lastForegroundPackage: Flow<String?> = store.data.map { it[KEY_LAST_FG_PKG] }

    suspend fun setLastForegroundPackage(pkg: String) = store.edit { it[KEY_LAST_FG_PKG] = pkg }
}
