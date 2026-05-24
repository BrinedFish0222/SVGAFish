package com.svgafish.library.model

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

data class SVGAVideoShape(
    val type: SVGAVideoShapeType,
    val args: Map<String, Any>,
    val styles: SVGAVideoShapeStyles?,
    val transform: Matrix?
) {
    var shapePath: Path? = null
        private set

    val isKeep: Boolean
        get() = type == SVGAVideoShapeType.KEEP

    fun buildPath() {
        if (shapePath != null) {
            return
        }

        val path = Path()
        when (type) {
            SVGAVideoShapeType.SHAPE -> {
                (args["d"] as? String)?.let {
                    SVGAVideoPath(it).buildPath(path)
                }
            }

            SVGAVideoShapeType.ELLIPSE -> {
                val x = (args["x"] as? Number)?.toFloat() ?: return
                val y = (args["y"] as? Number)?.toFloat() ?: return
                val radiusX = (args["radiusX"] as? Number)?.toFloat() ?: return
                val radiusY = (args["radiusY"] as? Number)?.toFloat() ?: return
                path.addOval(
                    RectF(x - radiusX, y - radiusY, x + radiusX, y + radiusY),
                    Path.Direction.CW
                )
            }

            SVGAVideoShapeType.RECT -> {
                val x = (args["x"] as? Number)?.toFloat() ?: return
                val y = (args["y"] as? Number)?.toFloat() ?: return
                val width = (args["width"] as? Number)?.toFloat() ?: return
                val height = (args["height"] as? Number)?.toFloat() ?: return
                val cornerRadius = (args["cornerRadius"] as? Number)?.toFloat() ?: return
                path.addRoundRect(
                    RectF(x, y, x + width, y + height),
                    cornerRadius,
                    cornerRadius,
                    Path.Direction.CW
                )
            }

            SVGAVideoShapeType.KEEP -> return
        }

        shapePath = path
    }
}

enum class SVGAVideoShapeType {
    SHAPE,
    RECT,
    ELLIPSE,
    KEEP
}

data class SVGAVideoShapeStyles(
    val fill: Int,
    val stroke: Int,
    val strokeWidth: Float,
    val lineCap: String,
    val lineJoin: String,
    val miterLimit: Int,
    val lineDash: FloatArray
)
