package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

class TouchBlockerController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val views = mutableMapOf<String, TouchBlockerView>()

    fun sync(targets: Map<String, IntBounds>, settings: InspectorPreferences.OverlaySettings) {
        val removed = views.keys - targets.keys
        removed.forEach(::remove)

        targets.forEach { (name, bounds) ->
            val adjustedBounds = adjustBounds(name, bounds)
            if (adjustedBounds.width <= 0 || adjustedBounds.height <= 0) {
                remove(name)
                return@forEach
            }
            val parameters = WindowManager.LayoutParams(
                adjustedBounds.width,
                adjustedBounds.height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = adjustedBounds.left
                y = adjustedBounds.top
                title = "LINE Feed Blocker: $name"
            }
            val current = views[name]
            if (current == null) {
                val view = TouchBlockerView(service).also { it.applyStyle(name, settings) }
                windowManager.addView(view, parameters)
                views[name] = view
            } else {
                current.applyStyle(name, settings)
                windowManager.updateViewLayout(current, parameters)
            }
        }
    }

    private fun adjustBounds(name: String, bounds: IntBounds): IntBounds {
        val density = service.resources.displayMetrics.density
        return when (name) {
            "shopping-tab", "mini-app-tab" -> bounds.copy(
                top = (bounds.top + (30 * density).toInt()).coerceAtMost(bounds.bottom),
            )
            "agent-header", "agent-floating" -> {
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

    fun hideAll() {
        views.keys.toList().forEach(::remove)
    }

    private fun remove(name: String) {
        val view = views.remove(name) ?: return
        runCatching { windowManager.removeViewImmediate(view) }
    }
}
