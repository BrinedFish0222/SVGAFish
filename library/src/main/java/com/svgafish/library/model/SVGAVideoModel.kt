package com.svgafish.library.model

import android.graphics.Bitmap
import com.svgafish.library.utils.SVGARect

/**
 * 解析后的只读共享模型，承载可缓存、可复用的静态数据与资源。
 */
class SVGAVideoModel internal constructor(
    internal val requestKey: String,
    val videoSize: SVGARect,
    val fps: Int,
    val frames: Int,
    val spriteList: List<SVGAVideoSprite>,
    val imageMap: Map<String, Bitmap>,
    val audioTracks: List<SVGAAudioTrack>
)
