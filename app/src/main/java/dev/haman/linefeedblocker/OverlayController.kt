package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var overlayView: OverlayView? = null
    private var visible = false

    val isShowing: Boolean get() = visible

    fun show(bounds: IntBounds, settings: InspectorPreferences.OverlaySettings, name: String) {
        val screen = currentScreenBounds()
        val density = service.resources.displayMetrics.density
        val minimumSize = (48 * density).toInt().coerceAtLeast(1)
        val displayed = OverlayAdjustmentMath.apply(
            bounds,
            InspectorPreferences.overlayAdjustment(service, name),
            density,
            screen,
            minimumSize,
        )
        show(displayed) { it.applySettings(settings) }
    }

    fun showBrowserBlock(bounds: IntBounds) = show(bounds, OverlayView::showBrowserBlock)

    private fun show(bounds: IntBounds, configure: (OverlayView) -> Unit) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            hide()
            return
        }

        val current = overlayView
        val view = if (current == null) {
            OverlayView(service).also {
                try {
                    windowManager.addView(it, layoutParameters(bounds))
                    overlayView = it
                } catch (error: Throwable) {
                    overlayView = null
                    throw error
                }
            }
        } else {
            current
        }

        configure(view)
        windowManager.updateViewLayout(view, layoutParameters(bounds))
        view.visibility = View.VISIBLE
        visible = true
    }

    private fun layoutParameters(bounds: IntBounds) = WindowManager.LayoutParams(
        bounds.width,
        bounds.height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        title = "LINE Feed Blocker"
    }

    private fun currentScreenBounds(): IntBounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            IntBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        } else {
            val metrics = service.resources.displayMetrics
            IntBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
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
