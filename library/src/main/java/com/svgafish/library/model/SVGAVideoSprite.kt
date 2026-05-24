package com.svgafish.library.model

data class SVGAVideoSprite(
    val imageKey: String?,
    val matteKey: String?,
    val frames: List<SVGAVideoSpriteFrame>
)
