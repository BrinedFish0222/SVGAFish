package com.svgafish.library.drawer

import android.graphics.Canvas
import android.graphics.Path
import com.svgafish.library.model.SVGAVideoShape

internal class PathCache {

    private var canvasWidth: Int = 0
    private var canvasHeight: Int = 0
    private val cache = HashMap<SVGAVideoShape, Path>()

    fun onSizeChanged(canvas: Canvas) {
        if (canvasWidth != canvas.width || canvasHeight != canvas.height) {
            cache.clear()
        }
        canvasWidth = canvas.width
        canvasHeight = canvas.height
    }

    fun buildPath(shape: SVGAVideoShape): Path {
        if (!cache.containsKey(shape)) {
            val path = Path()
            shape.shapePath?.let(path::set)
            cache[shape] = path
        }
        return checkNotNull(cache[shape])
    }
}
