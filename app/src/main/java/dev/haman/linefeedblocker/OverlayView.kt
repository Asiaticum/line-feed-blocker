package dev.haman.linefeedblocker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

class OverlayView(context: Context) : FrameLayout(context) {
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        text = context.getString(R.string.overlay_label)
        textSize = 18f
        setTextColor(Color.DKGRAY)
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun applySettings(settings: InspectorPreferences.OverlaySettings) {
        val alpha = (settings.opacityPercent * 255 / 100).coerceIn(0, 255)
        val fill = Color.argb(
            alpha,
            Color.red(settings.backgroundColor),
            Color.green(settings.backgroundColor),
            Color.blue(settings.backgroundColor),
        )
        background = GradientDrawable().apply {
            setColor(fill)
            if (settings.debugBorder) {
                setStroke((2 * resources.displayMetrics.density).toInt().coerceAtLeast(1), Color.RED)
            }
        }
        label.visibility = if (settings.showText) View.VISIBLE else View.GONE
        isClickable = settings.touchBlocking
    }
}
