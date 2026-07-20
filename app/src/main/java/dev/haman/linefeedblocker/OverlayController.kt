package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: OverlayView? = null
    private var visible = false

    val isShowing: Boolean get() = visible

    fun show(bounds: IntBounds, settings: InspectorPreferences.OverlaySettings) {
        val width = bounds.width
        val height = bounds.height
        if (width <= 0 || height <= 0) {
            hide()
            return
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (settings.touchBlocking) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        val parameters = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
            title = "LINE Feed Blocker"
        }

        val current = overlayView
        if (current == null) {
            val view = OverlayView(service).also { it.applySettings(settings) }
            try {
                windowManager.addView(view, parameters)
                overlayView = view
                visible = true
            } catch (error: Throwable) {
                overlayView = null
                throw error
            }
        } else {
            current.applySettings(settings)
            windowManager.updateViewLayout(current, parameters)
            current.visibility = View.VISIBLE
            visible = true
        }
    }

    fun conceal() {
        overlayView?.visibility = View.GONE
        visible = false
    }

    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        visible = false
        runCatching { windowManager.removeViewImmediate(view) }
    }
}
