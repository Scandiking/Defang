package com.defang.launcher.service.overlay

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen end card shown when the session timer expires.
 *
 * Extension friction design (non-negotiable per PRD §7.4):
 * 1. "I need more time" button is invisible for the first 30 seconds.
 * 2. Tapping it reveals a "Why?" text field (minimum 10 characters enforced).
 * 3. Extension is limited to once per day across all watched apps.
 * 4. Adds exactly 10 minutes — not configurable mid-session.
 * 5. No second extension.
 */
class EndCardOverlay(
    private val context: Context,
    private val appLabel: String,
    private val sessionDurationMs: Long,
    private val intentDeclared: String?,
    private val tidbit: String,
    private val offlinePrompt: String,
    private val extensionAvailable: Boolean,
    private val onRequestExtension: (reason: String) -> Unit,
    private val onGoHome: () -> Unit,
    private val onAnotherPrompt: () -> Unit,
) {
    private var btnExtension: Button? = null
    private var extensionSection: LinearLayout? = null
    private var etReason: EditText? = null
    private var btnConfirmExtension: Button? = null
    private var tvOfflinePrompt: TextView? = null
    private var extensionRevealTimer: CountDownTimer? = null

    val view: View by lazy { buildView() }

    private fun buildView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.argb(250, 10, 10, 10))
            setPadding(64, 128, 64, 96)
        }

        // Time spent
        val minutes = sessionDurationMs / 60_000
        val seconds = (sessionDurationMs % 60_000) / 1000
        val durationStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

        val tvTime = TextView(context).apply {
            text = context.getString(com.defang.launcher.R.string.endcard_time_used, durationStr, appLabel)
            textSize = 18f
            setTextColor(Color.rgb(224, 224, 224))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        root.addView(tvTime)

        // Intent declared
        val tvIntent = TextView(context).apply {
            text = if (intentDeclared != null)
                context.getString(com.defang.launcher.R.string.endcard_intent_declared, intentDeclared)
            else
                context.getString(com.defang.launcher.R.string.endcard_intent_none)
            textSize = 13f
            setTextColor(Color.rgb(128, 128, 128))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(tvIntent)

        // Tidbit
        val tvTidbit = TextView(context).apply {
            text = tidbit
            textSize = 14f
            setTextColor(Color.rgb(160, 160, 160))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(tvTidbit)

        // Offline prompt
        tvOfflinePrompt = TextView(context).apply {
            text = offlinePrompt
            textSize = 16f
            setTextColor(Color.rgb(176, 125, 74))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        root.addView(tvOfflinePrompt)

        val btnAnotherPrompt = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.endcard_another_prompt)
            textSize = 12f
            setTextColor(Color.rgb(128, 128, 128))
            setBackgroundColor(Color.TRANSPARENT)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            layoutParams = lp
            setOnClickListener { onAnotherPrompt() }
        }
        root.addView(btnAnotherPrompt)

        // ── Extension section (hidden for first 30s) ──────────────────────
        if (extensionAvailable) {
            btnExtension = Button(context).apply {
                text = context.getString(com.defang.launcher.R.string.endcard_more_time)
                visibility = View.GONE // hidden for first 30s
                textSize = 13f
                setTextColor(Color.rgb(128, 128, 128))
                setBackgroundColor(Color.argb(30, 128, 128, 128))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 48
                layoutParams = lp
                setOnClickListener { showExtensionForm() }
            }
            root.addView(btnExtension)

            extensionSection = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 8
                layoutParams = lp
            }

            val tvWhy = TextView(context).apply {
                text = context.getString(com.defang.launcher.R.string.endcard_why_prompt)
                textSize = 14f
                setTextColor(Color.rgb(200, 200, 200))
                setPadding(0, 0, 0, 8)
            }
            extensionSection!!.addView(tvWhy)

            etReason = EditText(context).apply {
                hint = context.getString(com.defang.launcher.R.string.endcard_why_hint)
                setHintTextColor(Color.rgb(80, 80, 80))
                setTextColor(Color.rgb(200, 200, 200))
                setBackgroundColor(Color.argb(60, 255, 255, 255))
                textSize = 14f
                minLines = 2
                maxLines = 4
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                layoutParams = lp
            }
            extensionSection!!.addView(etReason)

            btnConfirmExtension = Button(context).apply {
                text = context.getString(com.defang.launcher.R.string.endcard_why_confirm)
                isEnabled = false
                alpha = 0.3f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 8
                layoutParams = lp
                setOnClickListener {
                    val reason = etReason?.text?.toString() ?: ""
                    if (reason.length >= 10) onRequestExtension(reason)
                }
            }
            extensionSection!!.addView(btnConfirmExtension)
            root.addView(extensionSection)

            etReason?.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val valid = (s?.length ?: 0) >= 10
                    btnConfirmExtension?.isEnabled = valid
                    btnConfirmExtension?.alpha = if (valid) 1.0f else 0.3f
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // Reveal the extension button after 30 seconds
            extensionRevealTimer = object : CountDownTimer(30_000L, 30_000L) {
                override fun onTick(p0: Long) {}
                override fun onFinish() {
                    btnExtension?.visibility = View.VISIBLE
                }
            }.start()
        } else {
            // Extension already used today
            val tvNoExtension = TextView(context).apply {
                text = context.getString(com.defang.launcher.R.string.endcard_extension_used)
                textSize = 12f
                setTextColor(Color.rgb(96, 96, 96))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = 48
                layoutParams = lp
            }
            root.addView(tvNoExtension)
        }

        // ── Go home ──────────────────────────────────────────────────────
        val btnGoHome = Button(context).apply {
            text = context.getString(com.defang.launcher.R.string.endcard_go_home)
            textSize = 14f
            setTextColor(Color.rgb(224, 224, 224))
            setBackgroundColor(Color.argb(60, 224, 224, 224))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = 32
            layoutParams = lp
            setOnClickListener { onGoHome() }
        }
        root.addView(btnGoHome)

        return root
    }

    private fun showExtensionForm() {
        btnExtension?.visibility = View.GONE
        extensionSection?.visibility = View.VISIBLE
    }

    fun updateOfflinePrompt(newPrompt: String) {
        tvOfflinePrompt?.text = newPrompt
    }

    fun cancel() {
        extensionRevealTimer?.cancel()
    }
}
