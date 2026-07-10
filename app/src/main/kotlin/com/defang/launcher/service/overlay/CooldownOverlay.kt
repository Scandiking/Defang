package com.defang.launcher.service.overlay

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.defang.launcher.R

/**
 * Full-screen lockout shown when a watched app is opened during its cool-down.
 *
 * Shows a live countdown until the app is available again, plus a tidbit.
 * No bypass button by design — the only exits are "back to home" or waiting
 * out the countdown (in which case the caller re-runs the normal gate flow).
 */
class CooldownOverlay(
    private val context: Context,
    private val appLabel: String,
    private val cooldownEndsAt: Long,
    private val tidbit: String,
    private val onGoHome: () -> Unit,
    private val onExpired: () -> Unit,
) {
    private var tvCountdown: TextView? = null
    private var countdownTimer: CountDownTimer? = null

    val view: View by lazy { buildView() }

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(250, 10, 10, 10))
            setPadding(64, 128, 64, 96)
        }

        val tvHeading = TextView(context).apply {
            text = context.getString(R.string.cooldown_heading)
            textSize = 22f
            setTextColor(Color.rgb(224, 224, 224))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(tvHeading)

        tvCountdown = TextView(context).apply {
            text = countdownText()
            textSize = 15f
            setTextColor(Color.rgb(160, 160, 160))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        root.addView(tvCountdown)

        val tvTidbit = TextView(context).apply {
            text = tidbit
            textSize = 14f
            setTextColor(Color.rgb(160, 160, 160))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        root.addView(tvTidbit)

        val btnGoHome = Button(context).apply {
            text = context.getString(R.string.cooldown_go_home)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { onGoHome() }
        }
        root.addView(btnGoHome)

        startCountdown()
        return root
    }

    private fun countdownText(): String {
        val remainingMs = (cooldownEndsAt - System.currentTimeMillis()).coerceAtLeast(0)
        val minutes = remainingMs / 60_000
        val seconds = (remainingMs % 60_000) / 1000
        val timeStr = context.getString(R.string.time_format_mm_ss, minutes, seconds)
        return context.getString(R.string.cooldown_available_in, appLabel, timeStr)
    }

    private fun startCountdown() {
        val remainingMs = (cooldownEndsAt - System.currentTimeMillis()).coerceAtLeast(0)
        countdownTimer = object : CountDownTimer(remainingMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                tvCountdown?.text = countdownText()
            }

            override fun onFinish() {
                onExpired()
            }
        }.start()
    }

    fun cancel() {
        countdownTimer?.cancel()
    }
}
