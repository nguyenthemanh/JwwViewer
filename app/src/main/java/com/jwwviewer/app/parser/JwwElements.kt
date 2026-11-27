package com.jwwviewer.app.parser

data class JwwAttr(
    val group: Int,
    val penStyle: Int,
    val color: Int,
    val penWidth: Int,
    val layer: Int,
    val gLayer: Int,
    val flags: Int,
)

sealed class JwwElement {
    abstract val attr: JwwAttr
    val layerIndex: Int get() = attr.gLayer * 16 + attr.layer
}

data class JwwLine(
    override val attr: JwwAttr,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
) : JwwElement()

data class JwwCircle(
    override val attr: JwwAttr,
    val cx: Double,
    val cy: Double,
    val radius: Double,
    val startAngle: Double,
    val sweepAngle: Double,
    val tilt: Double,
    val flatness: Double,
    val fullCircle: Boolean,
) : JwwElement()

data class JwwPoint(
    override val attr: JwwAttr,
    val x: Double,
    val y: Double,
) : JwwElement()

data class JwwText(
    override val attr: JwwAttr,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val style: Int,
    val sizeX: Double,
    val sizeY: Double,
    val spacing: Double,
    val angleDeg: Double,
    val fontName: String,
    val text: String,
) : JwwElement()

data class JwwSolid(
    override val attr: JwwAttr,
    val x1: Double,
    val y1: Double,
    val x4: Double,
    val y4: Double,
    val x2: Double,
    val y2: Double,
    val x3: Double,
    val y3: Double,
    val rgb: Int?,
) : JwwElement()

data class JwwBlockRef(
    override val attr: JwwAttr,
    val x: Double,
    val y: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotation: Double,
    val blockNumber: Int,
) : JwwElement()

data class JwwBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY

    companion object {
        fun from(elements: List<JwwElement>): JwwBounds? {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            fun add(x: Double, y: Double) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
            for (e in elements) {
                when (e) {
                    is JwwLine -> {
                        add(e.x1, e.y1); add(e.x2, e.y2)
                    }
                    is JwwCircle -> {
                        add(e.cx - e.radius, e.cy - e.radius)
                        add(e.cx + e.radius, e.cy + e.radius)
                    }
                    is JwwPoint -> add(e.x, e.y)
                    is JwwText -> {
                        add(e.x1, e.y1); add(e.x2, e.y2)
                    }
                    is JwwSolid -> {
                        add(e.x1, e.y1); add(e.x2, e.y2)
                        add(e.x3, e.y3); add(e.x4, e.y4)
                    }
                    is JwwBlockRef -> add(e.x, e.y)
                }
            }
            if (!minX.isFinite() || !maxX.isFinite()) return null
            // Degenerate (point / vertical / horizontal only): pad so fit/zoom still works
            var x0 = minX
            var x1 = maxX
            var y0 = minY
            var y1 = maxY
            if (x1 < x0 || y1 < y0) return null
            val pad = 1.0
            if (x1 - x0 < 1e-9) {
                x0 -= pad
                x1 += pad
            }
            if (y1 - y0 < 1e-9) {
                y0 -= pad
                y1 += pad
            }
            return JwwBounds(x0, y0, x1, y1)
        }
    }
}

data class JwwDrawing(
    val version: Int,
    val memo: String,
    val scales: DoubleArray,
    /** Active scale group index (0..15) from JWW header. */
    val writeGroup: Int,
    /** 256 layer visibility states: 0 hidden, >=1 visible */
    val layerStates: IntArray,
    val layerNames: Array<String>,
    val elements: List<JwwElement>,
    val bounds: JwwBounds?,
    /** Block definitions by JW_CAD serial number (CDataList.m_nNumber). */
    val blockDefs: Map<Int, List<JwwElement>> = emptyMap(),
    /** True if parse stopped early (unknown entity / truncated stream). */
    val truncated: Boolean = false,
) {
    /**
     * JW_CAD scale denominator S for the write group (display 1:S).
     * File coordinates are **paper mm (図寸)**; real mm (実寸) = paper × S.
     */
    val activeScale: Double
        get() = scaleForGroup(writeGroup)

    /** Scale denominator for layer group 0..15 (1:S). Invalid → 1. */
    fun scaleForGroup(gLayer: Int): Double {
        val s = scales.getOrElse(gLayer.coerceIn(0, 15)) { 1.0 }
        return if (s.isFinite() && s > 0.0) s else 1.0
    }
}
