package com.defang.launcher.service.overlay

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.defang.launcher.domain.model.AppConfig
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.util.TidbitSelector

/**
 * Full-screen interstitial that appears before a watched app opens.
 *
 * Layout (top to bottom):
 *   1. Tidbit — the awareness fact, shown large and prominent immediately.
 *   2. Countdown label — small, dim, counts down the gate delay.
 *   3. Button row — "Go back" | "Open anyway", equal weight.
 *      "Open anyway" is disabled (alpha 0.3) until the countdown finishes.
 *
 * This is a View-based overlay (not Compose) because it runs inside the
 * AccessibilityService process — there is no Activity lifecycle to host Compose.
 */
class IntentGateOverlay(
    private val context: Context,
    private val config: AppConfig,
    private val contentTrack: ContentTrack,
    private val tidbitSelector: TidbitSelector,
    private val onIntentDeclared: (intent: String?) -> Unit,
    private val onGoBack: () -> Unit,
) {
    private var countdownTimer: CountDownTimer? = null
    val view: View by lazy { buildView() }

    private lateinit var tvCountdown: TextView
    private lateinit var btnOpenAnyway: Button

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 10, 10, 10))
            setPadding(64, 96, 64, 96)
        }

        // Tidbit — first thing the user sees
        val tvTidbit = TextView(context).apply {
            text = tidbitSelector.next(contentTrack)
            textSize = 20f
            setTextColor(Color.rgb(210, 210, 210))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
            setLineSpacing(0f, 1.4f)
        }
        root.addView(tvTidbit)

        // Countdown — small, unobtrusive
        tvCountdown = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(80, 80, 80))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvCountdown)

        // Button row — equal weight, no visual hierarchy between the two choices
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val btnBack = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_go_back)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = 8 }
            setOnClickListener { onGoBack() }
        }
        buttonRow.addView(btnBack)

        btnOpenAnyway = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_open_anyway)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            isEnabled = false
            alpha = 0.3f
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginStart = 8 }
            setOnClickListener { onIntentDeclared(null) }
        }
        buttonRow.addView(btnOpenAnyway)

        root.addView(buttonRow)

        startCountdown(config.gateDelaySeconds)
        return root
    }

    private fun startCountdown(seconds: Int) {
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                tvCountdown.text = context.getString(
                    com.defang.launcher.R.string.gate_opening_in, remaining
                )
            }

            override fun onFinish() {
                tvCountdown.text = ""
                btnOpenAnyway.isEnabled = true
                btnOpenAnyway.alpha = 1.0f
            }
        }.start()
    }

    fun cancel() {
        countdownTimer?.cancel()
    }
}
