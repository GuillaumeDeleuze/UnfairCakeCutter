package com.unfaircake.cutter.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One colour per person, shared by the overlay and the people list. Candy brights, light enough
 * that the ink number on the dot stays readable. The first eight are the design's.
 */
val SliceColors: List<Color> = listOf(
    Color(0xFFFF4FA3), // bubblegum
    Color(0xFFFFD23F), // lemon
    Color(0xFF5CE1E6), // aqua
    Color(0xFFB388FF), // grape
    Color(0xFF7CF29A), // apple
    Color(0xFFFF8A5B), // tangerine
    Color(0xFFFF9ECF), // candyfloss
    Color(0xFF9ADCFF), // sky
    Color(0xFFD4F25C), // lime
    Color(0xFFE08CFF), // orchid
    Color(0xFFFFB84D), // apricot
    Color(0xFF6EF0C8), // mint
)

fun sliceColor(index: Int): Color = SliceColors[index.mod(SliceColors.size)]
