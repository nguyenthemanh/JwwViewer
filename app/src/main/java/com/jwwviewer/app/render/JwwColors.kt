package com.jwwviewer.app.render

import androidx.compose.ui.graphics.Color

/** JW_cad screen pen colors 0–9 (background, 1–8 pens, gray). */
object JwwColors {
    private val pens = arrayOf(
        Color(0xFF1A1A1A), // 0 bg / black lines on light paper → treat as dark ink
        Color(0xFFC62828), // 1 red
        Color(0xFF2E7D32), // 2 green
        Color(0xFF1565C0), // 3 blue
        Color(0xFF6A1B9A), // 4 magenta-ish
        Color(0xFF00838F), // 5 cyan
        Color(0xFFF9A825), // 6 yellow/amber
        Color(0xFFEF6C00), // 7 orange
        Color(0xFF37474F), // 8 dark gray
        Color(0xFF90A4AE), // 9 gray
    )

    fun pen(colorIndex: Int): Color {
        val i = colorIndex.coerceIn(0, 9)
        return pens[i]
    }

    val paperLight = Color(0xFFF4F7F5)
    val paperDark = Color(0xFF121816)
    val grid = Color(0x140F766E)
}
