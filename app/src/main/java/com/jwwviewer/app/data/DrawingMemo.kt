package com.jwwviewer.app.data

import java.util.UUID

enum class MemoTool {
    None,
    /** Freehand pen / ink on the drawing */
    Pen,
    /** Tap to place a text note */
    Note,
}

/** Text note pin (world coordinates). */
data class DrawingMemo(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val text: String,
)

/** Freehand ink stroke in world coordinates. */
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Pair<Float, Float>>,
    /** ARGB */
    val color: Int = DEFAULT_PEN_COLOR,
) {
    companion object {
        const val DEFAULT_PEN_COLOR: Int = 0xFFE11D48.toInt()
    }
}
