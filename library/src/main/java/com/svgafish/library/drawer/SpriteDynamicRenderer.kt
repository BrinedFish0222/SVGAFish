package com.svgafish.library.drawer

import android.graphics.Canvas

internal class SpriteDynamicRenderer(
    private val renderContext: CanvasRenderContext,
    private val frameMatrixFactory: FrameMatrixFactory
) {

    fun draw(
        sprite: FrameSpriteProvider.FrameSprite,
        canvas: Canvas,
        frameIndex: Int
    ) {
        val imageKey = sprite.imageKey ?: return
        val drawer = renderContext.dynamicItem.dynamicDrawer[imageKey]
        val sizedDrawer = renderContext.dynamicItem.dynamicDrawerSized[imageKey]
        if (drawer == null && sizedDrawer == null) {
            return
        }
        val frameMatrix = frameMatrixFactory.createFrameMatrix(sprite.frameEntity.transform)
        drawer?.let {
            canvas.save()
            canvas.concat(frameMatrix)
            it.invoke(canvas, frameIndex)
            canvas.restore()
        }
        sizedDrawer?.let {
            canvas.save()
            canvas.concat(frameMatrix)
            it.invoke(
                canvas,
                frameIndex,
                sprite.frameEntity.layout.width.toInt(),
                sprite.frameEntity.layout.height.toInt()
            )
            canvas.restore()
        }
    }
}
