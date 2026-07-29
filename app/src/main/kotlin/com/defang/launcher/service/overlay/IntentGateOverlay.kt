package com.defang.launcher.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.CountDownTimer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.util.TidbitSelector
import kotlin.math.hypot

/**
 * Full-screen interstitial that appears before a watched app opens.
 *
 * Layout (top to bottom):
 *   1. Tidbit — a short awareness warning, shown large and prominent. It
 *      escalates in severity as the slider is dragged (see below).
 *   2. Offline prompt — a small real-world task offered as the alternative
 *      to opening the app at all.
 *   3. Slide hint.
 *   4. Slide-to-enter track — the active unlock. A plain horizontal bar; the
 *      thumb is dragged left→right, and lifting the finger before the end snaps
 *      it back to the start.
 *   5. Countdown — the configured gate delay (AppConfig.gateDelaySeconds).
 *   6. "Go back" button.
 *
 * The slide is a deliberateness gate, not a skill game (PRD §Phase 2): no
 * progress fill, no animation, no reward feedback. Sliding is deliberately not
 * a reward — as the thumb advances, the warning above escalates from mild to
 * harsh, so committing to entry surfaces a worse truth rather than a payoff.
 * The harshest line lands exactly at the point of entry.
 *
 * Sliding to the end and waiting out the delay are BOTH required: the app opens
 * only once the slide is complete AND the countdown has finished, in either
 * order. The slide is something to do while waiting, not a way to skip it.
 *
 * This is a View-based overlay (not Compose) because it runs inside the
 * AccessibilityService process — there is no Activity lifecycle to host Compose.
 */
class IntentGateOverlay(
    private val context: Context,
    private val contentTrack: ContentTrack,
    private val tidbitSelector: TidbitSelector,
    private val offlinePrompt: String?,
    private val delaySeconds: Int,
    private val opensToday: Int,
    // When true, the unlock is a physical NFC-tag scan handled by the service:
    // this overlay shows only the tidbit + countdown, and the service dismisses
    // it and hands off to NfcUnlockActivity once the countdown ends. No slide is
    // revealed. When false, the slide-to-open below is the unlock, as before.
    private val nfcMode: Boolean = false,
    // When true, a QR/barcode scan (handled by the service) is the unlock:
    // behaves exactly like [nfcMode] — this overlay shows only tidbit + countdown
    // and the service dismisses it and hands off to QrUnlockActivity once the
    // countdown ends. Only the "scan after the countdown" mode uses this; the
    // "scan immediately" mode skips the overlay entirely.
    private val qrMode: Boolean = false,
    private val onTimerFinished: () -> Unit,
    private val onIntentDeclared: (intent: String?) -> Unit,
    private val onGoBack: () -> Unit,
) {
    val view: View by lazy { buildView() }

    private var countdownTimer: CountDownTimer? = null
    private var timerDone = false
    private var unlocked = false
    private lateinit var tvCountdown: TextView
    private lateinit var tvTidbit: TextView
    private lateinit var tvSlideHint: TextView
    private lateinit var slideToOpen: SlideToOpenView

    // The warning is picked once at build time: addictionLevel(opensToday) selects
    // the level, then a random variant at that level (TidbitSelector.lineForLevel).
    // One text per gate showing — it does not change while the gate is up.

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(245, 10, 10, 10))
            setPadding(64, 96, 64, 96)
        }

        // Tidbit — the one thing the user should read. Short, cigarette-warning
        // style; a single line chosen by the frequency floor (more opens today →
        // harsher). It does NOT change while the gate is up.
        val level = addictionLevel(opensToday)
        tvTidbit = TextView(context).apply {
            text = tidbitSelector.lineForLevel(contentTrack, level)
            textSize = 22f
            setTextColor(Color.rgb(210, 210, 210))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, if (offlinePrompt != null) 48 else 80)
            setLineSpacing(0f, 1.3f)
        }
        root.addView(tvTidbit)

        val density = context.resources.displayMetrics.density

        // During the wait there is only the text — no gesture to distract from
        // it. The slide-to-open control (below) is revealed once the countdown
        // ends; sliding it is the unlock.

        // Countdown — dim; the app opens only after this elapses.
        tvCountdown = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(96, 96, 96))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        root.addView(tvCountdown)

        // Offline prompt — the real-world alternative, before the unlock
        if (offlinePrompt != null) {
            val tvPrompt = TextView(context).apply {
                text = context.getString(
                    com.defang.launcher.R.string.gate_offline_suggestion, offlinePrompt
                )
                textSize = 20f
                setTextColor(Color.rgb(160, 160, 160))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 64)
                setLineSpacing(0f, 1.3f)
            }
            root.addView(tvPrompt)
        }

        // Slide-to-open — hidden until the countdown ends. Sliding the thumb to
        // the far end is the unlock (lock-screen style). Guarded on timerDone so
        // it can never open early even if revealed by a race.
        tvSlideHint = TextView(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_slide_hint)
            textSize = 13f
            setTextColor(Color.rgb(150, 150, 150))
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        root.addView(tvSlideHint)

        slideToOpen = SlideToOpenView(
            context = context,
            onComplete = {
                if (timerDone && !unlocked) {
                    unlocked = true
                    onIntentDeclared(null)
                }
            },
        ).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (72 * density).toInt(),
            ).also { it.bottomMargin = (16 * density).toInt() }
        }
        root.addView(slideToOpen)

        val btnBack = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_go_back)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setOnClickListener { onGoBack() }
        }
        root.addView(btnBack)

        startCountdown(delaySeconds)
        return root
    }

    private fun startCountdown(seconds: Int) {
        if (seconds <= 0) {
            onTimerElapsed()
            return
        }
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                tvCountdown.text = context.getString(
                    com.defang.launcher.R.string.gate_wait_countdown, remaining
                )
            }

            override fun onFinish() = onTimerElapsed()
        }.start()
    }

    /**
     * The wait is over: clear the countdown, reveal the Open button, and signal
     * the service (which switches the screen to grayscale before the app shows,
     * so the feed never flashes in colour).
     */
    private fun onTimerElapsed() {
        tvCountdown.text = ""
        timerDone = true
        // In NFC or QR mode the service takes over here — dismisses this overlay
        // and launches the scan activity — so we never reveal the slide.
        if (!nfcMode && !qrMode) {
            tvSlideHint.visibility = View.VISIBLE
            slideToOpen.visibility = View.VISIBLE
        }
        onTimerFinished()
    }

    fun cancel() {
        countdownTimer?.cancel()
    }

    companion object {
        /**
         * Minimum watched-app opens (since midnight) to reach each addiction
         * level. Grows roughly exponentially so higher levels take
         * disproportionately more opens — the harshest lines are reserved for
         * genuinely heavy use and stay rare. Level = thresholds passed (0..N).
         * Keep this length aligned with the ladder arrays (`tidbits_*_ladder`).
         */
        private val LEVEL_THRESHOLDS = intArrayOf(2, 3, 4, 6, 8, 11, 15, 20, 27, 36, 48)

        /** Addiction level 0..LEVEL_THRESHOLDS.size from today's open count. */
        fun addictionLevel(opensToday: Int): Int = LEVEL_THRESHOLDS.count { opensToday >= it }
    }
}

/**
 * Slide-to-open unlock control (shown only after the countdown ends).
 *
 * A horizontal track with a thumb at the left, in the style of an old Android
 * lock screen: drag the thumb to the far right to open; lifting before the end
 * snaps it back. This is the final commit gesture — the deliberateness friction
 * is the wait that precedes it, not this slide.
 */
@SuppressLint("ViewConstructor")
private class SlideToOpenView(
    context: Context,
    private val onComplete: () -> Unit,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val thumbRadius = 20 * density
    private val followRadius = 56 * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 70)
        strokeWidth = 6 * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 120, 120)
        strokeWidth = 6 * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(215, 215, 215)
    }

    private var startX = 0f
    private var endX = 0f
    private var cy = 0f
    private var thumbX = 0f
    private var dragging = false
    private var completed = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        startX = thumbRadius + 4 * density
        endX = w - thumbRadius - 4 * density
        cy = h / 2f
        thumbX = startX
        completed = false
    }

    private fun fraction(): Float =
        if (endX <= startX) 0f else ((thumbX - startX) / (endX - startX)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        if (endX <= startX) return
        canvas.drawLine(startX, cy, endX, cy, trackPaint)
        if (thumbX > startX) canvas.drawLine(startX, cy, thumbX, cy, fillPaint)
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (completed || endX <= startX) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(event.x - thumbX, event.y - cy) > followRadius) return false
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                thumbX = event.x.coerceIn(startX, endX)
                if (fraction() >= 0.98f) {
                    thumbX = endX
                    completed = true
                    dragging = false
                    invalidate()
                    onComplete()
                    return true
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && !completed) resetThumb()
                return true
            }
        }
        return false
    }

    private fun resetThumb() {
        thumbX = startX
        dragging = false
        invalidate()
    }
}
