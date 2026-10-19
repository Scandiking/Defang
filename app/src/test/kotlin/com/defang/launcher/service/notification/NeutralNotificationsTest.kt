package com.defang.launcher.service.notification

import android.app.Notification
import android.app.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class NeutralNotificationsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `message category counts as a person message`() {
        val n = Notification.Builder(context, "c")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        assertTrue(DefangNotificationListenerService.isPersonMessage(n))
    }

    @Test
    fun `MessagingStyle counts as a person message`() {
        val me = Person.Builder().setName("me").build()
        val n = Notification.Builder(context, "c")
            .setStyle(Notification.MessagingStyle(me).addMessage("hi", 0L, me))
            .build()
        assertTrue(DefangNotificationListenerService.isPersonMessage(n))
    }

    @Test
    fun `plain promo notification is not a person message`() {
        val n = Notification.Builder(context, "c")
            .setContentTitle("Snapchat")
            .setContentText("You have a new Story to watch!")
            .build()
        assertFalse(DefangNotificationListenerService.isPersonMessage(n))
    }

    private fun at(hour: Int, minute: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 19, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis

    @Test
    fun `window later today fires today`() {
        assertEquals(at(12, 0), BatchWindowScheduler.nextTrigger(12 * 60, at(9, 30)))
    }

    @Test
    fun `window already passed fires tomorrow`() {
        assertEquals(at(12, 0, 1), BatchWindowScheduler.nextTrigger(12 * 60, at(18, 0)))
    }

    @Test
    fun `re-arming right after the window fired picks tomorrow`() {
        // Alarm delivered at 12:00 exactly, or a few minutes into its window
        assertEquals(at(12, 0, 1), BatchWindowScheduler.nextTrigger(12 * 60, at(12, 0)))
        assertEquals(at(12, 0, 1), BatchWindowScheduler.nextTrigger(12 * 60, at(12, 7)))
    }
}
