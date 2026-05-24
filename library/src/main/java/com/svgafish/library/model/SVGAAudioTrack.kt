package com.svgafish.library.model

import java.io.File

/**
 * 解析阶段产出的音频静态描述，可被多个播放 session 共享。
 */
data class SVGAAudioTrack(
    val audioKey: String,
    val startFrame: Int,
    val endFrame: Int,
    val startTime: Int,
    val totalTime: Int,
    val file: File?
)
