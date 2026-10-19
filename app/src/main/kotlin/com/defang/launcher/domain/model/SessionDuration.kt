package com.defang.launcher.domain.model

import com.defang.launcher.data.local.db.entity.SessionEntity

/**
 * How much of a session row counts as use (issue #60). Rows written before
 * session pausing existed could stay open for hours behind a locked screen,
 * Home or a backgrounded Snapchat, so every reader caps a row at what the
 * HUD could have allowed instead of trusting endTime - startTime.
 */
object SessionDuration {
    /** Length of the one daily "more time" extension. */
    const val EXTENSION_MS = 10 * 60_000L

    /** Open rows older than this are unclosed garbage, not a live session. */
    const val STALE_OPEN_SESSION_MS = 24 * 60 * 60_000L

    /** Room for HUD/debounce lag past the limit. */
    private const val CAP_SLACK_MS = 60_000L

    /** Most a row with this session limit can count: the limit, or an
     *  extension if longer, plus slack. */
    fun capMs(limitMinutes: Int): Long =
        maxOf(limitMinutes * 60_000L, EXTENSION_MS) + CAP_SLACK_MS

    /**
     * Counted foreground time of this row: closed rows end at endTime, open
     * rows run to [now] (or count 0 when stale), minus paused time, clamped
     * to [0, capMs].
     */
    fun countedMs(row: SessionEntity, now: Long, capMs: Long): Long {
        val end = when {
            row.endTime > 0L -> row.endTime
            now - row.startTime < STALE_OPEN_SESSION_MS -> now
            else -> return 0L
        }
        return (end - row.startTime - row.pausedMs).coerceIn(0L, capMs)
    }
}
