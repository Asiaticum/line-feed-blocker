package dev.haman.linefeedblocker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object InspectorPreferences {
    private const val FILE_NAME = "line_feed_blocker"
    private const val KEY_INSPECTOR_ENABLED = "inspector_enabled"
    private const val KEY_DUMP_REQUEST = "dump_request"
    private const val KEY_DUMP_HANDLED = "dump_handled"
    private const val KEY_FEATURE_ENABLED = "feature_enabled"
    private const val KEY_TOP_OFFSET_DP = "top_offset_dp"
    private const val KEY_BOTTOM_OFFSET_DP = "bottom_offset_dp"
    private const val KEY_BASE_BOTTOM_EXTENSION_MIGRATED = "base_bottom_extension_60_migrated"
    private const val KEY_HORIZONTAL_INSET_DP = "horizontal_inset_dp"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_TOUCH_BLOCKING = "touch_blocking"
    private const val KEY_DEBUG_BORDER = "debug_border"
    private const val KEY_SHOW_TEXT = "show_text"
    private const val KEY_LAST_TREE_PATH = "last_tree_path"

    val operationalKeys = setOf(
        KEY_FEATURE_ENABLED,
        KEY_TOP_OFFSET_DP,
        KEY_BOTTOM_OFFSET_DP,
        KEY_HORIZONTAL_INSET_DP,
        KEY_OPACITY,
        KEY_BACKGROUND_COLOR,
        KEY_TOUCH_BLOCKING,
        KEY_DEBUG_BORDER,
        KEY_SHOW_TEXT,
    )

    data class OverlaySettings(
        val enabled: Boolean,
        val topOffsetDp: Int,
        val bottomOffsetDp: Int,
        val horizontalInsetDp: Int,
        val opacityPercent: Int,
        val backgroundColor: Int,
        val touchBlocking: Boolean,
        val debugBorder: Boolean,
        val showText: Boolean,
    )

    fun isInspectorEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_INSPECTOR_ENABLED, false)

    fun setInspectorEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_INSPECTOR_ENABLED, enabled).apply()
    }

    fun requestDump(context: Context) {
        preferences(context).edit().putLong(KEY_DUMP_REQUEST, System.currentTimeMillis()).apply()
    }

    fun dumpRequest(context: Context): Long =
        preferences(context).getLong(KEY_DUMP_REQUEST, 0L)

    fun handledDumpRequest(context: Context): Long =
        preferences(context).getLong(KEY_DUMP_HANDLED, 0L)

    fun markDumpHandled(context: Context, request: Long) {
        preferences(context).edit().putLong(KEY_DUMP_HANDLED, request).apply()
    }

    fun overlaySettings(context: Context): OverlaySettings {
        val preferences = preferences(context)
        val bottomOffsetDp = if (!preferences.getBoolean(KEY_BASE_BOTTOM_EXTENSION_MIGRATED, false)) {
            preferences.edit()
                .putInt(KEY_BOTTOM_OFFSET_DP, 0)
                .putBoolean(KEY_BASE_BOTTOM_EXTENSION_MIGRATED, true)
                .commit()
            0
        } else {
            preferences.getInt(KEY_BOTTOM_OFFSET_DP, 0)
        }
        return OverlaySettings(
            enabled = preferences.getBoolean(KEY_FEATURE_ENABLED, false),
            topOffsetDp = preferences.getInt(KEY_TOP_OFFSET_DP, 0),
            bottomOffsetDp = bottomOffsetDp,
            horizontalInsetDp = preferences.getInt(KEY_HORIZONTAL_INSET_DP, 0),
            opacityPercent = preferences.getInt(KEY_OPACITY, 100).coerceIn(0, 100),
            backgroundColor = preferences.getInt(KEY_BACKGROUND_COLOR, Color.WHITE),
            touchBlocking = preferences.getBoolean(KEY_TOUCH_BLOCKING, true),
            debugBorder = preferences.getBoolean(KEY_DEBUG_BORDER, false),
            showText = preferences.getBoolean(KEY_SHOW_TEXT, false),
        )
    }

    fun setFeatureEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()

    fun setTopOffsetDp(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_TOP_OFFSET_DP, value.coerceIn(-500, 500)).apply()

    fun setBottomOffsetDp(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_BOTTOM_OFFSET_DP, value.coerceIn(-500, 500)).apply()

    fun setHorizontalInsetDp(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_HORIZONTAL_INSET_DP, value.coerceIn(-200, 200)).apply()

    fun setOpacityPercent(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_OPACITY, value.coerceIn(0, 100)).apply()

    fun setBackgroundColor(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_BACKGROUND_COLOR, value).apply()

    fun setTouchBlocking(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_TOUCH_BLOCKING, value).apply()

    fun setDebugBorder(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_DEBUG_BORDER, value).apply()

    fun setShowText(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_SHOW_TEXT, value).apply()

    fun resetOperationalSettings(context: Context) {
        preferences(context).edit().apply {
            operationalKeys.forEach(::remove)
        }.apply()
    }

    fun setLastTreePath(context: Context, value: String) {
        preferences(context).edit().putString(KEY_LAST_TREE_PATH, value).apply()
    }

    fun lastTreePath(context: Context): String =
        preferences(context).getString(KEY_LAST_TREE_PATH, null) ?: "未保存"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
