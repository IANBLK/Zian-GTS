package com.zianblk.ziangts.v2.client

/**
 * Pure responsive geometry shared by the market renderer and its hit targets.
 * Minecraft's Screen width/height are already expressed in GUI-scaled pixels,
 * so these breakpoints react correctly to Auto/x2/x3 without reading the user's
 * GUI-scale option directly.
 */
internal data class MarketLayout(
    val columns: Int,
    val cardWidth: Int,
    val cardHeight: Int,
    val gap: Int,
    val left: Int,
    val top: Int,
    val headerTop: Int,
    val footerY: Int
) {
    fun cardX(index: Int): Int = left + (index % columns) * (cardWidth + gap)
    fun cardY(index: Int): Int = top + (index / columns) * (cardHeight + gap)

    companion object {
        fun calculate(width: Int, height: Int): MarketLayout {
            val margin = 10
            val gap = 7
            val available = (width - margin * 2).coerceAtLeast(132)
            val usableHeight = (height - 112).coerceAtLeast(112)

            // Keep cards deliberately small and nearly fixed. GUI scaling already changes
            // the logical canvas, so a conservative component size is more stable than
            // continuously stretching cards to fill whatever space happens to exist.
            val columns = when {
                available >= 480 -> 3
                available >= 315 -> 2
                else -> 1
            }
            val desiredCardWidth = 152
            val cardWidth = minOf(desiredCardWidth, (available - gap * (columns - 1)) / columns).coerceAtLeast(132)
            val cardHeight = minOf(116, usableHeight)
            val gridWidth = cardWidth * columns + gap * (columns - 1)
            val left = (width - gridWidth) / 2
            val top = if (height < 330) 70 else 78
            return MarketLayout(
                columns = columns,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                gap = gap,
                left = left,
                top = top,
                headerTop = 18,
                footerY = height - 30
            )
        }
    }
}

internal data class DetailsLayout(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val compact: Boolean,
    val contentLeft: Int,
    val contentTop: Int,
    val rightCenterX: Int,
    val modelY: Int,
    val radarY: Int,
    val buttonY: Int
) {
    companion object {
        fun calculate(screenWidth: Int, screenHeight: Int): DetailsLayout {
            val marginX = 10
            val marginY = 10
            // Deliberately conservative fixed targets. Only shrink when the logical canvas
            // cannot fit them. This keeps details visually consistent across x1/x2/x3/Auto.
            val targetWidth = 500
            val targetHeight = 290
            val panelWidth = minOf(targetWidth, screenWidth - marginX * 2).coerceAtLeast(250)
            val panelHeight = minOf(targetHeight, screenHeight - marginY * 2).coerceAtLeast(230)
            val left = (screenWidth - panelWidth) / 2
            val top = (screenHeight - panelHeight) / 2
            val compact = panelWidth < 470 || panelHeight < 270
            return DetailsLayout(
                left = left,
                top = top,
                width = panelWidth,
                height = panelHeight,
                compact = compact,
                contentLeft = left + 16,
                contentTop = top + 38,
                rightCenterX = if (compact) left + panelWidth - 72 else left + (panelWidth * 0.76).toInt(),
                modelY = top + if (compact) 58 else 52,
                radarY = top + panelHeight - if (compact) 78 else 88,
                buttonY = top + panelHeight - 27
            )
        }
    }
}
