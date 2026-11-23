package com.jwwviewer.app.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.jwwviewer.app.data.DrawingMemo
import com.jwwviewer.app.data.InkStroke
import com.jwwviewer.app.data.PerformanceMode
import com.jwwviewer.app.parser.JwwBounds
import com.jwwviewer.app.parser.JwwCircle
import com.jwwviewer.app.parser.JwwDrawing
import com.jwwviewer.app.parser.JwwElement
import com.jwwviewer.app.parser.JwwLine
import com.jwwviewer.app.parser.JwwPoint
import com.jwwviewer.app.parser.JwwSolid
import com.jwwviewer.app.parser.JwwText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Batched CAD draw (JwwMeasure-style): layer filter + native Path batch + Y-flip.
 */
class CadRenderer {
    private var cachedKey: Int = Int.MIN_VALUE
    private var androidLines: Map<Int, android.graphics.Path> = emptyMap()
    private var circles: List<JwwCircle> = emptyList()
    private var solids: List<JwwSolid> = emptyList()
    private var texts: List<JwwText> = emptyList()
    private var points: List<JwwPoint> = emptyList()

    fun prepare(drawing: JwwDrawing, layerVisible: BooleanArray) {
        val key = layerVisible.contentHashCode() xor drawing.elements.size
        if (key == cachedKey) return
        cachedKey = key

        val map = LinkedHashMap<Int, android.graphics.Path>()
        val circleList = ArrayList<JwwCircle>()
        val solidList = ArrayList<JwwSolid>()
        val textList = ArrayList<JwwText>()
        val pointList = ArrayList<JwwPoint>()

        for (e in drawing.elements) {
            if (!isVisible(e, layerVisible, drawing.layerStates)) continue
            when (e) {
                is JwwLine -> {
                    val path = map.getOrPut(e.attr.color) { android.graphics.Path() }
                    path.moveTo(e.x1.toFloat(), e.y1.toFloat())
                    path.lineTo(e.x2.toFloat(), e.y2.toFloat())
                }
                is JwwCircle -> circleList.add(e)
                is JwwSolid -> solidList.add(e)
                is JwwText -> textList.add(e)
                is JwwPoint -> pointList.add(e)
                else -> Unit
            }
        }
        androidLines = map
        circles = circleList
        solids = solidList
        texts = textList
        points = pointList
    }

    fun draw(
        scope: DrawScope,
        bounds: JwwBounds,
        viewW: Float,
        viewH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        strokePx: Float,
        fontScale: Float = 1f,
        performance: PerformanceMode = PerformanceMode.BALANCED,
        rotationDeg: Float = 0f,
        memos: List<DrawingMemo> = emptyList(),
        strokes: List<InkStroke> = emptyList(),
        draftStroke: List<Pair<Float, Float>> = emptyList(),
    ) {
        val vt = ViewTransform(bounds, viewW, viewH, scale, offsetX, offsetY, rotationDeg)
        fun tx(x: Float, y: Float): Offset = vt.worldToScreen(x, y)

        if (performance.drawSolids) {
            for (solid in solids) {
                val path = Path().apply {
                    val p1 = tx(solid.x1.toFloat(), solid.y1.toFloat())
                    val p2 = tx(solid.x2.toFloat(), solid.y2.toFloat())
                    val p3 = tx(solid.x3.toFloat(), solid.y3.toFloat())
                    val p4 = tx(solid.x4.toFloat(), solid.y4.toFloat())
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                val fill = solid.rgb?.let { Color(it or 0xFF000000.toInt()) }
                    ?: JwwColors.pen(solid.attr.color).copy(alpha = 0.35f)
                scope.drawPath(path, color = fill)
            }
        }

        val native = scope.drawContext.canvas.nativeCanvas
        val paintFlags = if (performance.antiAlias) android.graphics.Paint.ANTI_ALIAS_FLAG else 0
        val paint = android.graphics.Paint(paintFlags).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = if (performance.antiAlias) {
                android.graphics.Paint.Cap.ROUND
            } else {
                android.graphics.Paint.Cap.BUTT
            }
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = max(strokePx / scale, 0.12f)
        }
        native.save()
        native.translate(viewW * 0.5f + offsetX, viewH * 0.5f + offsetY)
        native.rotate(rotationDeg)
        native.scale(scale, -scale)
        native.translate(-vt.cx, -vt.cy)
        for ((colorIdx, path) in androidLines) {
            val c = JwwColors.pen(colorIdx)
            paint.color = android.graphics.Color.argb(
                255,
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt(),
            )
            native.drawPath(path, paint)
        }
        native.restore()

        val stroke = Stroke(width = max(strokePx, 1f))
        for (c in circles) {
            val color = JwwColors.pen(c.attr.color)
            if (c.fullCircle || kotlin.math.abs(c.sweepAngle) >= PI * 2 - 1e-3) {
                scope.drawCircle(
                    color = color,
                    radius = (c.radius * scale).toFloat(),
                    center = tx(c.cx.toFloat(), c.cy.toFloat()),
                    style = stroke,
                )
            } else {
                val minSteps = if (performance == PerformanceMode.LOW) 6 else 12
                val steps = max(
                    minSteps,
                    (kotlin.math.abs(c.sweepAngle) / performance.arcStepsDivisor).toInt(),
                )
                val path = Path()
                val flat = if (c.flatness == 0.0) 1.0 else c.flatness
                for (i in 0..steps) {
                    val a = c.startAngle + c.sweepAngle * (i.toDouble() / steps)
                    val x = (c.cx + c.radius * cos(a) * flat).toFloat()
                    val y = (c.cy + c.radius * sin(a)).toFloat()
                    val p = tx(x, y)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                scope.drawPath(path, color = color, style = stroke)
            }
        }

        if (performance.drawPoints) {
            for (p in points) {
                scope.drawCircle(
                    JwwColors.pen(p.attr.color),
                    radius = max(2f, strokePx),
                    center = tx(p.x.toFloat(), p.y.toFloat()),
                )
            }
        }

        if (performance.drawTexts) {
            val textPaint = android.graphics.Paint(paintFlags)
            var drawn = 0
            for (t in texts) {
                if (drawn >= performance.maxTexts) break
                if (t.text.isBlank()) continue
                val o = tx(t.x1.toFloat(), t.y1.toFloat())
                val c = JwwColors.pen(t.attr.color)
                textPaint.color = android.graphics.Color.argb(
                    255,
                    (c.red * 255).toInt(),
                    (c.green * 255).toInt(),
                    (c.blue * 255).toInt(),
                )
                textPaint.textSize = max(
                    9f * fontScale,
                    (t.sizeY * scale * fontScale).toFloat().coerceIn(8f * fontScale, 64f * fontScale),
                )
                native.save()
                native.translate(o.x, o.y)
                native.rotate((-t.angleDeg + rotationDeg).toFloat())
                native.drawText(t.text.replace("\r", " ").take(200), 0f, 0f, textPaint)
                native.restore()
                drawn++
            }
        }

        fun drawInk(points: List<Pair<Float, Float>>, colorArgb: Int) {
            if (points.size < 2) return
            val path = Path()
            val p0 = tx(points[0].first, points[0].second)
            path.moveTo(p0.x, p0.y)
            for (i in 1 until points.size) {
                val p = tx(points[i].first, points[i].second)
                path.lineTo(p.x, p.y)
            }
            scope.drawPath(
                path,
                color = Color(colorArgb),
                style = Stroke(
                    width = max(3f, strokePx * 2.2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        for (stroke in strokes) {
            drawInk(stroke.points, stroke.color)
        }
        if (draftStroke.isNotEmpty()) {
            drawInk(draftStroke, InkStroke.DEFAULT_PEN_COLOR)
        }

        if (memos.isNotEmpty()) {
            val pinPaint = android.graphics.Paint(paintFlags).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(235, 15, 118, 110)
            }
            val bubblePaint = android.graphics.Paint(paintFlags).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(230, 255, 255, 255)
            }
            val bubbleStroke = android.graphics.Paint(paintFlags).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f
                color = android.graphics.Color.argb(180, 15, 118, 110)
            }
            val numPaint = android.graphics.Paint(paintFlags).apply {
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 12f * fontScale
                isFakeBoldText = true
            }
            val notePaint = android.graphics.Paint(paintFlags).apply {
                color = android.graphics.Color.argb(255, 11, 31, 28)
                textSize = 12f * fontScale
            }
            memos.forEachIndexed { i, memo ->
                val p = tx(memo.x, memo.y)
                val r = 12f * fontScale
                native.drawCircle(p.x, p.y, r, pinPaint)
                val label = (i + 1).toString()
                val ty = p.y - (numPaint.ascent() + numPaint.descent()) / 2f
                native.drawText(label, p.x, ty, numPaint)
                val preview = memo.text.replace('\n', ' ').trim()
                if (preview.isNotEmpty()) {
                    val shown = if (preview.length > 28) preview.take(27) + "…" else preview
                    val tw = notePaint.measureText(shown)
                    val padX = 8f
                    val padY = 6f
                    val left = p.x + r + 6f
                    val top = p.y - notePaint.textSize - padY
                    val rect = android.graphics.RectF(
                        left,
                        top,
                        left + tw + padX * 2,
                        p.y + padY,
                    )
                    native.drawRoundRect(rect, 8f, 8f, bubblePaint)
                    native.drawRoundRect(rect, 8f, 8f, bubbleStroke)
                    native.drawText(shown, left + padX, p.y - padY * 0.4f, notePaint)
                }
            }
        }
    }

    private fun isVisible(
        e: JwwElement,
        override: BooleanArray,
        headerStates: IntArray,
    ): Boolean {
        val idx = e.layerIndex.coerceIn(0, 255)
        return if (idx < override.size) override[idx] else headerStates.getOrElse(idx) { 1 } != 0
    }

    companion object {
        fun fitScale(
            bounds: JwwBounds,
            viewW: Float,
            viewH: Float,
            padding: Float = 48f,
            rotationDeg: Float = 0f,
        ): Float = ViewTransform.fitScale(bounds, viewW, viewH, padding, rotationDeg)
    }
}
