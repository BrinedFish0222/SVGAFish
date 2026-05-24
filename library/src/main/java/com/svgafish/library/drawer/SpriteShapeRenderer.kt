package com.svgafish.library.drawer

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint

internal class SpriteShapeRenderer(
    private val renderContext: CanvasRenderContext,
    private val frameMatrixFactory: FrameMatrixFactory
) {

    fun draw(sprite: FrameSpriteProvider.FrameSprite, canvas: Canvas) {
        val frameMatrix = frameMatrixFactory.createFrameMatrix(sprite.frameEntity.transform)
        sprite.frameEntity.shapes.forEach { shape ->
            shape.buildPath()
            shape.shapePath?.let {
                val paint = renderContext.sharedValues.sharedPaint()
                paint.reset()
                paint.isAntiAlias = renderContext.videoSession.antiAlias
                paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
                val path = renderContext.sharedValues.sharedPath()
                path.reset()
                path.addPath(renderContext.pathCache.buildPath(shape))
                val shapeMatrix = renderContext.sharedValues.sharedMatrix2()
                shapeMatrix.reset()
                shape.transform?.let(shapeMatrix::postConcat)
                shapeMatrix.postConcat(frameMatrix)
                path.transform(shapeMatrix)
                shape.styles?.fill?.let { fill ->
                    if (fill != 0x00000000) {
                        paint.style = Paint.Style.FILL
                        paint.color = fill
                        val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
                        if (alpha != 255) {
                            paint.alpha = alpha
                        }
                        withMaskPath(canvas, sprite, frameMatrix) {
                            canvas.drawPath(path, paint)
                        }
                    }
                }
                shape.styles?.strokeWidth?.let { strokeWidth ->
                    if (strokeWidth > 0) {
                        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
                        paint.style = Paint.Style.STROKE
                        shape.styles?.stroke?.let { stroke ->
                            paint.color = stroke
                            val alpha = (sprite.frameEntity.alpha * 255).toInt().coerceIn(0, 255)
                            if (alpha != 255) {
                                paint.alpha = alpha
                            }
                        }
                        val scale = frameMatrixFactory.matrixScale(frameMatrix)
                        paint.strokeWidth = strokeWidth * scale
                        shape.styles?.lineCap?.let {
                            when {
                                it.equals("butt", true) -> paint.strokeCap = Paint.Cap.BUTT
                                it.equals("round", true) -> paint.strokeCap = Paint.Cap.ROUND
                                it.equals("square", true) -> paint.strokeCap = Paint.Cap.SQUARE
                            }
                        }
                        shape.styles?.lineJoin?.let {
                            when {
                                it.equals("miter", true) -> paint.strokeJoin = Paint.Join.MITER
                                it.equals("round", true) -> paint.strokeJoin = Paint.Join.ROUND
                                it.equals("bevel", true) -> paint.strokeJoin = Paint.Join.BEVEL
                            }
                        }
                        shape.styles?.miterLimit?.let {
                            paint.strokeMiter = it.toFloat() * scale
                        }
                        shape.styles?.lineDash?.let {
                            if (it.size == 3 && (it[0] > 0 || it[1] > 0)) {
                                paint.pathEffect = DashPathEffect(
                                    floatArrayOf(
                                        (if (it[0] < 1.0f) 1.0f else it[0]) * scale,
                                        (if (it[1] < 0.1f) 0.1f else it[1]) * scale
                                    ),
                                    it[2] * scale
                                )
                            }
                        }
                        withMaskPath(canvas, sprite, frameMatrix) {
                            canvas.drawPath(path, paint)
                        }
                    }
                }
            }
        }
    }

    private inline fun withMaskPath(
        canvas: Canvas,
        sprite: FrameSpriteProvider.FrameSprite,
        frameMatrix: Matrix,
        block: () -> Unit
    ) {
        val maskPath = sprite.frameEntity.maskPath
        if (maskPath == null) {
            block()
            return
        }
        canvas.save()
        val path = renderContext.sharedValues.sharedPath2()
        maskPath.buildPath(path)
        path.transform(frameMatrix)
        canvas.clipPath(path)
        block()
        canvas.restore()
    }
}
