package com.example.viko.view.screens.home.tutorial

/** All values use pixels from the same window; measured text can grow with font scale. */
internal fun tutorialBubbleTop(
    targetTop: Float?,
    targetBottom: Float?,
    bubbleHeight: Float,
    viewportHeight: Float,
    safeTop: Float,
    safeBottom: Float,
    gap: Float
): Float {
    val lastTop = (viewportHeight - safeBottom - bubbleHeight).coerceAtLeast(safeTop)
    val below = targetBottom?.plus(gap)
    val above = targetTop?.minus(gap)?.minus(bubbleHeight)
    val preferred = when {
        below != null && below >= safeTop && below <= lastTop -> below
        above != null && above >= safeTop && above <= lastTop -> above
        else -> (safeTop + lastTop) / 2f
    }
    return preferred.coerceIn(safeTop, lastTop)
}
