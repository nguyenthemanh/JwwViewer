package com.jwwviewer.app.render

import androidx.compose.ui.graphics.Color

/**
 * JW_CAD default 線色 1–8 (light paper):
 * 1 水色, 2 黒, 3 緑, 4 黄, 5 紫, 6 青, 7 濃水色, 8 赤.
 * Index 0 = black ink (safe fallback).
 */
object JwwColors {
    private val pens = arrayOf(
        Color(0xFF1A1A1A), // 0 fallback black
        Color(0xFF00A0C8), // 1 水色 (cyan / ocean)
        Color(0xFF1A1A1A), // 2 black
        Color(0xFF2E7D32), // 3 green
        Color(0xFFF9A825), // 4 yellow
        Color(0xFF8E24AA), // 5 magenta
        Color(0xFF1565C0), // 6 blue
        Color(0xFF00838F), // 7 deep cyan
        Color(0xFFD32F2F), // 8 red
        Color(0xFF90A4AE), // 9 gray
    )

    /** 実点 markers — always vivid red (JW_CAD screen convention users expect). */
    val pointRed = Color(0xFFE53935)

    fun pen(colorIndex: Int): Color {
        val i = colorIndex.coerceIn(0, 9)
        return pens[i]
    }

    val paperLight = Color(0xFFFFFFFF)
    val grid = Color(0x140F766E)
}
