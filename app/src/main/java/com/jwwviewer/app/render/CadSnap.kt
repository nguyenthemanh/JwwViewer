package com.jwwviewer.app.render

import androidx.compose.ui.geometry.Offset
import com.jwwviewer.app.parser.JwwCircle
import com.jwwviewer.app.parser.JwwDrawing
import com.jwwviewer.app.parser.JwwLine
import com.jwwviewer.app.parser.JwwPoint
import com.jwwviewer.app.parser.JwwSolid
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Object snap for measure (JW_CAD-style): endpoints, midpoints, centers,
 * then nearest point on a visible segment — in drawing coordinates, not screen mm.
 */
object CadSnap {

    data class Result(
        val world: Offset,
        val kind: Kind,
    )

    enum class Kind { End, Mid, Center, Nearest }

    fun snap(
        drawing: JwwDrawing,
        layerVisible: BooleanArray,
        tap: Offset,
        vt: ViewTransform,
        maxScreenDistPx: Float,
    ): Result? {
        var bestEnd: Result? = null
        var bestEndD = Float.MAX_VALUE
        var bestMid: Result? = null
        var bestMidD = Float.MAX_VALUE
        var bestCenter: Result? = null
        var bestCenterD = Float.MAX_VALUE
        var bestNear: Result? = null
        var bestNearD = Float.MAX_VALUE
        val maxD = maxScreenDistPx
        val maxD2 = maxD * maxD

        fun considerPoint(wx: Double, wy: Double, kind: Kind) {
            val s = vt.worldToScreen(wx.toFloat(), wy.toFloat())
            val dx = s.x - tap.x
            val dy = s.y - tap.y
            val d2 = dx * dx + dy * dy
            if (d2 > maxD2) return
            val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val r = Result(Offset(wx.toFloat(), wy.toFloat()), kind)
            when (kind) {
                Kind.End -> if (d < bestEndD) {
                    bestEndD = d; bestEnd = r
                }
                Kind.Mid -> if (d < bestMidD) {
                    bestMidD = d; bestMid = r
                }
                Kind.Center -> if (d < bestCenterD) {
                    bestCenterD = d; bestCenter = r
                }
                Kind.Nearest -> if (d < bestNearD) {
                    bestNearD = d; bestNear = r
                }
            }
        }

        fun considerSegment(x1: Double, y1: Double, x2: Double, y2: Double) {
            considerPoint(x1, y1, Kind.End)
            considerPoint(x2, y2, Kind.End)
            considerPoint((x1 + x2) * 0.5, (y1 + y2) * 0.5, Kind.Mid)

            val s1 = vt.worldToScreen(x1.toFloat(), y1.toFloat())
            val s2 = vt.worldToScreen(x2.toFloat(), y2.toFloat())
            val nearestScreen = projectScreen(tap, s1, s2) ?: return
            val d2 = (nearestScreen.x - tap.x).let { it * it } +
                (nearestScreen.y - tap.y).let { it * it }
            if (d2 > maxD2) return
            val world = vt.screenToWorld(nearestScreen.x, nearestScreen.y)
            // Re-project onto true world segment for accuracy
            val w = projectWorld(world.x.toDouble(), world.y.toDouble(), x1, y1, x2, y2)
            val d = hypot(
                (nearestScreen.x - tap.x).toDouble(),
                (nearestScreen.y - tap.y).toDouble(),
            ).toFloat()
            if (d < bestNearD) {
                bestNearD = d
                bestNear = Result(Offset(w.first.toFloat(), w.second.toFloat()), Kind.Nearest)
            }
        }

        for (e in drawing.elements) {
            val idx = e.layerIndex.coerceIn(0, 255)
            if (idx < layerVisible.size && !layerVisible[idx]) continue
            when (e) {
                is JwwLine -> considerSegment(e.x1, e.y1, e.x2, e.y2)
                is JwwPoint -> considerPoint(e.x, e.y, Kind.End)
                is JwwSolid -> {
                    considerSegment(e.x1, e.y1, e.x2, e.y2)
                    considerSegment(e.x2, e.y2, e.x3, e.y3)
                    considerSegment(e.x3, e.y3, e.x4, e.y4)
                    considerSegment(e.x4, e.y4, e.x1, e.y1)
                }
                is JwwCircle -> {
                    considerPoint(e.cx, e.cy, Kind.Center)
                    if (e.fullCircle) {
                        // quadrant points help for circles
                        considerPoint(e.cx + e.radius, e.cy, Kind.End)
                        considerPoint(e.cx - e.radius, e.cy, Kind.End)
                        considerPoint(e.cx, e.cy + e.radius, Kind.End)
                        considerPoint(e.cx, e.cy - e.radius, Kind.End)
                    } else {
                        val a0 = Math.toRadians(e.startAngle)
                        val a1 = Math.toRadians(e.startAngle + e.sweepAngle)
                        considerPoint(
                            e.cx + e.radius * cos(a0),
                            e.cy + e.radius * sin(a0),
                            Kind.End,
                        )
                        considerPoint(
                            e.cx + e.radius * cos(a1),
                            e.cy + e.radius * sin(a1),
                            Kind.End,
                        )
                    }
                }
                else -> Unit
            }
        }

        // Priority like JW_CAD: end → mid → center → nearest on edge
        return bestEnd ?: bestMid ?: bestCenter ?: bestNear
    }

    private fun projectScreen(p: Offset, a: Offset, b: Offset): Offset? {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        if (len2 < 1e-6f) return a
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        return Offset(a.x + abx * t, a.y + aby * t)
    }

    private fun projectWorld(
        px: Double,
        py: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Pair<Double, Double> {
        val abx = x2 - x1
        val aby = y2 - y1
        val len2 = abx * abx + aby * aby
        if (len2 < 1e-18) return x1 to y1
        val t = (((px - x1) * abx + (py - y1) * aby) / len2).coerceIn(0.0, 1.0)
        return (x1 + abx * t) to (y1 + aby * t)
    }
}
