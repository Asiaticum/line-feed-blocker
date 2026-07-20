package dev.haman.linefeedblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDetectorTest {
    private val screen = IntBounds(0, 0, 1260, 2800)

    @Test
    fun coversEntireMeasuredHomeContentAtTop() {
        val detection = FeedDetector.detect(homeTree(feedTop = 1350, feedBottom = 2581), screen)

        assertTrue(detection.shouldShow)
        assertEquals(IntBounds(0, 324, 1260, 2473), detection.overlayBounds)
        assertEquals(100, detection.confidence)
    }

    @Test
    fun keepsSameBoundsAfterHomeScroll() {
        val detection = FeedDetector.detect(homeTree(feedTop = 341, feedBottom = 1947), screen)

        assertTrue(detection.shouldShow)
        assertEquals(IntBounds(0, 324, 1260, 2473), detection.overlayBounds)
    }

    @Test
    fun rejectsTalkTabEvenWhenHomeContainerExists() {
        val detection = FeedDetector.detect(homeTree(feedTop = 1350, feedBottom = 2581, homeSelected = false), screen)

        assertFalse(detection.shouldShow)
        assertEquals("home-tab-not-selected", detection.reason)
    }

    @Test
    fun rejectsChatRoomWithoutMainNavigation() {
        val chatRoot = node(
            id = "android:id/content",
            bounds = screen,
            children = listOf(
                node(
                    id = "jp.naver.line.android:id/chathistory_message_list",
                    bounds = IntBounds(0, 324, 1260, 2575),
                    scrollable = true,
                ),
            ),
        )

        val detection = FeedDetector.detect(chatRoot, screen)

        assertFalse(detection.shouldShow)
        assertEquals("home-container-missing", detection.reason)
    }

    @Test
    fun noLongerDependsOnIndividualFeedCardBounds() {
        val detection = FeedDetector.detect(homeTree(feedTop = 0, feedBottom = 0), screen)

        assertTrue(detection.shouldShow)
        assertEquals(IntBounds(0, 324, 1260, 2473), detection.overlayBounds)
    }

    @Test
    fun blocksMeasuredAgentShoppingAndMiniAppTargets() {
        val policy = NavigationPolicyDetector.detect(navigationTree(shoppingSelected = false), screen)

        assertEquals(2473, policy.navigationTop)
        assertFalse(policy.blockedTabSelected)
        assertEquals(IntBounds(534, 2473, 726, 2728), policy.touchBlockers["shopping-tab"])
        assertEquals(IntBounds(1038, 2473, 1230, 2728), policy.touchBlockers["mini-app-tab"])
        assertEquals(IntBounds(775, 196, 869, 290), policy.touchBlockers["agent-header"])
        assertEquals(IntBounds(1107, 319, 1215, 427), policy.touchBlockers["agent-floating"])
        assertEquals(IntBounds(0, 963, 1260, 1167), policy.touchBlockers["agent-conversation"])
        assertEquals(IntBounds(0, 438, 1260, 774), policy.touchBlockers["talk-promoted"])
    }

    @Test
    fun marksBlockedTabWhenShoppingIsAlreadySelected() {
        val policy = NavigationPolicyDetector.detect(navigationTree(shoppingSelected = true), screen)

        assertTrue(policy.blockedTabSelected)
    }

    private fun homeTree(
        feedTop: Int,
        feedBottom: Int,
        homeSelected: Boolean = true,
    ) = node(
        bounds = screen,
        children = listOf(
            node(
                className = "androidx.viewpager.widget.ViewPager",
                id = "jp.naver.line.android:id/viewpager",
                bounds = IntBounds(0, 0, 1260, 2653),
                scrollable = true,
                children = listOf(
                    node(
                        bounds = IntBounds(0, 324, 1260, 2581),
                        scrollable = true,
                        children = listOf(
                            node(bounds = IntBounds(48, feedTop, 1212, feedBottom)),
                        ),
                    ),
                ),
            ),
            node(
                id = "jp.naver.line.android:id/main_tab_container",
                bounds = IntBounds(0, 2473, 1260, 2728),
                children = listOf(
                    node(
                        id = "jp.naver.line.android:id/bnb_home26",
                        bounds = IntBounds(30, 2473, 222, 2728),
                        children = listOf(
                            node(
                                id = "jp.naver.line.android:id/bnb_button_clickable_area",
                                bounds = IntBounds(30, 2560, 222, 2728),
                                selected = homeSelected,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun navigationTree(shoppingSelected: Boolean) = node(
        bounds = screen,
        children = listOf(
            node(
                id = "jp.naver.line.android:id/app_main_root",
                bounds = screen,
                children = listOf(
                    node(bounds = IntBounds(765, 171, 879, 315), clickable = true),
                    node(bounds = IntBounds(879, 171, 993, 315), clickable = true),
                    node(bounds = IntBounds(48, 309, 1089, 438), clickable = true),
                    node(bounds = IntBounds(1089, 309, 1233, 438), clickable = true),
                    node(bounds = IntBounds(0, 774, 1260, 2581), scrollable = true),
                    node(
                        bounds = IntBounds(0, 963, 1260, 1167),
                        clickable = true,
                        children = listOf(node(text = "Agent i")),
                    ),
                    node(
                        id = "jp.naver.line.android:id/main_tab_container",
                        bounds = IntBounds(0, 2473, 1260, 2728),
                        children = listOf(
                            node(
                                id = "jp.naver.line.android:id/bnb_chat",
                                bounds = IntBounds(282, 2473, 474, 2728),
                                children = listOf(node(selected = true)),
                            ),
                            node(
                                id = "jp.naver.line.android:id/bnb_commerce",
                                bounds = IntBounds(534, 2473, 726, 2728),
                                children = listOf(node(selected = shoppingSelected)),
                            ),
                            node(
                                id = "jp.naver.line.android:id/bnb_mini",
                                bounds = IntBounds(1038, 2473, 1230, 2728),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun node(
        className: String = "android.view.View",
        id: String? = null,
        bounds: IntBounds = IntBounds(0, 0, 0, 0),
        selected: Boolean = false,
        scrollable: Boolean = false,
        children: List<UiNodeSnapshot> = emptyList(),
        clickable: Boolean = false,
        text: String? = null,
    ) = UiNodeSnapshot(className, id, bounds, selected, scrollable, clickable, children, text)
}
