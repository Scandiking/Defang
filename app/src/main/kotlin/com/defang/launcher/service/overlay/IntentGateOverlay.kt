package com.defang.launcher.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.defang.launcher.domain.model.ContentTrack
import com.defang.launcher.util.TidbitSelector
import kotlin.math.abs

/**
 * Full-screen interstitial that appears before a watched app opens.
 *
 * Layout (top to bottom):
 *   1. Tidbit — the awareness fact, shown large and prominent immediately.
 *   2. Offline prompt — a small real-world task offered as the alternative
 *      to opening the app at all.
 *   3. Slide hint.
 *   4. Slide track — the active unlock. The thumb must be dragged the full
 *      width in one continuous motion; lifting the finger snaps it back.
 *   5. "Go back" button.
 *
 * The slide is a deliberateness gate, not a skill game (PRD §Phase 2): no
 * progress fill, no animation, no reward feedback — just a thumb on a track.
 *
 * This is a View-based overlay (not Compose) because it runs inside the
 * AccessibilityService process — there is no Activity lifecycle to host Compose.
 */
class IntentGateOverlay(
    private val context: Context,
    private val contentTrack: ContentTrack,
    private val tidbitSelector: TidbitSelector,
    private val offlinePrompt: String?,
    private val onIntentDeclared: (intent: String?) -> Unit,
    private val onGoBack: () -> Unit,
) {
    val view: View by lazy { buildView() }

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
            setPadding(0, 0, 0, if (offlinePrompt != null) 48 else 80)
            setLineSpacing(0f, 1.4f)
        }
        root.addView(tvTidbit)

        // Offline prompt — the real-world alternative, before the unlock
        if (offlinePrompt != null) {
            val tvPrompt = TextView(context).apply {
                text = context.getString(
                    com.defang.launcher.R.string.gate_offline_suggestion, offlinePrompt
                )
                textSize = 15f
                setTextColor(Color.rgb(160, 160, 160))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 64)
                setLineSpacing(0f, 1.3f)
            }
            root.addView(tvPrompt)
        }

        // Slide hint — small and dim
        val tvHint = TextView(context).apply {
            text = context.getString(com.defang.launcher.R.string.gate_slide_hint)
            textSize = 13f
            setTextColor(Color.rgb(120, 120, 120))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvHint)

        val density = context.resources.displayMetrics.density
        val slider = SlideToUnlockView(
            context = context,
            onComplete = { onIntentDeclared(null) },
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (72 * density).toInt(),
            ).also { it.bottomMargin = (24 * density).toInt() }
        }
        root.addView(slider)

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

        return root
    }

    fun cancel() {
        // Nothing time-based to stop — kept so callers can treat all overlays alike.
    }
}

/**
 * Horizontal slide-to-unlock track.
 *
 * The thumb must travel from the left end to the right end in one continuous
 * drag; lifting the finger before the end snaps it back to the start. No
 * speed requirement — the deliberate act is the full-width drag itself.
 */
@SuppressLint("ViewConstructor")
private class SlideToUnlockView(
    context: Context,
    private val onComplete: () -> Unit,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val thumbRadius = 16 * density
    private val grabRadius = 40 * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 55, 55)
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 180, 180)
    }

    private var trackStart = 0f
    private var trackEnd = 0f

    private var progressPx = 0f
    private var dragging = false
    private var completed = false
    private var lastX = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        trackStart = thumbRadius * 2
        trackEnd = w - thumbRadius * 2
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        canvas.drawLine(trackStart, cy, trackEnd, cy, trackPaint)
        canvas.drawCircle(trackStart + progressPx, cy, thumbRadius, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (completed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val thumbX = trackStart + progressPx
                if (abs(event.x - thumbX) > grabRadius) return false
                dragging = true
                lastX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val dx = event.x - lastX
                lastX = event.x
                progressPx = (progressPx + dx).coerceIn(0f, trackEnd - trackStart)
                if (progressPx >= trackEnd - trackStart) {
                    completed = true
                    dragging = false
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
        // Snap, not animate — a smooth return would be its own little reward
        progressPx = 0f
        dragging = false
        invalidate()
    }
}
