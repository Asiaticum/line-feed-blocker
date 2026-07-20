package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

class TouchBlockerController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val views = mutableMapOf<String, TouchBlockerView>()

    fun sync(targets: Map<String, IntBounds>, settings: InspectorPreferences.OverlaySettings) {
        val removed = views.keys - targets.keys
        removed.forEach(::remove)

        val screen = currentScreenBounds()
        val density = service.resources.displayMetrics.density
        val minimumSize = (32 * density).toInt().coerceAtLeast(1)
        targets.forEach { (name, bounds) ->
            val base = adjustBounds(name, bounds)
            val displayed = OverlayAdjustmentMath.apply(
                base,
                InspectorPreferences.overlayAdjustment(service, name),
                density,
                screen,
                minimumSize,
            )
            if (displayed.width <= 0 || displayed.height <= 0) {
                remove(name)
                return@forEach
            }

            val current = views[name]
            val view = if (current == null) {
                TouchBlockerView(service).also {
                    windowManager.addView(it, layoutParameters(name, displayed))
                    views[name] = it
                }
            } else {
                current
            }
            view.applyStyle(name, settings)
            windowManager.updateViewLayout(view, layoutParameters(name, displayed))
        }
    }

    private fun layoutParameters(name: String, bounds: IntBounds) = WindowManager.LayoutParams(
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
        title = "LINE Feed Blocker: $name"
    }

    private fun adjustBounds(name: String, bounds: IntBounds): IntBounds {
        val density = service.resources.displayMetrics.density
        return when (name) {
            "shopping-tab", "mini-app-tab" -> bounds.copy(
                top = (bounds.top + (30 * density).toInt()).coerceAtMost(bounds.bottom),
            )
            "friends-header", "agent-header", "agent-floating" -> {
                val expansion = (3 * density).toInt()
                IntBounds(
                    left = (bounds.left - expansion).coerceAtLeast(0),
                    top = (bounds.top - expansion).coerceAtLeast(0),
                    right = bounds.right + expansion,
                    bottom = bounds.bottom + expansion,
                )
            }
            else -> bounds
        }
    }

    private fun currentScreenBounds(): IntBounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            IntBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        } else {
            val metrics = service.resources.displayMetrics
            IntBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

    fun hideAll() {
        views.keys.toList().forEach(::remove)
    }

    fun hide(name: String) {
        remove(name)
    }

    private fun remove(name: String) {
        val view = views.remove(name) ?: return
        runCatching { windowManager.removeViewImmediate(view) }
    }
}
