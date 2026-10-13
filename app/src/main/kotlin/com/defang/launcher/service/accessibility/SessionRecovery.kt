package com.defang.launcher.service.accessibility

/** What [DefangAccessibilityService] should do with a session row found open on reconnect. */
sealed interface RecoveryAction {
    /** App still in front with time left — resume the HUD for [remainingMs]. */
    data class Resume(val remainingMs: Long) : RecoveryAction

    /** App still in front but the limit passed while the service was down. */
    data object ExpireNow : RecoveryAction

    /** App no longer in front — close the row at [endTime], no end card, no cooldown. */
    data class Close(val endTime: Long) : RecoveryAction
}

/**
 * Decides how to treat a session left open by process death (issue #20) or a
 * leaked row (issue #44). An open row alone says nothing about whether the
 * user is still in that app — trusting it showed a days-long session on the
 * end card after a reinstall — so the live foreground app decides.
 */
object SessionRecovery {

    fun decide(startTime: Long, limitMs: Long, now: Long, appInForeground: Boolean): RecoveryAction {
        if (!appInForeground) return RecoveryAction.Close(cappedEnd(startTime, limitMs, now))
        val remaining = limitMs - (now - startTime)
        return if (remaining > 0L) RecoveryAction.Resume(remaining) else RecoveryAction.ExpireNow
    }

    /** A stale row never counts for more than its limit — the HUD would have ended it there. */
    fun cappedEnd(startTime: Long, limitMs: Long, now: Long): Long = minOf(now, startTime + limitMs)
}
