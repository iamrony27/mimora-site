package com.mimora.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Five user-selectable writing canvas colors. These replace the old
 * calendar-driven seasonal papers so the diary always matches the user's
 * chosen palette instead of the current month.
 */
enum class WritingCanvasTheme(
    val displayName: String,
    val lightCanvasColors: List<Color>,
    val darkCanvasColors: List<Color>,
    val accentColor: Color,
    val swatchColor: Color
) {
    LAVENDER(
        displayName = "Lavender",
        lightCanvasColors = listOf(Color(0xFFF4EEFF), Color(0xFFE6DBFF)),
        darkCanvasColors = listOf(Color(0xFF241B38), Color(0xFF33254D)),
        accentColor = Color(0xFF7650D8),
        swatchColor = Color(0xFF9A7BE8)
    ),
    OCEAN(
        displayName = "Ocean",
        lightCanvasColors = listOf(Color(0xFFE9F7FF), Color(0xFFD5ECFA)),
        darkCanvasColors = listOf(Color(0xFF102632), Color(0xFF183C4E)),
        accentColor = Color(0xFF2F7FA3),
        swatchColor = Color(0xFF4FA8CC)
    ),
    MEADOW(
        displayName = "Meadow",
        lightCanvasColors = listOf(Color(0xFFECF9EE), Color(0xFFD8F0DC)),
        darkCanvasColors = listOf(Color(0xFF12271A), Color(0xFF1D3B27)),
        accentColor = Color(0xFF3F8F5B),
        swatchColor = Color(0xFF63B57C)
    ),
    SUNSET(
        displayName = "Sunset",
        lightCanvasColors = listOf(Color(0xFFFFF1E8), Color(0xFFFFDED0)),
        darkCanvasColors = listOf(Color(0xFF2C1B16), Color(0xFF43291F)),
        accentColor = Color(0xFFD06B45),
        swatchColor = Color(0xFFEE8C62)
    ),
    ROSE(
        displayName = "Rose",
        lightCanvasColors = listOf(Color(0xFFFFEDF4), Color(0xFFFADCE9)),
        darkCanvasColors = listOf(Color(0xFF2B1922), Color(0xFF422735)),
        accentColor = Color(0xFFC45C86),
        swatchColor = Color(0xFFE07EA6)
    );

    companion object {
        fun fromIndex(index: Int): WritingCanvasTheme = entries.getOrElse(index) { LAVENDER }
    }
}

/**
 * Subtle page-wide wash using the selected writing color. The writing card
 * itself uses a stronger version of the same palette.
 */
@Composable
fun WritingCanvasThemeBackgroundContainer(
    theme: WritingCanvasTheme,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkTheme) theme.darkCanvasColors else theme.lightCanvasColors
    val pageColors = palette.map { color ->
        color.copy(alpha = if (isDarkTheme) 0.42f else 0.30f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(pageColors))
    ) {
        content()
    }
}

/**
 * Faint repeating doodle wallpaper, inspired by classic chat backgrounds.
 * It sits behind the writing fields and is tinted by the active writing theme,
 * so the five palettes remain in charge of the page color while the text area
 * gains a soft, paper-like texture.
 */
fun Modifier.chatWallpaperPattern(
    tint: Color,
    alpha: Float = 0.12f
): Modifier = drawBehind {
    val tile = 168.dp.toPx()
    val icon = 20.dp.toPx()
    val stroke = Stroke(width = 1.6.dp.toPx())
    val color = tint.copy(alpha = alpha)
    val columns = (size.width / tile).toInt() + 2
    val rows = (size.height / tile).toInt() + 2

    fun DrawScope.drawDoodle(kind: Int, cx: Float, cy: Float) {
        when (kind) {
            0 -> { // heart
                val path = Path().apply {
                    moveTo(cx, cy + icon * 0.42f)
                    cubicTo(
                        cx - icon * 1.1f, cy - icon * 0.25f,
                        cx - icon * 0.5f, cy - icon * 0.95f,
                        cx, cy - icon * 0.35f
                    )
                    cubicTo(
                        cx + icon * 0.5f, cy - icon * 0.95f,
                        cx + icon * 1.1f, cy - icon * 0.25f,
                        cx, cy + icon * 0.42f
                    )
                }
                drawPath(path = path, color = color, style = stroke)
            }
            1 -> { // star
                val path = Path()
                for (i in 0 until 10) {
                    val angle = Math.toRadians((-90 + i * 36).toDouble())
                    val radius = if (i % 2 == 0) icon * 0.62f else icon * 0.26f
                    val px = cx + (radius * cos(angle)).toFloat()
                    val py = cy + (radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path = path, color = color, style = stroke)
            }
            2 -> { // smiley
                drawCircle(color = color, radius = icon * 0.5f, center = Offset(cx, cy), style = stroke)
                drawCircle(
                    color = color,
                    radius = icon * 0.055f,
                    center = Offset(cx - icon * 0.18f, cy - icon * 0.12f)
                )
                drawCircle(
                    color = color,
                    radius = icon * 0.055f,
                    center = Offset(cx + icon * 0.18f, cy - icon * 0.12f)
                )
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - icon * 0.28f, cy - icon * 0.02f),
                    size = Size(icon * 0.56f, icon * 0.36f),
                    style = stroke
                )
            }
            else -> { // music note
                drawCircle(
                    color = color,
                    radius = icon * 0.2f,
                    center = Offset(cx - icon * 0.28f, cy + icon * 0.32f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(cx - icon * 0.08f, cy + icon * 0.32f),
                    end = Offset(cx - icon * 0.08f, cy - icon * 0.5f),
                    strokeWidth = stroke.width
                )
                val path = Path().apply {
                    moveTo(cx - icon * 0.08f, cy - icon * 0.5f)
                    quadraticBezierTo(
                        cx + icon * 0.4f, cy - icon * 0.6f,
                        cx + icon * 0.34f, cy - icon * 0.18f
                    )
                }
                drawPath(path = path, color = color, style = stroke)
            }
        }
    }

    for (row in 0 until rows) {
        for (col in 0 until columns) {
            val x = col * tile + if (row % 2 == 0) 0f else tile / 2
            val y = row * tile
            drawDoodle((row + col) % 4, x + tile * 0.32f, y + tile * 0.3f)
            drawDoodle((row + col + 1) % 4, x + tile * 0.72f, y + tile * 0.72f)
        }
    }
}
