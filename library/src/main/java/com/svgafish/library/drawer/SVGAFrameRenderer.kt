package com.svgafish.library.drawer

import android.graphics.Canvas
import android.os.Build
import android.widget.ImageView
import com.svgafish.library.session.SVGADynamicContent
import com.svgafish.library.session.SVGAVideoSession

internal class SVGAFrameRenderer(
    private val videoSession: SVGAVideoSession,
    private val dynamicItem: SVGADynamicContent
) {

    private val renderContext = CanvasRenderContext(
        videoSession = videoSession,
        dynamicItem = dynamicItem
    )
    private val frameSpriteProvider = FrameSpriteProvider(renderContext.videoModel)
    private val mattePlanner = FrameMattePlanner()
    private val frameMatrixFactory = FrameMatrixFactory(
        scaleInfo = renderContext.scaleInfo,
        sharedValues = renderContext.sharedValues
    )
    private val imageRenderer = SpriteImageRenderer(renderContext, frameMatrixFactory)
    private val shapeRenderer = SpriteShapeRenderer(renderContext, frameMatrixFactory)
    private val dynamicRenderer = SpriteDynamicRenderer(renderContext, frameMatrixFactory)

    fun drawFrame(canvas: Canvas, frameIndex: Int, scaleType: ImageView.ScaleType) {
        renderContext.scaleInfo.performScaleType(
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            renderContext.videoModel.videoSize.width.toFloat(),
            renderContext.videoModel.videoSize.height.toFloat(),
            scaleType
        )
        videoSession.playAudio(frameIndex)
        renderContext.pathCache.onSizeChanged(canvas)
        frameSpriteProvider.withFrameSprites(frameIndex) { sprites ->
            if (sprites.isEmpty()) {
                return@withFrameSprites
            }
            val mattePlan = mattePlanner.plan(sprites)
            if (!mattePlan.hasMatteLayer || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                sprites.forEach { sprite ->
                    drawSprite(sprite, canvas, frameIndex)
                }
                return@withFrameSprites
            }
            var saveID = -1
            sprites.forEachIndexed { index, sprite ->
                if (mattePlan.isMatteSprite(sprite)) {
                    return@forEachIndexed
                }
                if (mattePlan.isMatteBegin(index)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        saveID = canvas.saveLayer(
                            0f,
                            0f,
                            canvas.width.toFloat(),
                            canvas.height.toFloat(),
                            null
                        )
                    } else {
                        canvas.save()
                    }
                }
                drawSprite(sprite, canvas, frameIndex)
                if (mattePlan.isMatteEnd(index)) {
                    mattePlan.matteSpriteFor(sprite.matteKey)?.let { matteSprite ->
                        drawSprite(
                            matteSprite,
                            renderContext.sharedValues.shareMatteCanvas(canvas.width, canvas.height),
                            frameIndex
                        )
                        canvas.drawBitmap(
                            renderContext.sharedValues.sharedMatteBitmap(),
                            0f,
                            0f,
                            renderContext.sharedValues.shareMattePaint()
                        )
                        if (saveID != -1) {
                            canvas.restoreToCount(saveID)
                        } else {
                            canvas.restore()
                        }
                        return@forEachIndexed
                    }
                }
            }
        }
    }

    private fun drawSprite(
        sprite: FrameSpriteProvider.FrameSprite, canvas: Canvas, frameIndex: Int
    ) {
        imageRenderer.draw(sprite, canvas)
        shapeRenderer.draw(sprite, canvas)
        dynamicRenderer.draw(sprite, canvas, frameIndex)
    }
}
