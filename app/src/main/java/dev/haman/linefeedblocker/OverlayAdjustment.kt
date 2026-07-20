package dev.haman.linefeedblocker

import kotlin.math.roundToInt

data class OverlayAdjustment(
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
    val widthDeltaDp: Int = 0,
    val heightDeltaDp: Int = 0,
)

object OverlayAdjustmentMath {
    fun apply(
        base: IntBounds,
        adjustment: OverlayAdjustment,
        density: Float,
        screen: IntBounds,
        minimumSizePx: Int,
    ): IntBounds {
        val width = (base.width + adjustment.widthDeltaDp * density).roundToInt()
            .coerceIn(minimumSizePx.coerceAtMost(screen.width), screen.width)
        val height = (base.height + adjustment.heightDeltaDp * density).roundToInt()
            .coerceIn(minimumSizePx.coerceAtMost(screen.height), screen.height)
        val left = (base.left + adjustment.offsetXDp * density).roundToInt()
            .coerceIn(screen.left, screen.right - width)
        val top = (base.top + adjustment.offsetYDp * density).roundToInt()
            .coerceIn(screen.top, screen.bottom - height)
        return IntBounds(left, top, left + width, top + height)
    }

}
