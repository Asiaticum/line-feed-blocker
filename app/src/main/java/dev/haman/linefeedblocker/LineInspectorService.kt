package dev.haman.linefeedblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File

class LineInspectorService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlayController: OverlayController
    private lateinit var touchBlockerController: TouchBlockerController
    private var lastForegroundPackage: String? = null
    private var lastHiddenReason: String? = null
    private var evaluationRunnable: Runnable? = null
    private var pendingEvaluationReason: String? = null
    private var lastEvaluationUptimeMs = 0L
    private var lastKnownHomeBounds: IntBounds? = null
    private var dumpRunnable: Runnable? = null
    private var scrollSettleRunnable: Runnable? = null

    private val watchdog = object : Runnable {
        override fun run() {
            try {
                if (InspectorPreferences.overlaySettings(this@LineInspectorService).enabled) {
                    scheduleEvaluation("watchdog")
                } else {
                    hideOverlay("feature-disabled")
                }
            } finally {
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = OverlayController(this)
        touchBlockerController = TouchBlockerController(this)
        InspectorPreferences.preferences(this).registerOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        Log.i(TAG, "service_connected target=$TARGET_PACKAGE")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) {
                hideOverlay("null-event")
                return
            }
            val packageName = event.packageName?.toString()

            if (packageName != null && packageName != lastForegroundPackage &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                lastForegroundPackage = packageName
                Log.i(TAG, "foreground_package=$packageName")
            }

            if (packageName != TARGET_PACKAGE) {
                cancelPendingDump()
                val foregroundPackage = foregroundApplicationPackage()
                if (foregroundPackage == TARGET_PACKAGE) {
                    scheduleEvaluation("foreign-event-with-line-window:${packageName ?: "unknown"}")
                } else {
                    cancelEvaluation()
                    hideOverlay(
                        if (foregroundPackage == null) "foreground-application-unknown"
                        else "outside-line:$foregroundPackage",
                    )
                }
                return
            }

            handleInspectorRequest(event.eventType)
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                handleFastTabClick(event)
            }
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (::touchBlockerController.isInitialized) {
                    touchBlockerController.hide("talk-promoted")
                }
                scheduleScrollSettleEvaluation()
            } else {
                scheduleEvaluation("event:${AccessibilityEvent.eventTypeToString(event.eventType)}")
            }
        } catch (error: Throwable) {
            cancelEvaluation()
            cancelPendingDump()
            hideOverlay("event-exception")
            Log.e(TAG, "event_processing_failed", error)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        hideOverlay("configuration-changed")
        scheduleEvaluation("configuration-changed")
        super.onConfigurationChanged(newConfig)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!InspectorPreferences.isOperationalKey(key)) return
        if (!InspectorPreferences.overlaySettings(this).enabled) {
            hideOverlay("feature-disabled")
        } else {
            scheduleEvaluation("settings-changed")
        }
    }

    override fun onInterrupt() {
        stopServiceWork("service-interrupted")
    }

    override fun onDestroy() {
        InspectorPreferences.preferences(this).unregisterOnSharedPreferenceChangeListener(this)
        stopServiceWork("service-destroyed")
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopServiceWork("service-unbound")
        return super.onUnbind(intent)
    }

    private fun scheduleEvaluation(reason: String) {
        pendingEvaluationReason = reason
        if (evaluationRunnable != null) return
        val elapsed = SystemClock.uptimeMillis() - lastEvaluationUptimeMs
        val delay = (MIN_EVALUATION_INTERVAL_MS - elapsed).coerceAtLeast(0L)
        evaluationRunnable = Runnable {
            evaluationRunnable = null
            val trigger = pendingEvaluationReason ?: reason
            pendingEvaluationReason = null
            lastEvaluationUptimeMs = SystemClock.uptimeMillis()
            evaluateNow(trigger)
        }
        if (delay == 0L) {
            handler.post(evaluationRunnable!!)
        } else {
            handler.postDelayed(evaluationRunnable!!, delay)
        }
    }

    private fun evaluateNow(trigger: String) {
        try {
            val settings = InspectorPreferences.overlaySettings(this)
            if (!settings.enabled) {
                hideOverlay("feature-disabled")
                return
            }
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                hideOverlay("screen-locked")
                return
            }

            val root = findLineApplicationRoot()
            if (root == null) {
                val foregroundPackage = foregroundApplicationPackage()
                hideOverlay(
                    if (foregroundPackage == TARGET_PACKAGE) "line-root-missing"
                    else "outside-line:${foregroundPackage ?: "unknown"}",
                )
                return
            }

            val screen = currentScreenBounds()
            val snapshot = UiNodeSnapshot.from(root)
            val policy = NavigationPolicyDetector.detect(snapshot, screen)
            val enabledTargets = policy.touchBlockers.filterKeys { isTargetEnabled(it, settings) }
            touchBlockerController.sync(enabledTargets, settings)
            val detection = FeedDetector.detect(snapshot, screen)
            val rawBounds = when {
                detection.overlayBounds != null -> detection.overlayBounds
                policy.selectedRestrictedTab?.let { isTargetEnabled(it, settings) } == true &&
                    policy.navigationTop != null ->
                    IntBounds(screen.left, screen.top, screen.right, policy.navigationTop)
                else -> null
            }
            if (rawBounds == null) {
                hideContentOverlay(
                    if (enabledTargets.isEmpty()) detection.reason
                    else "restricted-icons=${enabledTargets.keys.joinToString()}",
                )
                return
            }

            if (detection.overlayBounds != null) {
                lastKnownHomeBounds = detection.overlayBounds
            }

            val density = resources.displayMetrics.density
            val adjusted = if (detection.overlayBounds != null) {
                IntBounds(
                    left = rawBounds.left,
                    top = rawBounds.top,
                    right = rawBounds.right,
                    bottom = (
                        rawBounds.bottom +
                            (BASE_BOTTOM_EXTENSION_DP * density).toInt()
                        ).coerceIn(rawBounds.top, screen.bottom),
                )
            } else {
                rawBounds
            }
            if (adjusted.width <= 0 || adjusted.height <= 0) {
                hideOverlay("adjusted-bounds-invalid")
                return
            }

            val mode = if (detection.overlayBounds != null) "home-content" else "blocked-tab"
            overlayController.show(adjusted, settings, mode)
            lastHiddenReason = null
            val status = "表示中 mode=$mode bounds=[${adjusted.left},${adjusted.top}][${adjusted.right},${adjusted.bottom}] trigger=$trigger"
            Log.i(TAG, "overlay_show $status")
        } catch (error: Throwable) {
            hideOverlay("evaluation-exception")
            Log.e(TAG, "evaluation_failed trigger=$trigger", error)
        }
    }

    private fun currentScreenBounds(): IntBounds {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            IntBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        } else {
            val metrics = resources.displayMetrics
            IntBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun scheduleScrollSettleEvaluation() {
        scrollSettleRunnable?.let(handler::removeCallbacks)
        scrollSettleRunnable = Runnable {
            scrollSettleRunnable = null
            scheduleEvaluation("scroll-settled")
        }
        handler.postDelayed(scrollSettleRunnable!!, SCROLL_SETTLE_DELAY_MS)
    }

    private fun isTargetEnabled(name: String, settings: InspectorPreferences.OverlaySettings): Boolean =
        when (name) {
            "friends-header" -> settings.showFriendsOverlay
            "agent-header", "agent-floating", "agent-conversation" -> settings.showAgentOverlay
            "shopping-tab" -> settings.showShoppingOverlay
            "mini-app-tab" -> settings.showAppOverlay
            else -> true
        }

    private fun handleFastTabClick(event: AccessibilityEvent) {
        val eventLabels = buildList {
            event.contentDescription?.toString()?.trim()?.let(::add)
            event.text.mapTo(this) { it.toString().trim() }
        }
        var homeTab = eventLabels.any { it == HOME_TAB_DESCRIPTION }
        var otherTab = eventLabels.any { it.endsWith(TAB_DESCRIPTION_SUFFIX) && it != HOME_TAB_DESCRIPTION }
        var node: AccessibilityNodeInfo? = event.source
        repeat(MAX_TAB_ANCESTOR_DEPTH) {
            val current = node ?: return@repeat
            val id = current.viewIdResourceName.orEmpty()
            val description = current.contentDescription?.toString()?.trim().orEmpty()
            if (id == HOME_TAB_CONTAINER_ID || description == HOME_TAB_DESCRIPTION) {
                homeTab = true
                return@repeat
            }
            if (description.endsWith(TAB_DESCRIPTION_SUFFIX) || id in NON_HOME_TAB_CONTAINER_IDS) {
                otherTab = true
            }
            node = current.parent
        }

        if (homeTab) {
            showCachedHomeOverlay()
        } else if (otherTab) {
            hideContentOverlay("fast-non-home-tab-click")
        }
    }

    private fun showCachedHomeOverlay() {
        val rawBounds = lastKnownHomeBounds ?: return
        val settings = InspectorPreferences.overlaySettings(this)
        if (!settings.enabled) return
        val screen = currentScreenBounds()
        val density = resources.displayMetrics.density
        val adjusted = IntBounds(
            left = rawBounds.left,
            top = rawBounds.top,
            right = rawBounds.right,
            bottom = (
                rawBounds.bottom +
                    (BASE_BOTTOM_EXTENSION_DP * density).toInt()
                ).coerceIn(rawBounds.top, screen.bottom),
        )
        if (adjusted.width <= 0 || adjusted.height <= 0) return
        overlayController.show(adjusted, settings, "home-content")
        lastHiddenReason = null
        Log.i(TAG, "overlay_show fast-home-tab-click bounds=$adjusted")
    }

    /**
     * Accessibility overlays can become rootInActiveWindow themselves. Select the focused
     * application window explicitly so our own overlay events cannot make us hide the overlay.
     */
    private fun foregroundApplicationPackage(): String? {
        val applicationWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val foregroundWindow = applicationWindows.firstOrNull { it.isFocused }
            ?: applicationWindows.firstOrNull { it.isActive }
            ?: applicationWindows.maxByOrNull { it.layer }
        return foregroundWindow?.root?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
    }

    private fun findLineApplicationRoot(): AccessibilityNodeInfo? {
        if (foregroundApplicationPackage() != TARGET_PACKAGE) return null
        val lineWindows = windows.filter { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.root?.packageName?.toString() == TARGET_PACKAGE
        }
        return (lineWindows.firstOrNull { it.isFocused }
            ?: lineWindows.firstOrNull { it.isActive }
            ?: lineWindows.maxByOrNull { it.layer })?.root
            ?: rootInActiveWindow?.takeIf { it.packageName?.toString() == TARGET_PACKAGE }
    }

    private fun hideOverlay(reason: String) {
        val wasShowing = ::overlayController.isInitialized && overlayController.isShowing
        if (::overlayController.isInitialized) overlayController.hide()
        if (::touchBlockerController.isInitialized) touchBlockerController.hideAll()
        if (wasShowing || reason != lastHiddenReason) {
            Log.i(TAG, "overlay_hide reason=$reason")
        }
        lastHiddenReason = reason
    }

    private fun hideContentOverlay(reason: String) {
        val wasShowing = ::overlayController.isInitialized && overlayController.isShowing
        if (::overlayController.isInitialized) overlayController.conceal()
        if (wasShowing || reason != lastHiddenReason) {
            Log.i(TAG, "content_overlay_hide reason=$reason")
        }
        lastHiddenReason = reason
    }

    private fun cancelEvaluation() {
        evaluationRunnable?.let(handler::removeCallbacks)
        scrollSettleRunnable?.let(handler::removeCallbacks)
        evaluationRunnable = null
        scrollSettleRunnable = null
        pendingEvaluationReason = null
    }

    private fun stopServiceWork(reason: String) {
        cancelEvaluation()
        cancelPendingDump()
        handler.removeCallbacks(watchdog)
        hideOverlay(reason)
        Log.i(TAG, reason)
    }

    private fun handleInspectorRequest(eventType: Int) {
        if (!InspectorPreferences.isInspectorEnabled(this)) return
        val request = InspectorPreferences.dumpRequest(this)
        if (request == 0L || request <= InspectorPreferences.handledDumpRequest(this)) return
        scheduleDump(request, eventType)
    }

    private fun scheduleDump(request: Long, eventType: Int) {
        cancelPendingDump()
        dumpRunnable = Runnable {
            dumpRunnable = null
            val root = findLineApplicationRoot()
            if (root == null) {
                Log.w(TAG, "dump_skipped reason=no_line_root request=$request")
                return@Runnable
            }

            val output = StringBuilder()
            Log.i(TAG, "dump_begin request=$request event=${AccessibilityEvent.eventTypeToString(eventType)} package=${root.packageName}")
            dumpNode(root, depth = 0, siblingIndex = 0, output = output)
            val directory = File(filesDir, "ui-trees").apply { mkdirs() }
            val file = File(directory, "ui-tree-latest.txt")
            file.writeText(output.toString())
            InspectorPreferences.markDumpHandled(this, request)
            Log.i(TAG, "dump_end request=$request file=${file.absolutePath}")
        }
        handler.postDelayed(dumpRunnable!!, DUMP_DEBOUNCE_MS)
    }

    private fun cancelPendingDump() {
        dumpRunnable?.let(handler::removeCallbacks)
        dumpRunnable = null
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, siblingIndex: Int, output: StringBuilder) {
        if (depth > MAX_DEPTH) {
            val line = "${indent(depth)}[$siblingIndex] depth_limit"
            output.appendLine(line)
            Log.w(TAG, line)
            return
        }

        val bounds = Rect().also(node::getBoundsInScreen)
        val line = buildString {
            append(indent(depth)).append('[').append(siblingIndex).append("] ")
            append("class=").append(safe(node.className))
            append(" text=").append(quoted(node.text))
            append(" desc=").append(quoted(node.contentDescription))
            append(" id=").append(safe(node.viewIdResourceName))
            append(" bounds=[").append(bounds.left).append(',').append(bounds.top)
            append("][").append(bounds.right).append(',').append(bounds.bottom).append(']')
            append(" visible=").append(node.isVisibleToUser)
            append(" selected=").append(node.isSelected)
            append(" scrollable=").append(node.isScrollable)
            append(" clickable=").append(node.isClickable)
            append(" enabled=").append(node.isEnabled)
            append(" children=").append(node.childCount)
        }
        output.appendLine(line)
        Log.i(TAG, line)

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { dumpNode(it, depth + 1, index, output) }
        }
    }

    private fun quoted(value: CharSequence?): String =
        "\"${safe(value).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"")}\""

    private fun safe(value: CharSequence?): String {
        val text = value?.toString() ?: "null"
        return if (text.length <= MAX_VALUE_LENGTH) text else text.take(MAX_VALUE_LENGTH) + "…"
    }

    private fun indent(depth: Int): String = "  ".repeat(depth)

    companion object {
        private const val TAG = "LineFeedInspector"
        private const val TARGET_PACKAGE = "jp.naver.line.android"
        private const val BASE_BOTTOM_EXTENSION_DP = 30
        private const val HOME_TAB_CONTAINER_ID = "jp.naver.line.android:id/bnb_home26"
        private const val HOME_TAB_DESCRIPTION = "ホームタブ"
        private const val TAB_DESCRIPTION_SUFFIX = "タブ"
        private const val MAX_TAB_ANCESTOR_DEPTH = 6
        private val NON_HOME_TAB_CONTAINER_IDS = setOf(
            "jp.naver.line.android:id/bnb_chat",
            "jp.naver.line.android:id/bnb_commerce",
            "jp.naver.line.android:id/bnb_call",
            "jp.naver.line.android:id/bnb_mini",
        )
        private const val MIN_EVALUATION_INTERVAL_MS = 16L
        private const val SCROLL_SETTLE_DELAY_MS = 300L
        private const val DUMP_DEBOUNCE_MS = 350L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val MAX_DEPTH = 80
        private const val MAX_VALUE_LENGTH = 160
    }
}
