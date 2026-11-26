package dev.haman.linefeedblocker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONObject

object InspectorPreferences {
    private const val FILE_NAME = "line_feed_blocker"
    private const val KEY_INSPECTOR_ENABLED = "inspector_enabled"
    private const val KEY_DUMP_REQUEST = "dump_request"
    private const val KEY_DUMP_HANDLED = "dump_handled"
    private const val KEY_FEATURE_ENABLED = "feature_enabled"
    private const val KEY_TOP_OFFSET_DP = "top_offset_dp"
    private const val KEY_BOTTOM_OFFSET_DP = "bottom_offset_dp"
    private const val KEY_NUMERIC_ADJUSTMENT_MIGRATED = "numeric_adjustment_migrated"
    private const val KEY_HORIZONTAL_INSET_DP = "horizontal_inset_dp"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_BACKGROUND_COLOR = "background_color"
    private const val KEY_SHOW_FRIENDS_OVERLAY = "show_friends_overlay"
    private const val KEY_SHOW_AGENT_OVERLAY = "show_agent_overlay"
    private const val KEY_SHOW_SHOPPING_OVERLAY = "show_shopping_overlay"
    private const val KEY_SHOW_APP_OVERLAY = "show_app_overlay"
    private const val KEY_BLOCK_IN_APP_BROWSER = "block_in_app_browser"
    private const val KEY_ADJUSTMENT_TARGET = "adjustment_target"
    private const val KEY_DEBUG_OPTIONS_VISIBLE = "debug_options_visible"
    private const val KEY_SAVED_SETTINGS = "saved_settings_json"
    private const val ADJUSTMENT_PREFIX = "overlay_adjustment_"

    val operationalKeys = setOf(
        KEY_FEATURE_ENABLED,
        KEY_OPACITY,
        KEY_BACKGROUND_COLOR,
        KEY_SHOW_FRIENDS_OVERLAY,
        KEY_SHOW_AGENT_OVERLAY,
        KEY_SHOW_SHOPPING_OVERLAY,
        KEY_SHOW_APP_OVERLAY,
        KEY_BLOCK_IN_APP_BROWSER,
        KEY_ADJUSTMENT_TARGET,
    )

    data class OverlaySettings(
        val enabled: Boolean,
        val opacityPercent: Int,
        val backgroundColor: Int,
        val showFriendsOverlay: Boolean,
        val showAgentOverlay: Boolean,
        val showShoppingOverlay: Boolean,
        val showAppOverlay: Boolean,
        val blockInAppBrowser: Boolean,
        val adjustmentTarget: String,
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
        migrateLegacyAdjustments(preferences)
        return OverlaySettings(
            enabled = preferences.getBoolean(KEY_FEATURE_ENABLED, false),
            opacityPercent = preferences.getInt(KEY_OPACITY, 100).coerceIn(0, 100),
            backgroundColor = preferences.getInt(KEY_BACKGROUND_COLOR, Color.WHITE),
            showFriendsOverlay = preferences.getBoolean(KEY_SHOW_FRIENDS_OVERLAY, true),
            showAgentOverlay = preferences.getBoolean(KEY_SHOW_AGENT_OVERLAY, true),
            showShoppingOverlay = preferences.getBoolean(KEY_SHOW_SHOPPING_OVERLAY, true),
            showAppOverlay = preferences.getBoolean(KEY_SHOW_APP_OVERLAY, true),
            blockInAppBrowser = preferences.getBoolean(KEY_BLOCK_IN_APP_BROWSER, true),
            adjustmentTarget = preferences.getString(KEY_ADJUSTMENT_TARGET, TARGET_HOME_FEED)
                ?: TARGET_HOME_FEED,
        )
    }

    fun setFeatureEnabled(context: Context, enabled: Boolean) =
        preferences(context).edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply()

    fun setOpacityPercent(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_OPACITY, value.coerceIn(0, 100)).apply()

    fun setBackgroundColor(context: Context, value: Int) =
        preferences(context).edit().putInt(KEY_BACKGROUND_COLOR, value).apply()

    fun setShowFriendsOverlay(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_SHOW_FRIENDS_OVERLAY, value).apply()

    fun setShowAgentOverlay(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_SHOW_AGENT_OVERLAY, value).apply()

    fun setShowShoppingOverlay(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_SHOW_SHOPPING_OVERLAY, value).apply()

    fun setShowAppOverlay(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_SHOW_APP_OVERLAY, value).apply()

    fun setBlockInAppBrowser(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_BLOCK_IN_APP_BROWSER, value).apply()

    fun setAdjustmentTarget(context: Context, value: String) =
        preferences(context).edit().putString(KEY_ADJUSTMENT_TARGET, value).apply()

    fun overlayAdjustment(context: Context, name: String): OverlayAdjustment {
        val stored = preferences(context)
        val canonicalName = canonicalAdjustmentName(name)
        val serialized = stored.getString(ADJUSTMENT_PREFIX + canonicalName, null)
            ?: stored.getString(ADJUSTMENT_PREFIX + name, null)
        val parts = serialized
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty()
        return if (parts.size == 4) {
            OverlayAdjustment(parts[0], parts[1], parts[2], parts[3])
        } else {
            OverlayAdjustment()
        }
    }

    fun setOverlayAdjustment(context: Context, name: String, value: OverlayAdjustment) =
        preferences(context).edit().putString(
            ADJUSTMENT_PREFIX + canonicalAdjustmentName(name),
            "${value.offsetXDp},${value.offsetYDp},${value.widthDeltaDp},${value.heightDeltaDp}",
        ).apply()

    fun clearOverlayAdjustment(context: Context, name: String) =
        preferences(context).edit().remove(ADJUSTMENT_PREFIX + canonicalAdjustmentName(name)).apply()

    fun resetOverlayAdjustments(context: Context) {
        val stored = preferences(context)
        stored.edit().apply {
            stored.all.keys.filter { it.startsWith(ADJUSTMENT_PREFIX) }.forEach(::remove)
        }.apply()
    }

    fun settingsSnapshotJson(context: Context): String {
        val stored = preferences(context)
        migrateLegacyAdjustments(stored)
        val values = JSONObject()
        stored.all.forEach { (key, value) ->
            if (isExportableKey(key)) values.put(key, value)
        }
        return JSONObject()
            .put("formatVersion", 1)
            .put("settings", values)
            .toString(2)
    }

    fun applySettingsSnapshotJson(context: Context, json: String): Boolean = runCatching {
        val root = JSONObject(json)
        require(root.getInt("formatVersion") == 1)
        val values = root.getJSONObject("settings")
        val stored = preferences(context)
        val editor = stored.edit()
        exportableKeys.forEach(editor::remove)
        stored.all.keys.filter { it.startsWith(ADJUSTMENT_PREFIX) }.forEach(editor::remove)
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!isExportableKey(key)) continue
            val value = values.get(key)
            when {
                key.startsWith(ADJUSTMENT_PREFIX) -> {
                    val name = canonicalAdjustmentName(key.removePrefix(ADJUSTMENT_PREFIX))
                    val serialized = value as? String
                    require(parseAdjustment(serialized) != null)
                    editor.putString(ADJUSTMENT_PREFIX + name, serialized)
                }
                key == KEY_ADJUSTMENT_TARGET -> {
                    val target = value as? String
                    require(target in adjustmentTargets)
                    editor.putString(key, target)
                }
                value is Boolean -> editor.putBoolean(key, value)
                value is Int -> editor.putInt(key, value)
                else -> error("Unsupported setting type for $key")
            }
        }
        editor.putBoolean(KEY_NUMERIC_ADJUSTMENT_MIGRATED, true)
        editor.commit()
    }.getOrDefault(false)

    fun saveSettingsSnapshot(context: Context) {
        preferences(context).edit()
            .putString(KEY_SAVED_SETTINGS, settingsSnapshotJson(context))
            .apply()
    }

    fun hasSavedSettingsSnapshot(context: Context): Boolean =
        preferences(context).contains(KEY_SAVED_SETTINGS)

    fun loadSavedSettingsSnapshot(context: Context): Boolean =
        preferences(context).getString(KEY_SAVED_SETTINGS, null)
            ?.let { applySettingsSnapshotJson(context, it) }
            ?: false

    fun clearSavedSettingsSnapshot(context: Context) {
        preferences(context).edit().remove(KEY_SAVED_SETTINGS).apply()
    }

    fun isOperationalKey(key: String?): Boolean =
        key in operationalKeys || key?.startsWith(ADJUSTMENT_PREFIX) == true

    fun areDebugOptionsVisible(context: Context): Boolean =
        preferences(context).getBoolean(KEY_DEBUG_OPTIONS_VISIBLE, false)

    fun setDebugOptionsVisible(context: Context, value: Boolean) =
        preferences(context).edit().putBoolean(KEY_DEBUG_OPTIONS_VISIBLE, value).apply()

    fun resetOperationalSettings(context: Context) {
        val stored = preferences(context)
        stored.edit().apply {
            operationalKeys.forEach(::remove)
            stored.all.keys.filter { it.startsWith(ADJUSTMENT_PREFIX) }.forEach(::remove)
        }.apply()
    }

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun migrateLegacyAdjustments(stored: SharedPreferences) {
        if (stored.getBoolean(KEY_NUMERIC_ADJUSTMENT_MIGRATED, false)) return
        val top = stored.getInt(KEY_TOP_OFFSET_DP, 0)
        val bottom = stored.getInt(KEY_BOTTOM_OFFSET_DP, 0)
        val inset = stored.getInt(KEY_HORIZONTAL_INSET_DP, 0)
        val home = parseAdjustment(stored.getString(ADJUSTMENT_PREFIX + TARGET_HOME_FEED, null))
            ?: OverlayAdjustment()
        val editor = stored.edit()
        if (top != 0 || bottom != 0 || inset != 0) {
            val migrated = home.copy(
                offsetXDp = home.offsetXDp + inset,
                offsetYDp = home.offsetYDp + top,
                widthDeltaDp = home.widthDeltaDp - (inset * 2),
                heightDeltaDp = home.heightDeltaDp + bottom - top,
            )
            editor.putString(ADJUSTMENT_PREFIX + TARGET_HOME_FEED, serializeAdjustment(migrated))
        }
        migrateLegacyTarget(stored, editor, "friends-header", TARGET_FRIENDS)
        listOf("agent-header", "agent-floating", "agent-conversation")
            .firstOrNull { stored.contains(ADJUSTMENT_PREFIX + it) }
            ?.let { migrateLegacyTarget(stored, editor, it, TARGET_AGENT) }
        editor
            .remove(KEY_TOP_OFFSET_DP)
            .remove(KEY_BOTTOM_OFFSET_DP)
            .remove(KEY_HORIZONTAL_INSET_DP)
            .remove("adjustment_mode")
            .putBoolean(KEY_NUMERIC_ADJUSTMENT_MIGRATED, true)
            .commit()
    }

    private fun migrateLegacyTarget(
        stored: SharedPreferences,
        editor: SharedPreferences.Editor,
        legacyName: String,
        targetName: String,
    ) {
        val targetKey = ADJUSTMENT_PREFIX + targetName
        val legacyKey = ADJUSTMENT_PREFIX + legacyName
        if (!stored.contains(targetKey)) {
            stored.getString(legacyKey, null)?.let { editor.putString(targetKey, it) }
        }
    }

    private fun parseAdjustment(serialized: String?): OverlayAdjustment? {
        val parts = serialized?.split(',')?.mapNotNull(String::toIntOrNull).orEmpty()
        return if (parts.size == 4) {
            OverlayAdjustment(parts[0], parts[1], parts[2], parts[3])
        } else {
            null
        }
    }

    private fun serializeAdjustment(value: OverlayAdjustment): String =
        "${value.offsetXDp},${value.offsetYDp},${value.widthDeltaDp},${value.heightDeltaDp}"

    private fun canonicalAdjustmentName(name: String): String = when (name) {
        "friends-header" -> TARGET_FRIENDS
        "agent-header", "agent-floating", "agent-conversation" -> TARGET_AGENT
        else -> name
    }

    private fun isExportableKey(key: String): Boolean =
        key in exportableKeys || key.startsWith(ADJUSTMENT_PREFIX)

    private val exportableKeys: Set<String>
        get() = operationalKeys + KEY_DEBUG_OPTIONS_VISIBLE + KEY_INSPECTOR_ENABLED

    const val TARGET_HOME_FEED = "home-content"
    const val TARGET_BLOCKED_TAB = "blocked-tab"
    const val TARGET_FRIENDS = "friends"
    const val TARGET_AGENT = "agent"
    const val TARGET_SHOPPING = "shopping-tab"
    const val TARGET_APP = "mini-app-tab"
    const val TARGET_TALK_PROMOTED = "talk-promoted"

    val adjustmentTargets = setOf(
        TARGET_HOME_FEED,
        TARGET_BLOCKED_TAB,
        TARGET_FRIENDS,
        TARGET_AGENT,
        TARGET_SHOPPING,
        TARGET_APP,
        TARGET_TALK_PROMOTED,
    )
}
