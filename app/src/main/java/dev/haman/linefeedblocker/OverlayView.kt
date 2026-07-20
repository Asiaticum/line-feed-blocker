package dev.haman.linefeedblocker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class OverlayView(context: Context) : FrameLayout(context) {
    private val blockingTouchListener = OnTouchListener { view, event ->
        if (event.action == MotionEvent.ACTION_UP) view.performClick()
        true
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = true
        setOnTouchListener(blockingTouchListener)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun applySettings(settings: InspectorPreferences.OverlaySettings) {
        val alpha = (settings.opacityPercent * 255 / 100).coerceIn(0, 255)
        val base = settings.backgroundColor
        val fill = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        background = GradientDrawable().apply {
            setColor(fill)
        }
    }
}
