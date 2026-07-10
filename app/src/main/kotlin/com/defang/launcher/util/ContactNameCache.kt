package com.defang.launcher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory set of address-book display names, used for the notification
 * contact bypass: notifications whose title matches a contact are never
 * intercepted (PRD: "calls and notifications from contacts are never batched").
 *
 * Best-effort by design: matching on the notification title catches the
 * common case (messaging apps title DMs with the sender name). Without the
 * READ_CONTACTS grant the cache stays empty and everything is intercepted.
 */
@Singleton
class ContactNameCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var names: Set<String> = emptySet()
    @Volatile private var loadedAtMs = 0L
    private val loading = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isContact(title: CharSequence?): Boolean {
        val name = title?.toString()?.trim()?.lowercase() ?: return false
        if (name.isEmpty()) return false
        refreshIfStale()
        return name in names
    }

    /** Kick off a background refresh if the cache is older than 6 hours. */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - loadedAtMs < REFRESH_INTERVAL_MS) return
        if (!loading.compareAndSet(false, true)) return
        scope.launch {
            try {
                names = load()
                loadedAtMs = System.currentTimeMillis()
            } finally {
                loading.set(false)
            }
        }
    }

    private fun load(): Set<String> {
        val granted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return emptySet()

        val result = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.trim()?.lowercase()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { result.add(it) }
                }
            }
        } catch (_: SecurityException) {
            return emptySet()
        }
        return result
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 6 * 60 * 60_000L
    }
}
