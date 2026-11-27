package com.jwwviewer.app.parser

import kotlin.math.cos
import kotlin.math.sin

/**
 * Expand [JwwBlockRef] inserts using stored [CDataList] definitions (JW_CAD 部品).
 * Rotation is radians (LibreCAD `m_radKaitenKaku`).
 */
object BlockExpand {
    private const val MAX_DEPTH = 8

    fun flatten(
        elements: List<JwwElement>,
        blockDefs: Map<Int, List<JwwElement>>,
    ): List<JwwElement> {
        if (blockDefs.isEmpty() || elements.none { it is JwwBlockRef }) return elements
        val out = ArrayList<JwwElement>(elements.size)
        for (e in elements) {
            when (e) {
                is JwwBlockRef -> expandRef(e, blockDefs, out, 0)
                else -> out.add(e)
            }
        }
        return out
    }

    private fun expandRef(
        ref: JwwBlockRef,
        defs: Map<Int, List<JwwElement>>,
        out: MutableList<JwwElement>,
        depth: Int,
    ) {
        if (depth >= MAX_DEPTH) {
            out.add(ref)
            return
        }
        val members = defs[ref.blockNumber]
        if (members == null) {
            out.add(ref) // keep insert so it is not silently deleted
            return
        }
        for (m in members) {
            when (m) {
                is JwwBlockRef -> {
                    // Nested: apply outer then inner — approximate by transforming insert
                    val (x, y) = xform(m.x, m.y, ref)
                    expandRef(
                        m.copy(
                            x = x,
                            y = y,
                            scaleX = m.scaleX * ref.scaleX,
                            scaleY = m.scaleY * ref.scaleY,
                            rotation = m.rotation + ref.rotation,
                        ),
                        defs,
                        out,
                        depth + 1,
                    )
                }
                is JwwLine -> {
                    val (x1, y1) = xform(m.x1, m.y1, ref)
                    val (x2, y2) = xform(m.x2, m.y2, ref)
                    // Keep member attr (layer/color) — not the insert's
                    out.add(JwwLine(m.attr, x1, y1, x2, y2))
                }
                is JwwCircle -> {
                    val (cx, cy) = xform(m.cx, m.cy, ref)
                    val s = (kotlin.math.abs(ref.scaleX) + kotlin.math.abs(ref.scaleY)) * 0.5
                    val mirror = ref.scaleX * ref.scaleY < 0
                    out.add(
                        m.copy(
                            cx = cx,
                            cy = cy,
                            radius = m.radius * s,
                            // startAngle is radians (same as CadRenderer / CadSnap)
                            startAngle = m.startAngle + ref.rotation,
                            sweepAngle = if (mirror) -m.sweepAngle else m.sweepAngle,
                        ),
                    )
                }
                is JwwPoint -> {
                    val (x, y) = xform(m.x, m.y, ref)
                    out.add(JwwPoint(m.attr, x, y))
                }
                is JwwText -> {
                    val (x1, y1) = xform(m.x1, m.y1, ref)
                    val (x2, y2) = xform(m.x2, m.y2, ref)
                    out.add(
                        m.copy(
                            x1 = x1,
                            y1 = y1,
                            x2 = x2,
                            y2 = y2,
                            sizeX = m.sizeX * kotlin.math.abs(ref.scaleX),
                            sizeY = m.sizeY * kotlin.math.abs(ref.scaleY),
                            angleDeg = m.angleDeg + Math.toDegrees(ref.rotation),
                        ),
                    )
                }
                is JwwSolid -> {
                    val (x1, y1) = xform(m.x1, m.y1, ref)
                    val (x2, y2) = xform(m.x2, m.y2, ref)
                    val (x3, y3) = xform(m.x3, m.y3, ref)
                    val (x4, y4) = xform(m.x4, m.y4, ref)
                    out.add(JwwSolid(m.attr, x1, y1, x4, y4, x2, y2, x3, y3, m.rgb))
                }
            }
        }
    }

    private fun xform(px: Double, py: Double, ref: JwwBlockRef): Pair<Double, Double> {
        val x = px * ref.scaleX
        val y = py * ref.scaleY
        val c = cos(ref.rotation)
        val s = sin(ref.rotation)
        return (x * c - y * s + ref.x) to (x * s + y * c + ref.y)
    }
}
