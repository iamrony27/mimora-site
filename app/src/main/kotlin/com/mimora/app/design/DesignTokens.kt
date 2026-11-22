package com.mimora.app.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * MIMORA Design System Tokens
 * Aligned strictly with Stitch "Serene Sanctuary" tokens and Material 3 principles.
 */
object MimoraSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val normal = 16.dp
    val large = 20.dp
    val extraLarge = 24.dp
    val doubleLarge = 32.dp
}

object MimoraShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(24.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val full = RoundedCornerShape(9999.dp)
}

object MimoraTouchTargets {
    val minTarget = 48.dp
}

object MimoraElevation {
    val level0 = 0.dp
    val level1 = 2.dp
    val level2 = 4.dp
    val level3 = 8.dp
}
