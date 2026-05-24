package com.svgafish.library.drawer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.text.BoringLayout
import android.text.StaticLayout
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.graphics.withMatrix

internal class SpriteImageRenderer(
    private val renderContext: CanvasRenderContext,
    private val frameMatrixFactory: FrameMatrixFactory
) {

    private val drawTextCache = HashMap<String, Bitmap>()

    fun draw(sprite: FrameSpriteProvider.FrameSprite, canvas: Canvas) {
        val imageKey = sprite.imageKey ?: return
        if (renderContext.dynamicItem.dynamicHidden[imageKey] == true) {
            return
        }
        val drawingBitmap = resolveDrawingBitmap(imageKey) ?: return
        val frameMatrix = frameMatrixFactory.createFrameMatrix(sprite.frameEntity.transform)
        val paint = renderContext.sharedValues.sharedPaint()
        paint.isAntiAlias = renderContext.videoSession.antiAlias
        paint.isFilterBitmap = renderContext.videoSession.antiAlias
        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
        drawBitmap(sprite, canvas, drawingBitmap, frameMatrix, paint)
        updateClickArea(imageKey, drawingBitmap, frameMatrix)
        drawTextOnBitmap(canvas, drawingBitmap, sprite, frameMatrix)
    }

    private fun resolveDrawingBitmap(imageKey: String): Bitmap? {
        val bitmapKey = if (imageKey.endsWith(MATTE_SUFFIX)) {
            imageKey.substring(0, imageKey.length - MATTE_SUFFIX.length)
        } else {
            imageKey
        }
        return renderContext.dynamicItem.dynamicImage[bitmapKey]
            ?: renderContext.videoModel.imageMap[bitmapKey]
    }

    private fun drawBitmap(
        sprite: FrameSpriteProvider.FrameSprite,
        canvas: Canvas,
        drawingBitmap: Bitmap,
        frameMatrix: Matrix,
        paint: android.graphics.Paint
    ) {
        if (sprite.frameEntity.maskPath != null) {
            val maskPath = sprite.frameEntity.maskPath ?: return
            canvas.withSave {
                val path = renderContext.sharedValues.sharedPath()
                maskPath.buildPath(path)
                path.transform(frameMatrix)
                clipPath(path)
                frameMatrix.preScale(
                    (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat(),
                    (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
                )
                if (!drawingBitmap.isRecycled) {
                    drawBitmap(drawingBitmap, frameMatrix, paint)
                }
            }
            return
        }
        frameMatrix.preScale(
            (sprite.frameEntity.layout.width / drawingBitmap.width).toFloat(),
            (sprite.frameEntity.layout.height / drawingBitmap.height).toFloat()
        )
        if (!drawingBitmap.isRecycled) {
            canvas.drawBitmap(drawingBitmap, frameMatrix, paint)
        }
    }

    private fun updateClickArea(
        imageKey: String,
        drawingBitmap: Bitmap,
        frameMatrix: Matrix
    ) {
        renderContext.dynamicItem.dynamicIClickArea[imageKey]?.let { listener ->
            val matrixArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            frameMatrix.getValues(matrixArray)
            listener.onResponseArea(
                imageKey,
                matrixArray[2].toInt(),
                matrixArray[5].toInt(),
                (drawingBitmap.width * matrixArray[0] + matrixArray[2]).toInt(),
                (drawingBitmap.height * matrixArray[4] + matrixArray[5]).toInt()
            )
        }
    }

    private fun drawTextOnBitmap(
        canvas: Canvas,
        drawingBitmap: Bitmap,
        sprite: FrameSpriteProvider.FrameSprite,
        frameMatrix: Matrix
    ) {
        if (renderContext.dynamicItem.isTextDirty) {
            drawTextCache.clear()
            renderContext.dynamicItem.isTextDirty = false
        }
        val imageKey = sprite.imageKey ?: return
        val textBitmap = resolveTextBitmap(imageKey, drawingBitmap) ?: return
        val paint = renderContext.sharedValues.sharedPaint()
        paint.isAntiAlias = renderContext.videoSession.antiAlias
        paint.alpha = (sprite.frameEntity.alpha * 255).toInt()
        if (sprite.frameEntity.maskPath != null) {
            val maskPath = sprite.frameEntity.maskPath ?: return
            canvas.withMatrix(frameMatrix) {
                clipRect(0, 0, drawingBitmap.width, drawingBitmap.height)
                paint.shader =
                    BitmapShader(textBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val path = renderContext.sharedValues.sharedPath()
                maskPath.buildPath(path)
                drawPath(path, paint)
            }
            return
        }
        paint.isFilterBitmap = renderContext.videoSession.antiAlias
        canvas.drawBitmap(textBitmap, frameMatrix, paint)
    }

    private fun resolveTextBitmap(imageKey: String, drawingBitmap: Bitmap): Bitmap? {
        var textBitmap: Bitmap? = null
        renderContext.dynamicItem.dynamicText[imageKey]?.let { drawingText ->
            renderContext.dynamicItem.dynamicTextPaint[imageKey]?.let { drawingTextPaint ->
                textBitmap = drawTextCache[imageKey] ?: createPlainTextBitmap(
                    imageKey = imageKey,
                    drawingBitmap = drawingBitmap,
                    drawingText = drawingText,
                    drawingTextPaint = drawingTextPaint
                )
            }
        }
        renderContext.dynamicItem.dynamicBoringLayoutText[imageKey]?.let { layout ->
            textBitmap = drawTextCache[imageKey] ?: createBoringLayoutBitmap(
                imageKey = imageKey,
                drawingBitmap = drawingBitmap,
                layout = layout
            )
        }
        renderContext.dynamicItem.dynamicStaticLayoutText[imageKey]?.let { layout ->
            textBitmap = drawTextCache[imageKey] ?: createStaticLayoutBitmap(
                imageKey = imageKey,
                drawingBitmap = drawingBitmap,
                layout = layout
            )
        }
        return textBitmap
    }

    private fun createPlainTextBitmap(
        imageKey: String,
        drawingBitmap: Bitmap,
        drawingText: String,
        drawingTextPaint: android.text.TextPaint
    ): Bitmap {
        val bitmap = createBitmap(drawingBitmap.width, drawingBitmap.height, Bitmap.Config.ARGB_8888)
        val drawRect = Rect(0, 0, drawingBitmap.width, drawingBitmap.height)
        val textCanvas = Canvas(bitmap)
        drawingTextPaint.isAntiAlias = true
        val fontMetrics = drawingTextPaint.fontMetrics
        val baseLineY = drawRect.centerY() - fontMetrics.top / 2 - fontMetrics.bottom / 2
        textCanvas.drawText(
            drawingText,
            drawRect.centerX().toFloat(),
            baseLineY,
            drawingTextPaint
        )
        drawTextCache[imageKey] = bitmap
        return bitmap
    }

    private fun createBoringLayoutBitmap(
        imageKey: String,
        drawingBitmap: Bitmap,
        layout: BoringLayout
    ): Bitmap {
        layout.paint.isAntiAlias = true
        val bitmap = createBitmap(drawingBitmap.width, drawingBitmap.height, Bitmap.Config.ARGB_8888)
        val textCanvas = Canvas(bitmap)
        textCanvas.translate(0f, ((drawingBitmap.height - layout.height) / 2).toFloat())
        layout.draw(textCanvas)
        drawTextCache[imageKey] = bitmap
        return bitmap
    }

    private fun createStaticLayoutBitmap(
        imageKey: String,
        drawingBitmap: Bitmap,
        layout: StaticLayout
    ): Bitmap {
        layout.paint.isAntiAlias = true
        val renderedLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lineMax = try {
                val field = StaticLayout::class.java.getDeclaredField("mMaximumVisibleLineCount")
                field.isAccessible = true
                field.getInt(layout)
            } catch (_: Exception) {
                Int.MAX_VALUE
            }
            StaticLayout.Builder.obtain(
                layout.text,
                0,
                layout.text.length,
                layout.paint,
                drawingBitmap.width
            ).setAlignment(layout.alignment)
                .setMaxLines(lineMax)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        } else {
            StaticLayout(
                layout.text,
                0,
                layout.text.length,
                layout.paint,
                drawingBitmap.width,
                layout.alignment,
                layout.spacingMultiplier,
                layout.spacingAdd,
                false
            )
        }
        val bitmap = createBitmap(drawingBitmap.width, drawingBitmap.height, Bitmap.Config.ARGB_8888)
        val textCanvas = Canvas(bitmap)
        textCanvas.translate(0f, ((drawingBitmap.height - renderedLayout.height) / 2).toFloat())
        renderedLayout.draw(textCanvas)
        drawTextCache[imageKey] = bitmap
        return bitmap
    }

    private companion object {
        const val MATTE_SUFFIX = ".matte"
    }
}
