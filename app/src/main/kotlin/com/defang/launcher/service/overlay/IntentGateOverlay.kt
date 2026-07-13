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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen interstitial that appears before a watched app opens.
 *
 * Layout (top to bottom):
 *   1. Tidbit — the awareness fact, shown large and prominent immediately.
 *   2. Offline prompt — a small real-world task offered as the alternative
 *      to opening the app at all.
 *   3. Slide hint.
 *   4. Path track — the active unlock. A randomly chosen shape (line, circle,
 *      square, triangle, parabola — random orientation); the thumb must be
 *      traced along the whole path in one continuous motion, and lifting the
 *      finger snaps it back to the start.
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
        val slider = PathSlideView(
            context = context,
            onComplete = { onIntentDeclared(null) },
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (260 * density).toInt(),
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
 * Path-tracing unlock track.
 *
 * Each gate draws one randomly chosen shape — line, circle, square, triangle
 * or parabola, in a random orientation — and the thumb must be traced along
 * the whole path in one continuous drag. Straying from the path just stalls
 * the thumb; lifting the finger snaps it back to the start. No speed
 * requirement — the deliberate act is tracing the full path.
 *
 * The path is a dense polyline; the thumb advances by snapping to the sample
 * nearest the finger inside a small look-ahead window, so the path cannot be
 * short-circuited by jumping straight to the end point.
 */
@SuppressLint("ViewConstructor")
private class PathSlideView(
    context: Context,
    private val onComplete: () -> Unit,
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val thumbRadius = 16 * density
    private val followRadius = 48 * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 55, 55)
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 180, 180)
    }

    // Path samples (x,y interleaved) and the Path object drawn as the track
    private var samples = FloatArray(0)
    private val trackPath = android.graphics.Path()
    private var cur = 0 // index of the sample the thumb sits on
    private var dragging = false
    private var completed = false

    private val sampleCount get() = samples.size / 2
    private fun sx(i: Int) = samples[i * 2]
    private fun sy(i: Int) = samples[i * 2 + 1]

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        samples = buildShape(w.toFloat(), h.toFloat())
        trackPath.reset()
        trackPath.moveTo(sx(0), sy(0))
        for (i in 1 until sampleCount) trackPath.lineTo(sx(i), sy(i))
        cur = 0
        completed = false
    }

    /** ~200 evenly spread points for a random shape inside the view bounds. */
    private fun buildShape(w: Float, h: Float): FloatArray {
        val pad = thumbRadius * 2
        val cx = w / 2f
        val cy = h / 2f
        val r = (minOf(w, h) / 2f - pad)
        val n = 200
        val out = FloatArray(n * 2)

        fun fromParam(f: (Float) -> Pair<Float, Float>): FloatArray {
            for (i in 0 until n) {
                val (x, y) = f(i / (n - 1).toFloat())
                out[i * 2] = x
                out[i * 2 + 1] = y
            }
            return out
        }

        fun perimeter(corners: List<Pair<Float, Float>>): FloatArray {
            // Closed polygon, points distributed by edge length
            val closed = corners + corners.first()
            val lengths = closed.zipWithNext { a, b -> hypot(b.first - a.first, b.second - a.second) }
            val total = lengths.sum()
            return fromParam { t ->
                var dist = t * total
                for ((edge, len) in lengths.withIndex()) {
                    if (dist <= len || edge == lengths.lastIndex) {
                        val (ax, ay) = closed[edge]
                        val (bx, by) = closed[edge + 1]
                        val u = (dist / len).coerceIn(0f, 1f)
                        return@fromParam ax + (bx - ax) * u to ay + (by - ay) * u
                    }
                    dist -= len
                }
                closed.last()
            }
        }

        val reverse = Random.nextBoolean()
        val shape = when (Random.nextInt(5)) {
            0 -> { // line — horizontal, vertical or diagonal
                val ends = listOf(
                    (pad to cy) to (w - pad to cy),
                    (cx to pad) to (cx to h - pad),
                    (pad to pad) to (w - pad to h - pad),
                    (pad to h - pad) to (w - pad to pad),
                ).random()
                fromParam { t ->
                    val (a, b) = ends
                    a.first + (b.first - a.first) * t to a.second + (b.second - a.second) * t
                }
            }
            1 -> { // full circle, random start quadrant
                val start = Random.nextInt(4) * 90f
                fromParam { t ->
                    val deg = start + t * 360f
                    val rad = Math.toRadians(deg.toDouble())
                    cx + r * cos(rad).toFloat() to cy + r * sin(rad).toFloat()
                }
            }
            2 -> { // square perimeter, random start corner
                val corners = listOf(
                    cx - r to cy - r, cx + r to cy - r,
                    cx + r to cy + r, cx - r to cy + r,
                )
                val startAt = Random.nextInt(4)
                perimeter(List(4) { corners[(startAt + it) % 4] })
            }
            3 -> { // triangle perimeter, random rotation
                val rot = Random.nextInt(4) * 90.0
                perimeter(List(3) {
                    val rad = Math.toRadians(rot + it * 120.0 - 90.0)
                    cx + r * cos(rad).toFloat() to cy + r * sin(rad).toFloat()
                })
            }
            else -> { // parabola across the width, opening up or down
                val opensDown = Random.nextBoolean()
                fromParam { t ->
                    val u = t * 2f - 1f
                    val x = pad + t * (w - 2 * pad)
                    val y = if (opensDown) pad + (h - 2 * pad) * u * u
                    else (h - pad) - (h - 2 * pad) * u * u
                    x to y
                }
            }
        }

        if (reverse) {
            // Trace the same shape in the opposite direction
            for (i in 0 until n / 2) {
                val j = n - 1 - i
                val tx = shape[i * 2]; val ty = shape[i * 2 + 1]
                shape[i * 2] = shape[j * 2]; shape[i * 2 + 1] = shape[j * 2 + 1]
                shape[j * 2] = tx; shape[j * 2 + 1] = ty
            }
        }
        return shape
    }

    override fun onDraw(canvas: Canvas) {
        if (sampleCount == 0) return
        canvas.drawPath(trackPath, trackPaint)
        canvas.drawCircle(sx(cur), sy(cur), thumbRadius, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (completed || sampleCount == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hypot(event.x - sx(cur), event.y - sy(cur)) > followRadius) return false
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                // Advance to the nearest sample inside a small window around the
                // thumb — the finger has to actually trace the path
                var best = -1
                var bestDist = followRadius
                for (i in maxOf(0, cur - 6)..minOf(sampleCount - 1, cur + 12)) {
                    val d = hypot(event.x - sx(i), event.y - sy(i))
                    if (d < bestDist) { bestDist = d; best = i }
                }
                if (best >= 0 && best != cur) {
                    cur = best
                    if (cur >= sampleCount - 1) {
                        completed = true
                        dragging = false
                        onComplete()
                        return true
                    }
                    invalidate()
                }
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
        cur = 0
        dragging = false
        invalidate()
    }
}
