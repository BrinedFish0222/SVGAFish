package com.svgafish.library.model

import android.graphics.Matrix
import com.svgafish.library.utils.SVGARect

data class SVGAVideoSpriteFrame(
    val alpha: Double,
    val layout: SVGARect,
    val transform: Matrix,
    val maskPath: SVGAVideoPath?,
    val shapes: List<SVGAVideoShape>
)
