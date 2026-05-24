package com.svgafish.library.drawer

import com.svgafish.library.utils.SVGAScaleInfo
import com.svgafish.library.session.SVGADynamicContent
import com.svgafish.library.model.SVGAVideoModel
import com.svgafish.library.session.SVGAVideoSession

internal class CanvasRenderContext(
    val videoSession: SVGAVideoSession,
    val dynamicItem: SVGADynamicContent,
    val videoModel: SVGAVideoModel = videoSession.model,
    val scaleInfo: SVGAScaleInfo = SVGAScaleInfo(),
    val sharedValues: ShareValues = ShareValues(),
    val pathCache: PathCache = PathCache()
)
