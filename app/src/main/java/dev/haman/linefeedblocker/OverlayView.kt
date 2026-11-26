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
    private val browserMessage = TextView(context).apply {
        setText(R.string.in_app_browser_blocked)
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        visibility = View.GONE
    }

    private val blockingTouchListener = OnTouchListener { view, event ->
        if (event.action == MotionEvent.ACTION_UP) view.performClick()
        true
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = true
        setOnTouchListener(blockingTouchListener)
        addView(browserMessage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun applySettings(settings: InspectorPreferences.OverlaySettings) {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        browserMessage.visibility = View.GONE
        val alpha = (settings.opacityPercent * 255 / 100).coerceIn(0, 255)
        val base = settings.backgroundColor
        val fill = Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
        background = GradientDrawable().apply {
            setColor(fill)
        }
    }

    fun showBrowserBlock() {
        if (browserMessage.visibility == View.VISIBLE) return
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        browserMessage.visibility = View.VISIBLE
    }
}
