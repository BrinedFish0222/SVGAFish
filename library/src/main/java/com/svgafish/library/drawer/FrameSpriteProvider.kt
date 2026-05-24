package com.svgafish.library.drawer

import com.svgafish.library.utils.Pools
import com.svgafish.library.model.SVGAVideoModel
import com.svgafish.library.model.SVGAVideoSpriteFrame
import kotlin.math.max

internal class FrameSpriteProvider(
    private val videoModel: SVGAVideoModel
) {

    private val spritePool = Pools.SimplePool<FrameSprite>(max(1, videoModel.spriteList.size))

    internal class FrameSprite {
        var matteKey: String? = null
            private set

        var imageKey: String? = null
            private set

        private var boundFrameEntity: SVGAVideoSpriteFrame? = null

        val frameEntity: SVGAVideoSpriteFrame
            get() = checkNotNull(boundFrameEntity)

        fun bind(
            matteKey: String?,
            imageKey: String?,
            frameEntity: SVGAVideoSpriteFrame
        ): FrameSprite = apply {
            this.matteKey = matteKey
            this.imageKey = imageKey
            this.boundFrameEntity = frameEntity
        }

        fun reset() {
            matteKey = null
            imageKey = null
            boundFrameEntity = null
        }
    }

    fun <R> withFrameSprites(frameIndex: Int, block: (List<FrameSprite>) -> R): R {
        val sprites = requestFrameSprites(frameIndex)
        return try {
            block(sprites)
        } finally {
            releaseFrameSprites(sprites)
        }
    }

    private fun requestFrameSprites(frameIndex: Int): List<FrameSprite> {
        return videoModel.spriteList.mapNotNull { sprite ->
            if (frameIndex < 0 || frameIndex >= sprite.frames.size) {
                return@mapNotNull null
            }
            val imageKey = sprite.imageKey ?: return@mapNotNull null
            val frameEntity = sprite.frames[frameIndex]
            if (!imageKey.endsWith(".matte") && frameEntity.alpha <= 0.0) {
                return@mapNotNull null
            }
            (spritePool.acquire() ?: FrameSprite()).bind(
                matteKey = sprite.matteKey,
                imageKey = imageKey,
                frameEntity = frameEntity
            )
        }
    }

    private fun releaseFrameSprites(sprites: List<FrameSprite>) {
        sprites.forEach { sprite ->
            sprite.reset()
            spritePool.release(sprite)
        }
    }
}
