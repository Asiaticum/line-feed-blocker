package dev.haman.linefeedblocker

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAdjustmentTest {
    private val screen = IntBounds(0, 0, 1000, 2000)

    @Test
    fun appliesRelativePositionAndSizeInDp() {
        val base = IntBounds(100, 200, 300, 400)
        val adjustment = OverlayAdjustment(
            offsetXDp = 10,
            offsetYDp = -20,
            widthDeltaDp = 50,
            heightDeltaDp = -25,
        )

        val result = OverlayAdjustmentMath.apply(base, adjustment, 2f, screen, 80)

        assertEquals(IntBounds(120, 160, 420, 310), result)
    }

    @Test
    fun keepsMinimumSizeWhenAdjustmentShrinksTooFar() {
        val result = OverlayAdjustmentMath.apply(
            base = IntBounds(100, 200, 300, 400),
            adjustment = OverlayAdjustment(widthDeltaDp = -500, heightDeltaDp = -500),
            density = 2f,
            screen = screen,
            minimumSizePx = 96,
        )

        assertEquals(IntBounds(100, 200, 196, 296), result)
    }
}
