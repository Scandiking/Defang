package com.defang.launcher.service.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Phase 2: notification interception and batching.
 * Stub for now — declared in manifest so the permission can be requested during onboarding.
 */
class DefangNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Phase 2: check if sbn.packageName is a watched app,
        // cancel the notification, store it, re-post at the delivery window.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Phase 2: clean up stored notifications.
    }
}
