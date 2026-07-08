package com.defang.launcher.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SYSTEM_ALERT_WINDOW views.
 * Each overlay (intent gate, HUD, end-card) registers itself here.
 * Only one non-HUD overlay is shown at a time.
 */
@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var currentFullscreenView: View? = null
    private var hudView: View? = null

    fun showFullscreen(view: View) {
        dismissFullscreen()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        windowManager.addView(view, params)
        currentFullscreenView = view
    }

    fun dismissFullscreen() {
        currentFullscreenView?.let {
            runCatching { windowManager.removeViewImmediate(it) }
            currentFullscreenView = null
        }
    }

    fun showHud(view: View) {
        dismissHud()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
        params.x = 24
        params.y = 80
        windowManager.addView(view, params)
        hudView = view
    }

    fun dismissHud() {
        hudView?.let {
            runCatching { windowManager.removeViewImmediate(it) }
            hudView = null
        }
    }

    fun dismissAll() {
        dismissFullscreen()
        dismissHud()
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
}
