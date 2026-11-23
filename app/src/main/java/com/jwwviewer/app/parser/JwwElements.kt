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
            if (!minX.isFinite() || widthOf(minX, maxX) <= 0.0) return null
            return JwwBounds(minX, minY, maxX, maxY)
        }

        private fun widthOf(a: Double, b: Double) = b - a
    }
}

data class JwwDrawing(
    val version: Int,
    val memo: String,
    val scales: DoubleArray,
    /** 256 layer visibility states: 0 hidden, >=1 visible */
    val layerStates: IntArray,
    val layerNames: Array<String>,
    val elements: List<JwwElement>,
    val bounds: JwwBounds?,
)
