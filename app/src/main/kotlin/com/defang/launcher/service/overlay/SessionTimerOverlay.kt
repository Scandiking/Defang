package com.defang.launcher.service.overlay

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Minimal HUD drawn over the watched app during a session.
 * Shows time remaining in the top-right corner.
 * Tapping it has no effect — it is information only.
 */
class SessionTimerOverlay(
    private val context: Context,
    private val sessionLimitMs: Long,
    private val onSessionExpired: () -> Unit,
) {
    private var countdownTimer: CountDownTimer? = null
    private lateinit var tvTimer: TextView

    val view: View by lazy { buildView() }

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(180, 10, 10, 10))
            setPadding(16, 8, 16, 8)
        }

        tvTimer = TextView(context).apply {
            text = formatMillis(sessionLimitMs)
            textSize = 12f
            setTextColor(Color.rgb(176, 125, 74))
        }
        root.addView(tvTimer)

        startTimer()
        return root
    }

    private fun startTimer() = startCountdown(sessionLimitMs)

    // Time left on the HUD; kept current by onTick so pause() can freeze it.
    private var remainingMs: Long = sessionLimitMs

    private fun startCountdown(ms: Long) {
        countdownTimer?.cancel()
        remainingMs = ms
        countdownTimer = object : CountDownTimer(ms, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                if (::tvTimer.isInitialized) {
                    tvTimer.text = formatMillis(millisUntilFinished)
                    // Turn subtle red in last 60 seconds
                    if (millisUntilFinished < 60_000L) {
                        tvTimer.setTextColor(Color.rgb(176, 64, 64))
                    }
                }
            }

            override fun onFinish() {
                remainingMs = 0L
                onSessionExpired()
            }
        }.start()
    }

    /** Call when the user uses the extension to add 10 more minutes. */
    fun addExtensionTime(extraMs: Long) = startCountdown(extraMs)

    /** Freezes the countdown (screen off, Home): paused time isn't session time. */
    fun pause() {
        countdownTimer?.cancel()
        countdownTimer = null
    }

    /** Restarts the countdown from where [pause] froze it. */
    fun resume() {
        if (countdownTimer != null || remainingMs <= 0L) return
        startCountdown(remainingMs)
    }

    fun cancel() {
        countdownTimer?.cancel()
    }

    private fun formatMillis(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return context.getString(com.defang.launcher.R.string.time_format_mm_ss, minutes, seconds)
    }
}
