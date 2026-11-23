package com.jwwviewer.app.render

import androidx.compose.ui.geometry.Offset
import com.jwwviewer.app.data.DrawingMemo
import com.jwwviewer.app.parser.JwwBounds
import com.jwwviewer.app.parser.JwwDrawing
import com.jwwviewer.app.parser.JwwText
import com.jwwviewer.app.translate.containsJapanese
import com.jwwviewer.app.translate.normalizeCadText
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class CadTextHit(
    val text: JwwText,
    val display: String,
)

/**
 * Hit-test Japanese CAD notes using the same transform as [CadRenderer.draw].
 */
object CadTextHitTester {
    fun hitJapanese(
        drawing: JwwDrawing,
        layerVisible: BooleanArray,
        tap: Offset,
        bounds: JwwBounds,
        viewW: Float,
        viewH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        fontScale: Float,
        rotationDeg: Float = 0f,
    ): CadTextHit? {
        val vt = ViewTransform(bounds, viewW, viewH, scale, offsetX, offsetY, rotationDeg)
        val paint = android.graphics.Paint()
        var best: CadTextHit? = null
        var bestDist = Float.MAX_VALUE

        for (e in drawing.elements) {
            if (e !is JwwText) continue
            val idx = e.layerIndex.coerceIn(0, 255)
            if (idx < layerVisible.size && !layerVisible[idx]) continue
            val raw = normalizeCadText(e.text)
            if (raw.isBlank() || !containsJapanese(raw)) continue
            val display = raw.replace('\n', ' ').take(200)

            val o = vt.worldToScreen(e.x1.toFloat(), e.y1.toFloat())
            val textSize = max(
                9f * fontScale,
                (e.sizeY * scale * fontScale).toFloat().coerceIn(8f * fontScale, 64f * fontScale),
            )
            paint.textSize = textSize
            val w = paint.measureText(display).coerceAtLeast(textSize)
            val h = textSize * 1.35f
            val pad = max(14f, textSize * 0.35f)

            val a = Math.toRadians(e.angleDeg - rotationDeg)
            val cosA = cos(a).toFloat()
            val sinA = sin(a).toFloat()
            val dx = tap.x - o.x
            val dy = tap.y - o.y
            val lx = dx * cosA - dy * sinA
            val ly = dx * sinA + dy * cosA

            if (lx < -pad || lx > w + pad || ly < -h - pad || ly > pad) continue
            val cxLocal = (w * 0.5f).coerceAtLeast(1f)
            val cyLocal = -h * 0.4f
            val dist = (lx - cxLocal) * (lx - cxLocal) + (ly - cyLocal) * (ly - cyLocal)
            if (dist < bestDist) {
                bestDist = dist
                best = CadTextHit(e, display)
            }
        }
        return best
    }

    fun hitMemo(
        memos: List<DrawingMemo>,
        tap: Offset,
        bounds: JwwBounds,
        viewW: Float,
        viewH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        fontScale: Float,
        rotationDeg: Float,
    ): DrawingMemo? {
        val vt = ViewTransform(bounds, viewW, viewH, scale, offsetX, offsetY, rotationDeg)
        val hitR = 18f * fontScale
        val hitR2 = hitR * hitR
        var best: DrawingMemo? = null
        var bestDist = Float.MAX_VALUE
        for (m in memos) {
            val p = vt.worldToScreen(m.x, m.y)
            val dx = tap.x - p.x
            val dy = tap.y - p.y
            val d = dx * dx + dy * dy
            if (d <= hitR2 && d < bestDist) {
                bestDist = d
                best = m
            }
        }
        return best
    }
}
