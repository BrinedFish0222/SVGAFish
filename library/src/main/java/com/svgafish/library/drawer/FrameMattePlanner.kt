package com.svgafish.library.drawer

internal class FrameMattePlanner {

    fun plan(sprites: List<FrameSpriteProvider.FrameSprite>): FrameMattePlan {
        val hasMatteLayer = sprites.firstOrNull()?.imageKey?.endsWith(MATTE_SUFFIX) == true
        if (!hasMatteLayer) {
            return FrameMattePlan(
                hasMatteLayer = false,
                matteSprites = emptyMap(),
                beginFlags = BooleanArray(sprites.size),
                endFlags = BooleanArray(sprites.size)
            )
        }
        return FrameMattePlan(
            hasMatteLayer = true,
            matteSprites = collectMatteSprites(sprites),
            beginFlags = buildBeginFlags(sprites),
            endFlags = buildEndFlags(sprites)
        )
    }

    private fun collectMatteSprites(
        sprites: List<FrameSpriteProvider.FrameSprite>
    ): Map<String, FrameSpriteProvider.FrameSprite> {
        val matteSprites = LinkedHashMap<String, FrameSpriteProvider.FrameSprite>()
        sprites.forEach { sprite ->
            val imageKey = sprite.imageKey ?: return@forEach
            if (imageKey.endsWith(MATTE_SUFFIX)) {
                matteSprites[imageKey] = sprite
            }
        }
        return matteSprites
    }

    private fun buildBeginFlags(sprites: List<FrameSpriteProvider.FrameSprite>): BooleanArray {
        val beginFlags = BooleanArray(sprites.size)
        sprites.forEachIndexed { index, sprite ->
            if (isMatteSprite(sprite)) {
                return@forEachIndexed
            }
            val matteKey = sprite.matteKey
            if (matteKey.isNullOrEmpty()) {
                return@forEachIndexed
            }
            val lastSprite = sprites.getOrNull(index - 1)
            if (lastSprite == null || lastSprite.matteKey.isNullOrEmpty() || lastSprite.matteKey != matteKey) {
                beginFlags[index] = true
            }
        }
        return beginFlags
    }

    private fun buildEndFlags(sprites: List<FrameSpriteProvider.FrameSprite>): BooleanArray {
        val endFlags = BooleanArray(sprites.size)
        sprites.forEachIndexed { index, sprite ->
            if (isMatteSprite(sprite)) {
                return@forEachIndexed
            }
            val matteKey = sprite.matteKey
            if (matteKey.isNullOrEmpty()) {
                return@forEachIndexed
            }
            val nextSprite = sprites.getOrNull(index + 1)
            if (nextSprite == null || nextSprite.matteKey.isNullOrEmpty() || nextSprite.matteKey != matteKey) {
                endFlags[index] = true
            }
        }
        return endFlags
    }

    private fun isMatteSprite(sprite: FrameSpriteProvider.FrameSprite): Boolean {
        return sprite.imageKey?.endsWith(MATTE_SUFFIX) == true
    }

    internal data class FrameMattePlan(
        val hasMatteLayer: Boolean,
        val matteSprites: Map<String, FrameSpriteProvider.FrameSprite>,
        private val beginFlags: BooleanArray,
        private val endFlags: BooleanArray
    ) {
        fun isMatteBegin(index: Int): Boolean = beginFlags.getOrElse(index) { false }

        fun isMatteEnd(index: Int): Boolean = endFlags.getOrElse(index) { false }

        fun isMatteSprite(sprite: FrameSpriteProvider.FrameSprite): Boolean {
            return sprite.imageKey?.endsWith(MATTE_SUFFIX) == true
        }

        fun matteSpriteFor(matteKey: String?): FrameSpriteProvider.FrameSprite? {
            return matteSprites[matteKey]
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FrameMattePlan

            if (hasMatteLayer != other.hasMatteLayer) return false
            if (matteSprites != other.matteSprites) return false
            if (!beginFlags.contentEquals(other.beginFlags)) return false
            if (!endFlags.contentEquals(other.endFlags)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = hasMatteLayer.hashCode()
            result = 31 * result + matteSprites.hashCode()
            result = 31 * result + beginFlags.contentHashCode()
            result = 31 * result + endFlags.contentHashCode()
            return result
        }
    }

    private companion object {
        const val MATTE_SUFFIX = ".matte"
    }
}
