package dev.haman.linefeedblocker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

class TouchBlockerView(context: Context) : FrameLayout(context) {
    private val label = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 2
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = true
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }

    fun applyStyle(name: String, settings: InspectorPreferences.OverlaySettings) {
        val alpha = (settings.opacityPercent.coerceAtLeast(90) * 255 / 100).coerceIn(0, 255)
        val base = settings.backgroundColor
        val fill = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        val luminance = (Color.red(base) * 299 + Color.green(base) * 587 + Color.blue(base) * 114) / 1000
        label.setTextColor(if (luminance >= 128) Color.rgb(45, 45, 45) else Color.WHITE)
        label.text = if (name == "talk-promoted") "" else context.getString(R.string.restricted_symbol)
        label.textSize = 24f
        background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 8f * resources.displayMetrics.density
            val strokeColor = if (settings.debugBorder) Color.RED else Color.argb(100, 128, 128, 128)
            setStroke((if (settings.debugBorder) 2 else 1) * resources.displayMetrics.density.toInt().coerceAtLeast(1), strokeColor)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
