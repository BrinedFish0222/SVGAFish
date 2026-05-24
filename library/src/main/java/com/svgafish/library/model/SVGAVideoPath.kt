package com.svgafish.library.model

import android.graphics.Path
import java.util.StringTokenizer

private val VALID_PATH_METHODS: Set<String> = setOf(
    "M", "L", "H", "V", "C", "S", "Q", "R", "A", "Z",
    "m", "l", "h", "v", "c", "s", "q", "r", "a", "z"
)

/**
 * 可复用的路径描述，负责把 path string 构建为 Android Path。
 */
data class SVGAVideoPath(
    private val originValue: String
) {

    private val replacedValue: String =
        if (originValue.contains(",")) originValue.replace(",", " ") else originValue

    private var cachedPath: Path? = null

    fun buildPath(toPath: Path) {
        cachedPath?.let {
            toPath.set(it)
            return
        }

        val builtPath = Path()
        val segments = StringTokenizer(replacedValue, "MLHVCSQRAZmlhvcsqraz", true)
        var currentMethod = ""
        while (segments.hasMoreTokens()) {
            val segment = segments.nextToken()
            if (segment.isEmpty()) {
                continue
            }
            if (VALID_PATH_METHODS.contains(segment)) {
                currentMethod = segment
                if (currentMethod == "Z" || currentMethod == "z") {
                    operate(builtPath, currentMethod, StringTokenizer("", ""))
                }
            } else {
                operate(builtPath, currentMethod, StringTokenizer(segment, " "))
            }
        }
        cachedPath = builtPath
        toPath.set(builtPath)
    }

    private fun operate(finalPath: Path, method: String, args: StringTokenizer) {
        var x0 = 0.0f
        var y0 = 0.0f
        var x1 = 0.0f
        var y1 = 0.0f
        var x2 = 0.0f
        var y2 = 0.0f
        try {
            var index = 0
            while (args.hasMoreTokens()) {
                val token = args.nextToken()
                if (token.isEmpty()) {
                    continue
                }
                when (index) {
                    0 -> x0 = token.toFloat()
                    1 -> y0 = token.toFloat()
                    2 -> x1 = token.toFloat()
                    3 -> y1 = token.toFloat()
                    4 -> x2 = token.toFloat()
                    5 -> y2 = token.toFloat()
                }
                index++
            }
        } catch (_: Exception) {
        }

        var currentX = 0.0f
        var currentY = 0.0f
        if (method == "M") {
            finalPath.moveTo(x0, y0)
            currentX = x0
            currentY = y0
        } else if (method == "m") {
            finalPath.rMoveTo(x0, y0)
            currentX += x0
            currentY += y0
        }
        if (method == "L") {
            finalPath.lineTo(x0, y0)
        } else if (method == "l") {
            finalPath.rLineTo(x0, y0)
        }
        if (method == "C") {
            finalPath.cubicTo(x0, y0, x1, y1, x2, y2)
        } else if (method == "c") {
            finalPath.rCubicTo(x0, y0, x1, y1, x2, y2)
        }
        if (method == "Q") {
            finalPath.quadTo(x0, y0, x1, y1)
        } else if (method == "q") {
            finalPath.rQuadTo(x0, y0, x1, y1)
        }
        if (method == "H") {
            finalPath.lineTo(x0, currentY)
        } else if (method == "h") {
            finalPath.rLineTo(x0, 0f)
        }
        if (method == "V") {
            finalPath.lineTo(currentX, x0)
        } else if (method == "v") {
            finalPath.rLineTo(0f, x0)
        }
        if (method == "Z" || method == "z") {
            finalPath.close()
        }
    }
}
