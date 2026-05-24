package com.svgafish.library.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.svgafish.library.drawer.SVGAFrameRenderer
import com.svgafish.library.session.SVGADynamicContent
import com.svgafish.library.session.SVGAVideoSession

open class SVGAPlayerDrawable(
    val videoSession: SVGAVideoSession,
    val dynamicItem: SVGADynamicContent
) : Drawable() {

    var cleared = true
        internal set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateSelf()
        }

    var currentFrame = 0
        internal set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateSelf()
        }

    var scaleType: ImageView.ScaleType = ImageView.ScaleType.MATRIX

    private val drawer = SVGAFrameRenderer(videoSession, dynamicItem)

    override fun draw(canvas: Canvas) {
        if (cleared) {
            return
        }
        drawer.drawFrame(canvas, currentFrame, scaleType)
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    fun resume() {
        videoSession.resumeAudio()
    }

    fun pause() {
        videoSession.pauseAudio()
    }

    fun stop() {
        videoSession.stopAudio()
    }

    fun clear() {
        videoSession.close()
    }
}
