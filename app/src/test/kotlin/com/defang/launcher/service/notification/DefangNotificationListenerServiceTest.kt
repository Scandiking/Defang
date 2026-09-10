package com.defang.launcher.service.notification

import com.defang.launcher.data.local.db.entity.AppConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DefangNotificationListenerServiceTest {

    private val defaults = setOf("com.snapchat.android", "com.instagram.android")

    @Test
    fun `default package downgraded to Utility in DB is excluded`() {
        val all = listOf(
            AppConfigEntity(packageName = "com.snapchat.android", appLabel = "Snapchat", tier = 0),
        )

        val watched = DefangNotificationListenerService.computeWatchedPackages(all, defaults)

        assertEquals(setOf("com.instagram.android"), watched)
    }

    @Test
    fun `default package still watched when its DB row says so`() {
        val all = listOf(
            AppConfigEntity(packageName = "com.snapchat.android", appLabel = "Snapchat", tier = 1),
        )

        val watched = DefangNotificationListenerService.computeWatchedPackages(all, defaults)

        assertEquals(setOf("com.snapchat.android", "com.instagram.android"), watched)
    }

    @Test
    fun `default package with no DB row yet falls back to watched`() {
        val watched = DefangNotificationListenerService.computeWatchedPackages(emptyList(), defaults)

        assertEquals(defaults, watched)
    }

    @Test
    fun `non-default package watched only via explicit DB tier`() {
        val all = listOf(
            AppConfigEntity(packageName = "com.example.chat", appLabel = "Chat", tier = 1),
            AppConfigEntity(packageName = "com.example.calc", appLabel = "Calc", tier = 0),
        )

        val watched = DefangNotificationListenerService.computeWatchedPackages(all, defaults)

        assertEquals(defaults + "com.example.chat", watched)
    }
}
