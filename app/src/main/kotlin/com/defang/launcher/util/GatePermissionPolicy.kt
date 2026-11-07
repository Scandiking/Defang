package com.defang.launcher.util

import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.data.repository.AppConfigRepository
import com.defang.launcher.data.repository.WatchedUrlRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether Defang should ask for the gate permissions (accessibility,
 * draw over other apps, usage access).
 *
 * With no watched apps, no enabled watched sites and the built-in adult list
 * switched off (#90), Defang is a plain launcher and those permissions do nothing, so it doesn't ask (#89).
 * Un-watching an app is already a single tap, so skipping the prompts here
 * opens no new way around the gate.
 *
 * On first launch the app table isn't seeded yet, so this returns false; the
 * launcher resumes again after onboarding, by which point the default watch
 * list is in place.
 */
@Singleton
class GatePermissionPolicy @Inject constructor(
    private val appConfigRepo: AppConfigRepository,
    private val watchedUrlRepo: WatchedUrlRepository,
    private val prefs: PreferencesDataStore,
) {
    suspend fun needsGatePermissions(): Boolean =
        appConfigRepo.hasWatched() ||
            watchedUrlRepo.hasEnabled() ||
            prefs.builtinAdultListEnabled.first()
}
