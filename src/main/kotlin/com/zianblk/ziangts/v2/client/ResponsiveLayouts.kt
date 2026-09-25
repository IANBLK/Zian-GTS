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
            val margin = if (width < 520) 12 else 28
            val gap = if (width < 520) 6 else 8
            val available = (width - margin * 2).coerceAtLeast(138)

            // GUI scale 3/Auto commonly yields a logical width below ~600 px.
            // Drop columns instead of squeezing three desktop cards until text overlaps.
            val columns = when {
                available >= 520 -> 3
                available >= 330 -> 2
                else -> 1
            }
            val cardWidth = ((available - gap * (columns - 1)) / columns)
                .coerceIn(138, 176)
            val cardHeight = if (height < 330) 118 else 126
            val gridWidth = cardWidth * columns + gap * (columns - 1)
            val left = (width - gridWidth) / 2
            val top = if (height < 330) 72 else 80
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
            val marginX = if (screenWidth < 500) 8 else 15
            val marginY = if (screenHeight < 360) 8 else 20
            val panelWidth = minOf(560, screenWidth - marginX * 2).coerceAtLeast(250)
            val panelHeight = minOf(350, screenHeight - marginY * 2).coerceAtLeast(230)
            val left = (screenWidth - panelWidth) / 2
            val top = (screenHeight - panelHeight) / 2
            val compact = panelWidth < 470 || panelHeight < 300
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
