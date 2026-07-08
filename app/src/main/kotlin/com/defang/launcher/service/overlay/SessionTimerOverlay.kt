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

    private fun startTimer() {
        countdownTimer = object : CountDownTimer(sessionLimitMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (::tvTimer.isInitialized) {
                    tvTimer.text = formatMillis(millisUntilFinished)
                    // Turn subtle red in last 60 seconds
                    if (millisUntilFinished < 60_000L) {
                        tvTimer.setTextColor(Color.rgb(176, 64, 64))
                    }
                }
            }

            override fun onFinish() {
                onSessionExpired()
            }
        }.start()
    }

    /** Call when the user uses the extension to add 10 more minutes. */
    fun addExtensionTime(extraMs: Long) {
        countdownTimer?.cancel()
        val remaining = extraMs
        countdownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (::tvTimer.isInitialized) {
                    tvTimer.text = formatMillis(millisUntilFinished)
                }
            }

            override fun onFinish() {
                onSessionExpired()
            }
        }.start()
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
