package com.unfaircake.cutter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.unfaircake.cutter.R

/** "Candy edition" palette: bubblegum pink, a plum ink for every outline and hard shadow. */
object Candy {
    val Ink = Color(0xFF3A0A2A)
    val Cream = Color(0xFFFFF1F7)
    val Pink = Color(0xFFFF3E9A)
    val PinkSoft = Color(0xFFFF78B9)
    val Track = Color(0xFFFFD6EA)
    val Magenta = Color(0xFFC4005F)
    val Muted = Color(0xFF7A3A60)
    val Yellow = Color(0xFFFFD23F)
    val White = Color(0xFFFFFFFF)

    /** Camera area: behind the preview, the pills on the cake and the dimmed surroundings. */
    val Night = Color(0xFF1C0614)
    val Shade = Color(0xFF12040C)
}

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Optical size for UI text; the variable font would otherwise use its 12pt default.
        FontVariation.Setting("opsz", 16f),
    ),
)

/** Body face: Bricolage Grotesque (variable), 400 to 800. */
val Bricolage = FontFamily(
    bricolage(400),
    bricolage(500),
    bricolage(600),
    bricolage(700),
    bricolage(800),
)

/**
 * CSS-like line height: every line, first and last included, is exactly `lineHeight` tall.
 * Compose's default keeps the font's own (very tall, for Bagel) ascent on the first line.
 */
val ExactLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Display face for numbers, verdicts and titles. It only has one weight. */
val Bagel = FontFamily(Font(R.font.bagel_fat_one))

private val Base = TextStyle(fontFamily = Bricolage, color = Candy.Ink)

private val CandyTypography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(Base),
        displayMedium = displayMedium.merge(Base),
        displaySmall = displaySmall.merge(Base),
        headlineLarge = headlineLarge.merge(Base),
        headlineMedium = headlineMedium.merge(Base),
        headlineSmall = headlineSmall.merge(Base),
        titleLarge = titleLarge.merge(Base),
        titleMedium = titleMedium.merge(Base),
        titleSmall = titleSmall.merge(Base),
        bodyLarge = bodyLarge.merge(Base),
        bodyMedium = bodyMedium.merge(Base),
        bodySmall = bodySmall.merge(Base),
        labelLarge = labelLarge.merge(Base),
        labelMedium = labelMedium.merge(Base),
        labelSmall = labelSmall.merge(Base),
    )
}

// Material is only used for text selection colours, ripples and a few defaults.
private val CandyColors = lightColorScheme(
    primary = Candy.Pink,
    onPrimary = Candy.Ink,
    secondary = Candy.Yellow,
    onSecondary = Candy.Ink,
    background = Candy.Cream,
    onBackground = Candy.Ink,
    surface = Candy.Cream,
    onSurface = Candy.Ink,
    surfaceContainer = Candy.Cream,
    outline = Candy.Ink,
)

/** One look, light only: the design has no dark variant. */
@Composable
fun UnfairCakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CandyColors,
        typography = CandyTypography,
        content = content,
    )
}
