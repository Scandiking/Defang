package com.defang.launcher.service.overlay

import android.content.Context
import android.content.Intent
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Full-screen interstitial that appears before a watched app launches.
 *
 * Behaviour:
 * - Shows "What are you opening this for?" with a countdown (default 8s).
 * - "Open anyway" button is visually disabled and unclickable during countdown.
 * - Three declared-intent cards that skip the countdown.
 * - "Go back" always available (returns to launcher).
 * - Static tidbit shown for the full duration of the countdown.
 *
 * This is a View-based overlay (not Compose) because it runs in the
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
    private lateinit var tvTidbit: TextView

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 10, 10, 10))
            setPadding(64, 96, 64, 96)
        }

        // "What are you opening this for?"
        val tvQuestion = TextView(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_question)
            textSize = 22f
            setTextColor(Color.rgb(224, 224, 224))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(tvQuestion)

        // Tidbit
        tvTidbit = TextView(context).apply {
            text = tidbitSelector.next(contentTrack)
            textSize = 14f
            setTextColor(Color.rgb(128, 128, 128))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        root.addView(tvTidbit)

        // Intent cards
        val intents = listOf(
            context.getString(com.defang.launcher.R.string.gate_intent_post),
            context.getString(com.defang.launcher.R.string.gate_intent_dm),
            context.getString(com.defang.launcher.R.string.gate_intent_lookup),
        )
        intents.forEach { label ->
            val btn = Button(context).apply {
                text = label
                textSize = 14f
                setTextColor(Color.rgb(176, 125, 74))
                setBackgroundColor(Color.argb(40, 176, 125, 74))
                setPadding(48, 24, 48, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = 16
                layoutParams = lp
                setOnClickListener { onIntentDeclared(label) }
            }
            root.addView(btn)
        }

        // Countdown label
        tvCountdown = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(96, 96, 96))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 8)
        }
        root.addView(tvCountdown)

        // Bottom row: "Go back" and "Open anyway" side by side, equal weight
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = 8
            layoutParams = lp
        }

        val btnBack = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_go_back)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginEnd = 8
            }
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = 8
            }
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
