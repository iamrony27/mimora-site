package com.mimora.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * 12 Subtle Seasonal Moods for MIMORA writing pages.
 * Carefully calibrated to ensure high text contrast, distraction-free writing,
 * and seamless light/dark mode adaptation.
 */
enum class SeasonalMood(
    val monthIndex: Int,
    val displayName: String,
    val seasonDescription: String,
    val emoji: String,
    val lightColors: List<Color>,
    val darkColors: List<Color>,
    val accentColor: Color
) {
    JANUARY(
        monthIndex = 1,
        displayName = "Quiet Frost",
        seasonDescription = "Winter calm with soft slate undertones",
        emoji = "❄️",
        lightColors = listOf(Color(0xFFF7F9FB), Color(0xFFEDF2F7)),
        darkColors = listOf(Color(0xFF131518), Color(0xFF1A1D23)),
        accentColor = Color(0xFF5B7E96)
    ),
    FEBRUARY(
        monthIndex = 2,
        displayName = "Lavender Dawn",
        seasonDescription = "Late winter renewal with subtle lilac mist",
        emoji = "🪻",
        lightColors = listOf(Color(0xFFFAF8FC), Color(0xFFF0ECF8)),
        darkColors = listOf(Color(0xFF16131A), Color(0xFF1F1A25)),
        accentColor = Color(0xFF7E6699)
    ),
    MARCH(
        monthIndex = 3,
        displayName = "Meadow Mist",
        seasonDescription = "Early spring air with delicate sage",
        emoji = "🌱",
        lightColors = listOf(Color(0xFFF8FAF7), Color(0xFFEDF4ED)),
        darkColors = listOf(Color(0xFF131714), Color(0xFF1A211C)),
        accentColor = Color(0xFF5A8064)
    ),
    APRIL(
        monthIndex = 4,
        displayName = "Cherry Blossom",
        seasonDescription = "Gentle blossom tint with warm morning glow",
        emoji = "🌸",
        lightColors = listOf(Color(0xFFFCF8F9), Color(0xFFF8EDF1)),
        darkColors = listOf(Color(0xFF1A1316), Color(0xFF24191E)),
        accentColor = Color(0xFF945F73)
    ),
    MAY(
        monthIndex = 5,
        displayName = "Amber Breeze",
        seasonDescription = "Warm golden morning warmth",
        emoji = "🌾",
        lightColors = listOf(Color(0xFFFCFAF5), Color(0xFFF7F0E4)),
        darkColors = listOf(Color(0xFF1A1813), Color(0xFF26221A)),
        accentColor = Color(0xFF997A47)
    ),
    JUNE(
        monthIndex = 6,
        displayName = "Azure Solstice",
        seasonDescription = "Midsummer serene sky with bright clarity",
        emoji = "☀️",
        lightColors = listOf(Color(0xFFF6FAFC), Color(0xFFEAF3F8)),
        darkColors = listOf(Color(0xFF12171A), Color(0xFF192126)),
        accentColor = Color(0xFF477C99)
    ),
    JULY(
        monthIndex = 7,
        displayName = "Monsoon Rain",
        seasonDescription = "Deep cooling rain mist with peaceful gray-blue",
        emoji = "🌧️",
        lightColors = listOf(Color(0xFFF5F8F9), Color(0xFFE8EEF2)),
        darkColors = listOf(Color(0xFF121517), Color(0xFF191F24)),
        accentColor = Color(0xFF4C6A7A)
    ),
    AUGUST(
        monthIndex = 8,
        displayName = "Verdant Rain",
        seasonDescription = "Late monsoon lushness with quiet moss hues",
        emoji = "🌿",
        lightColors = listOf(Color(0xFFF6F9F6), Color(0xFFE9F2E9)),
        darkColors = listOf(Color(0xFF121713), Color(0xFF19221B)),
        accentColor = Color(0xFF4F7856)
    ),
    SEPTEMBER(
        monthIndex = 9,
        displayName = "Harvest Dawn",
        seasonDescription = "Early autumn with mellow golden straw hues",
        emoji = "🍂",
        lightColors = listOf(Color(0xFFFBF9F5), Color(0xFFF5EFE4)),
        darkColors = listOf(Color(0xFF191712), Color(0xFF242018)),
        accentColor = Color(0xFF8F7341)
    ),
    OCTOBER(
        monthIndex = 10,
        displayName = "Terracotta Dusk",
        seasonDescription = "Crisp autumn dusk with burnt ochre whispers",
        emoji = "🍁",
        lightColors = listOf(Color(0xFFFAF6F4), Color(0xFFF5EAE5)),
        darkColors = listOf(Color(0xFF1A1412), Color(0xFF261D19)),
        accentColor = Color(0xFF995E47)
    ),
    NOVEMBER(
        monthIndex = 11,
        displayName = "Plum Twilight",
        seasonDescription = "Festive pre-winter rich twilight violet",
        emoji = "🍇",
        lightColors = listOf(Color(0xFFF9F6F9), Color(0xFFF2EAF3)),
        darkColors = listOf(Color(0xFF181318), Color(0xFF241B25)),
        accentColor = Color(0xFF7E4F80)
    ),
    DECEMBER(
        monthIndex = 12,
        displayName = "Nordic Pine",
        seasonDescription = "Deep winter solstice with quiet evergreen silence",
        emoji = "🌲",
        lightColors = listOf(Color(0xFFF6F8F7), Color(0xFFE9EFEB)),
        darkColors = listOf(Color(0xFF121614), Color(0xFF18201C)),
        accentColor = Color(0xFF4A7057)
    );

    companion object {
        fun forCurrentMonth(): SeasonalMood {
            val currentMonth = LocalDate.now().monthValue
            return entries.firstOrNull { it.monthIndex == currentMonth } ?: NOVEMBER
        }

        fun forDate(date: LocalDate): SeasonalMood {
            return entries.firstOrNull { it.monthIndex == date.monthValue } ?: NOVEMBER
        }

        fun fromIndex(index: Int): SeasonalMood {
            if (index == 0) return forCurrentMonth()
            return entries.firstOrNull { it.monthIndex == index } ?: NOVEMBER
        }
    }
}

/**
 * Renders the subtle, non-distracting seasonal background wrapper.
 */
@Composable
fun SeasonalBackgroundContainer(
    mood: SeasonalMood,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradientColors = if (isDarkTheme) mood.darkColors else mood.lightColors
    val backgroundBrush = Brush.verticalGradient(gradientColors)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        content()
    }
}
