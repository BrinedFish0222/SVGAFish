package com.svgafish.library.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import com.svgafish.library.R
import com.svgafish.library.callback.SVGACallback
import com.svgafish.library.callback.SVGAClickAreaListener
import com.svgafish.library.session.SVGADynamicContent
import com.svgafish.library.session.SVGAVideoSession
import com.svgafish.library.utils.SVGARange
import java.lang.ref.WeakReference

open class SVGAPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    enum class FillMode {
        Backward,
        Forward,
        Clear,
    }

    var isAnimating = false
        private set

    var loops = 0

    var isMuted: Boolean = false
        set(value) {
            field = value
            val vol = if (value) 0f else 1f
            getPlayerDrawable()?.videoSession?.setAudioVolume(vol)
        }

    var fillMode: FillMode = FillMode.Forward
    var callback: SVGACallback? = null

    private var mAnimator: ValueAnimator? = null
    private var mItemClickAreaListener: SVGAClickAreaListener? = null
    private var mAntiAlias = true
    private val mAnimatorListener = AnimatorListener(this)
    private val mAnimatorUpdateListener = AnimatorUpdateListener(this)
    private var mStartFrame = 0
    private var mEndFrame = 0

    init {
        attrs?.let { loadAttrs(it) }
    }

    private fun loadAttrs(attrs: AttributeSet) {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.SVGAImageView, 0, 0)
        loops = typedArray.getInt(R.styleable.SVGAImageView_loopCount, 0)
        mAntiAlias = typedArray.getBoolean(R.styleable.SVGAImageView_antiAlias, true)
        typedArray.getString(R.styleable.SVGAImageView_fillMode)?.let {
            when (it) {
                "0" -> fillMode = FillMode.Backward
                "1" -> fillMode = FillMode.Forward
                "2" -> fillMode = FillMode.Clear
            }
        }
        typedArray.recycle()
    }

    fun startAnimation() {
        startAnimation(null, false)
    }

    fun startAnimation(range: SVGARange?, reverse: Boolean = false) {
        stopAnimation(false)

        getPlayerDrawable()?.let { drawable ->
            val videoModel = drawable.videoSession.model
            val fps = videoModel.fps
            val scale = generateScale()
            if (fps != 0 && scale != 0.0) {
                drawable.cleared = false
                drawable.scaleType = scaleType
                mStartFrame = 0.coerceAtLeast(range?.location ?: 0)
                mEndFrame = (videoModel.frames - 1).coerceAtMost(
                    ((range?.location ?: 0) + (range?.length ?: Int.MAX_VALUE) - 1)
                )
                val animator = ValueAnimator.ofInt(mStartFrame, mEndFrame)
                animator.interpolator = LinearInterpolator()
                animator.duration = ((mEndFrame - mStartFrame + 1) * (1000 / fps) / scale).toLong()
                animator.repeatCount = if (loops <= 0) 99999 else loops - 1
                animator.addUpdateListener(mAnimatorUpdateListener)
                animator.addListener(mAnimatorListener)
                if (reverse) {
                    animator.reverse()
                } else {
                    animator.start()
                }
                mAnimator = animator
            }
        }
    }

    private fun getPlayerDrawable(): SVGAPlayerDrawable? {
        return drawable as? SVGAPlayerDrawable
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    private fun generateScale(): Double {
        var scale = 1.0
        try {
            val animatorClass = Class.forName("android.animation.ValueAnimator") ?: return scale
            val getMethod = animatorClass.getDeclaredMethod("getDurationScale") ?: return scale
            scale = (getMethod.invoke(animatorClass) as Float).toDouble()
            if (scale == 0.0) {
                val setMethod = animatorClass.getDeclaredMethod("setDurationScale", Float::class.java)
                    ?: return scale
                setMethod.isAccessible = true
                setMethod.invoke(animatorClass, 1.0f)
                scale = 1.0
            }
        } catch (ignore: Exception) {
            ignore.printStackTrace()
        }
        return scale
    }

    private fun onAnimatorUpdate(animator: ValueAnimator) {
        getPlayerDrawable()?.let { drawable ->
            val frames = drawable.videoSession.model.frames.toDouble()
            if (frames != 0.0) {
                drawable.currentFrame = animator.animatedValue as Int
                val percentage = (drawable.currentFrame + 1).toDouble() / frames
                callback?.onStep(drawable.currentFrame, percentage)
            }
        }
    }

    private fun onAnimationEnd(animation: Animator) {
        isAnimating = false
        stopAnimation()
        val drawable = getPlayerDrawable()
        if (drawable != null) {
            when (fillMode) {
                FillMode.Backward -> drawable.currentFrame = mStartFrame
                FillMode.Forward -> drawable.currentFrame = mEndFrame
                FillMode.Clear -> drawable.cleared = true
            }
            drawable.videoSession.notifyPlaybackFinished()
        }
        callback?.onFinished()
    }

    fun clear() {
        stopAnimation(false)
        setImageDrawable(null)
    }

    fun pauseAnimation() {
        stopAnimation(false)
        callback?.onPause()
    }

    fun stopAnimation() {
        stopAnimation(clear = false)
    }

    fun stopAnimation(clear: Boolean) {
        mAnimator?.cancel()
        mAnimator?.removeAllListeners()
        mAnimator?.removeAllUpdateListeners()
        getPlayerDrawable()?.stop()
        getPlayerDrawable()?.cleared = clear
    }

    fun setVideoSession(
        videoSession: SVGAVideoSession?,
        dynamicItem: SVGADynamicContent = SVGADynamicContent()
    ) {
        stopAnimation(false)
        if (videoSession == null) {
            setImageDrawable(null)
            return
        }
        videoSession.antiAlias = mAntiAlias
        if (isMuted) {
            videoSession.setAudioVolume(0f)
        }
        val drawable = SVGAPlayerDrawable(videoSession, dynamicItem)
        drawable.cleared = true
        setImageDrawable(drawable)
    }

    fun stepToFrame(frame: Int, andPlay: Boolean) {
        pauseAnimation()
        val drawable = getPlayerDrawable() ?: return
        drawable.currentFrame = frame
        if (andPlay) {
            startAnimation()
            mAnimator?.let {
                it.currentPlayTime = (
                    0.0f.coerceAtLeast(
                        1.0f.coerceAtMost(frame.toFloat() / drawable.videoSession.model.frames.toFloat())
                    ) * it.duration
                ).toLong()
            }
        }
    }

    fun stepToPercentage(percentage: Double, andPlay: Boolean) {
        val drawable = getPlayerDrawable() ?: return
        val frames = drawable.videoSession.model.frames
        var frame = (frames * percentage).toInt()
        if (frame >= frames && frame > 0) {
            frame = frames - 1
        }
        stepToFrame(frame, andPlay)
    }

    fun setOnAnimKeyClickListener(clickListener: SVGAClickAreaListener) {
        mItemClickAreaListener = clickListener
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }
        val drawable = getPlayerDrawable() ?: return super.onTouchEvent(event)
        for ((key, value) in drawable.dynamicItem.mClickMap) {
            if (event.x >= value[0] && event.x <= value[2] && event.y >= value[1] && event.y <= value[3]) {
                mItemClickAreaListener?.let {
                    it.onClick(key)
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clear()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        val previousDrawable = this.drawable as? SVGAPlayerDrawable
        if (previousDrawable != null && previousDrawable !== drawable) {
            previousDrawable.clear()
        }
        super.setImageDrawable(drawable)
    }

    private class AnimatorListener(view: SVGAPlayerView) : Animator.AnimatorListener {
        private val weakReference = WeakReference(view)

        override fun onAnimationRepeat(animation: Animator) {
            weakReference.get()?.callback?.onRepeat()
        }

        override fun onAnimationEnd(animation: Animator) {
            weakReference.get()?.onAnimationEnd(animation)
        }

        override fun onAnimationCancel(animation: Animator) {
            weakReference.get()?.isAnimating = false
        }

        override fun onAnimationStart(animation: Animator) {
            weakReference.get()?.isAnimating = true
        }
    }

    private class AnimatorUpdateListener(view: SVGAPlayerView) : ValueAnimator.AnimatorUpdateListener {
        private val weakReference = WeakReference(view)

        override fun onAnimationUpdate(animation: ValueAnimator) {
            weakReference.get()?.onAnimatorUpdate(animation)
        }
    }
}
