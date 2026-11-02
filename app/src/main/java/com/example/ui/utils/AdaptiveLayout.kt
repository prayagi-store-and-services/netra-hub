package com.example.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout

/**
 * Netra family adaptive-layout rule (phone and tablet).
 *
 * Phones keep the layout they have today. On tablets, smart-class boards and other
 * wide screens the content must not stretch edge to edge: it is capped and centred.
 *
 * - Compact  (< 600 dp): unchanged phone layout, full width.
 * - Medium   (600-839 dp): content capped at 600 dp and centred.
 * - Expanded (>= 840 dp): content capped at 840 dp and centred.
 */
enum class AdaptiveWidthClass { COMPACT, MEDIUM, EXPANDED }

fun adaptiveWidthClassFor(maxWidth: Dp): AdaptiveWidthClass = when {
    maxWidth >= 840.dp -> AdaptiveWidthClass.EXPANDED
    maxWidth >= 600.dp -> AdaptiveWidthClass.MEDIUM
    else -> AdaptiveWidthClass.COMPACT
}

/** Content cap for a screen of [maxWidth]; on compact the screen keeps its full width. */
fun adaptiveContentMaxWidthFor(maxWidth: Dp): Dp = when (adaptiveWidthClassFor(maxWidth)) {
    AdaptiveWidthClass.EXPANDED -> 840.dp
    AdaptiveWidthClass.MEDIUM -> 600.dp
    AdaptiveWidthClass.COMPACT -> maxWidth
}

/**
 * Font scale for wide screens, for smart-class distance viewing. Phones stay at 1.0.
 * The quiz screen already applies its own larger smart-class text; this scale is for
 * the menu, profile, history and summary screens.
 */
fun adaptiveFontScaleFor(screenWidthDp: Int): Float = when {
    screenWidthDp >= 840 -> 1.20f
    screenWidthDp >= 600 -> 1.10f
    else -> 1.00f
}

/**
 * Caps this element's width at the adaptive content width for the current screen and
 * centres it. On phones (compact) this is a no-op: the element keeps its full width.
 */
fun Modifier.adaptiveContentWidth(): Modifier = this.layout { measurable, constraints ->
    val cap = when {
        constraints.maxWidth >= 840.dp.roundToPx() -> 840.dp.roundToPx()
        constraints.maxWidth >= 600.dp.roundToPx() -> 600.dp.roundToPx()
        else -> constraints.maxWidth
    }
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = cap))
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative((constraints.maxWidth - placeable.width) / 2, 0)
    }
}
