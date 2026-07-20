package dev.haman.linefeedblocker

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class IntBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class UiNodeSnapshot(
    val className: String,
    val viewId: String?,
    val bounds: IntBounds,
    val selected: Boolean,
    val scrollable: Boolean,
    val clickable: Boolean,
    val children: List<UiNodeSnapshot>,
    val text: String? = null,
    val contentDescription: String? = null,
) {
    fun descendantsAndSelf(): Sequence<UiNodeSnapshot> = sequence {
        yield(this@UiNodeSnapshot)
        children.forEach { yieldAll(it.descendantsAndSelf()) }
    }

    fun containsSelectedDescendant(): Boolean =
        descendantsAndSelf().any { it.selected }

    companion object {
        fun from(node: AccessibilityNodeInfo): UiNodeSnapshot {
            val rect = Rect().also(node::getBoundsInScreen)
            val children = buildList {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { add(from(it)) }
                }
            }
            return UiNodeSnapshot(
                className = node.className?.toString().orEmpty(),
                viewId = node.viewIdResourceName,
                bounds = IntBounds(rect.left, rect.top, rect.right, rect.bottom),
                selected = node.isSelected,
                scrollable = node.isScrollable,
                clickable = node.isClickable,
                children = children,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
            )
        }
    }
}

data class NavigationPolicy(
    val navigationTop: Int?,
    val blockedTabSelected: Boolean,
    val touchBlockers: Map<String, IntBounds>,
)

object NavigationPolicyDetector {
    private const val APP_ROOT_ID = "jp.naver.line.android:id/app_main_root"
    private const val NAV_CONTAINER_ID = "jp.naver.line.android:id/main_tab_container"
    private const val SHOPPING_ID = "jp.naver.line.android:id/bnb_commerce"
    private const val MINI_APP_ID = "jp.naver.line.android:id/bnb_mini"
    private const val TALK_ID = "jp.naver.line.android:id/bnb_chat"

    fun detect(root: UiNodeSnapshot, screen: IntBounds): NavigationPolicy {
        val nodes = root.descendantsAndSelf().toList()
        if (nodes.none { it.viewId == APP_ROOT_ID }) {
            return NavigationPolicy(null, false, emptyMap())
        }
        val navigation = nodes.firstOrNull { it.viewId == NAV_CONTAINER_ID }
            ?: return NavigationPolicy(null, false, emptyMap())
        val shopping = nodes.firstOrNull { it.viewId == SHOPPING_ID }
        val miniApp = nodes.firstOrNull { it.viewId == MINI_APP_ID }
        val talk = nodes.firstOrNull { it.viewId == TALK_ID }
        val talkSelected = talk?.containsSelectedDescendant() == true
        val talkSearchBar = if (talkSelected) {
            nodes.asSequence()
                .filter { it.clickable && it.className == "android.view.View" }
                .filter { it.bounds.width >= screen.width * 0.70 }
                .filter { it.bounds.top in (screen.top + (screen.height * 0.08).toInt())..(screen.top + (screen.height * 0.16).toInt()) }
                .filter { it.bounds.height <= screen.height * 0.08 }
                .maxByOrNull { it.bounds.width }
        } else {
            null
        }
        val blockers = linkedMapOf<String, IntBounds>()
        shopping?.let { blockers["shopping-tab"] = it.bounds }
        miniApp?.let { blockers["mini-app-tab"] = it.bounds }

        val headerAgent = nodes.asSequence()
            .filter { it.clickable && it.className == "android.view.View" }
            .filter { it.bounds.left in (screen.left + (screen.width * 0.55).toInt())..(screen.left + (screen.width * 0.75).toInt()) }
            .filter { it.bounds.top in (screen.top + (screen.height * 0.05).toInt())..(screen.top + (screen.height * 0.13).toInt()) }
            .filter { it.bounds.width in (screen.width * 0.06).toInt()..(screen.width * 0.15).toInt() }
            .minByOrNull { it.bounds.left }
        headerAgent?.let {
            blockers["agent-header"] = centeredSquare(it.bounds, (screen.width * 0.075).toInt(), screen)
        }

        val floatingAgent = talkSearchBar?.let { searchBar ->
            nodes.asSequence()
                .filter { it.clickable && it.className == "android.view.View" }
                .filter { it.bounds.left >= searchBar.bounds.right }
                .filter { kotlin.math.abs(it.bounds.top - searchBar.bounds.top) <= 2 }
                .filter { kotlin.math.abs(it.bounds.bottom - searchBar.bounds.bottom) <= 2 }
                .minByOrNull { it.bounds.left }
        }
        floatingAgent?.let {
            blockers["agent-floating"] = centeredSquare(it.bounds, (screen.width * 0.086).toInt(), screen)
        }

        val agentConversation = nodes.asSequence()
            .filter { it.clickable && it.bounds.width >= screen.width * 0.90 }
            .filter { it.bounds.height in (screen.height * 0.04).toInt()..(screen.height * 0.12).toInt() }
            .filter { candidate ->
                candidate.descendantsAndSelf().any {
                    it.text?.trim() == "Agent i" || it.contentDescription?.trim() == "Agent i"
                }
            }
            .firstOrNull()
        agentConversation?.let { blockers["agent-conversation"] = it.bounds }

        if (talkSelected) {
            val searchBar = talkSearchBar
            val talkList = nodes.asSequence()
                .filter { it.scrollable && it.className == "android.view.View" }
                .filter { it.bounds.width >= screen.width * 0.90 }
                .filter { it.bounds.top >= (searchBar?.bounds?.bottom ?: 0) }
                .filter { it.bounds.top < navigation.bounds.top }
                .minByOrNull { it.bounds.top }
            if (searchBar != null && talkList != null &&
                talkList.bounds.top - searchBar.bounds.bottom >= screen.height * 0.05
            ) {
                blockers["talk-promoted"] = IntBounds(
                    screen.left,
                    searchBar.bounds.bottom,
                    screen.right,
                    talkList.bounds.top,
                )
            }
        }

        return NavigationPolicy(
            navigationTop = navigation.bounds.top,
            blockedTabSelected = listOfNotNull(shopping, miniApp).any(UiNodeSnapshot::containsSelectedDescendant),
            touchBlockers = blockers,
        )
    }

    private fun centeredSquare(bounds: IntBounds, requestedSize: Int, screen: IntBounds): IntBounds {
        val size = requestedSize.coerceAtMost(minOf(bounds.width, bounds.height))
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        val left = (centerX - size / 2).coerceIn(screen.left, screen.right - size)
        val top = (centerY - size / 2).coerceIn(screen.top, screen.bottom - size)
        return IntBounds(left, top, left + size, top + size)
    }
}

data class FeedDetection(
    val overlayBounds: IntBounds?,
    val confidence: Int,
    val reason: String,
) {
    val shouldShow: Boolean get() = overlayBounds != null
}

object FeedDetector {
    private const val HOME_CONTAINER_ID = "jp.naver.line.android:id/bnb_home26"
    private const val VIEW_PAGER_ID = "jp.naver.line.android:id/viewpager"
    private const val NAV_CONTAINER_ID = "jp.naver.line.android:id/main_tab_container"
    fun detect(root: UiNodeSnapshot, screen: IntBounds): FeedDetection {
        val nodes = root.descendantsAndSelf().toList()
        val homeContainer = nodes.firstOrNull { it.viewId == HOME_CONTAINER_ID }
            ?: return hidden("home-container-missing")
        if (!homeContainer.containsSelectedDescendant()) {
            return hidden("home-tab-not-selected")
        }

        if (nodes.none { it.viewId == VIEW_PAGER_ID }) {
            return hidden("view-pager-missing")
        }

        val navigation = nodes.firstOrNull { it.viewId == NAV_CONTAINER_ID }
            ?: return hidden("navigation-missing")
        if (navigation.bounds.top <= screen.top || navigation.bounds.top >= screen.bottom) {
            return hidden("navigation-bounds-invalid")
        }

        val verticalScroll = nodes
            .asSequence()
            .filter { it.scrollable && it.className == "android.view.View" }
            .filter { it.bounds.width >= screen.width * 0.90 }
            .filter { it.bounds.top in (screen.top + (screen.height * 0.07).toInt())..(screen.top + (screen.height * 0.35).toInt()) }
            .filter { it.bounds.bottom >= navigation.bounds.top }
            .minByOrNull { it.bounds.top }
            ?: return hidden("home-vertical-scroll-missing")

        val overlayTop = verticalScroll.bounds.top
        val overlayBottom = navigation.bounds.top
        if (overlayBottom - overlayTop < screen.height * 0.10) {
            return hidden("feed-region-too-small")
        }

        return FeedDetection(
            overlayBounds = IntBounds(screen.left, overlayTop, screen.right, overlayBottom),
            confidence = 100,
            reason = "home-selected+stable-ids+full-home-content-region",
        )
    }

    private fun hidden(reason: String) = FeedDetection(null, 0, reason)
}
