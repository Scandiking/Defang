package com.defang.launcher.domain.usecase

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Returns true if the system Do Not Disturb / Bedtime schedule is currently active.
 * Used by the accessibility service to escalate friction during the user's configured quiet hours.
 * If no DnD schedule is configured, this always returns false — no fallback default.
 */
class IsDnDActiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isActive(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }
}
