package com.jwwviewer.app.render

import androidx.compose.ui.geometry.Offset
import com.jwwviewer.app.parser.JwwBounds
import kotlin.math.cos
import kotlin.math.sin

/** Shared CAD view: pan / zoom / 90°-step rotation around drawing center. */
data class ViewTransform(
    val bounds: JwwBounds,
    val viewW: Float,
    val viewH: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val rotationDeg: Float = 0f,
) {
    val cx: Float get() = ((bounds.minX + bounds.maxX) * 0.5).toFloat()
    val cy: Float get() = ((bounds.minY + bounds.maxY) * 0.5).toFloat()

    private val rad: Double get() = Math.toRadians(rotationDeg.toDouble())
    private val cosR: Float get() = cos(rad).toFloat()
    private val sinR: Float get() = sin(rad).toFloat()

    fun worldToScreen(x: Float, y: Float): Offset {
        val dx = (x - cx) * scale
        val dy = -(y - cy) * scale
        val rx = dx * cosR - dy * sinR
        val ry = dx * sinR + dy * cosR
        return Offset(rx + viewW * 0.5f + offsetX, ry + viewH * 0.5f + offsetY)
    }

    fun screenToWorld(sx: Float, sy: Float): Offset {
        val dx = sx - viewW * 0.5f - offsetX
        val dy = sy - viewH * 0.5f - offsetY
        // inverse rotate
        val ux = dx * cosR + dy * sinR
        val uy = -dx * sinR + dy * cosR
        val x = ux / scale + cx
        val y = -uy / scale + cy
        return Offset(x, y)
    }

    companion object {
        fun fitScale(
            bounds: JwwBounds,
            viewW: Float,
            viewH: Float,
            padding: Float,
            rotationDeg: Float,
        ): Float {
            val rot = ((rotationDeg % 360f) + 360f) % 360f
            val bw = bounds.width.toFloat().coerceAtLeast(1f)
            val bh = bounds.height.toFloat().coerceAtLeast(1f)
            val (effW, effH) = if (rot == 90f || rot == 270f) bh to bw else bw to bh
            val sx = (viewW - padding * 2) / effW
            val sy = (viewH - padding * 2) / effH
            return minOf(sx, sy).coerceIn(0.001f, 500f)
        }
    }
}
