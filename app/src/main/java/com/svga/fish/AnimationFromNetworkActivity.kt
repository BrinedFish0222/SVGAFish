package com.svga.fish

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.svgafish.library.view.SVGAPlayerView
import com.svgafish.library.SVGAResourceManager
import com.svgafish.library.session.SVGAVideoSession
import java.net.MalformedURLException
import java.net.URL

class AnimationFromNetworkActivity : AppCompatActivity() {

    private lateinit var animationView: SVGAPlayerView
    private lateinit var muteButton: Button
    private lateinit var svgaResourceManager: SVGAResourceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        svgaResourceManager = SVGAResourceManager.create(this)

        animationView = SVGAPlayerView(this).apply {
            setBackgroundColor(Color.GRAY)
        }

        muteButton = Button(this).apply {
            text = "Mute: OFF"
            setOnClickListener {
                animationView.isMuted = !animationView.isMuted
                text = if (animationView.isMuted) "Mute: ON" else "Mute: OFF"
            }
        }

        val container = FrameLayout(this).apply {
            addView(animationView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(muteButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 40
            })
        }

        setContentView(container)
        loadAnimation()
    }

    private fun loadAnimation() {
        try {
            svgaResourceManager.loadFromURL(
                URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/mp3_to_long.svga"),
                object : SVGAResourceManager.LoadCompletion {
                    override fun onComplete(session: SVGAVideoSession) {
                        Log.d("SVGA", "AnimationFromNetworkActivity load onComplete")
                        animationView.setVideoSession(session)
                        animationView.startAnimation()
                    }

                    override fun onError() {
                        Log.e("SVGA", "AnimationFromNetworkActivity load failed")
                    }
                }
            )
        } catch (error: MalformedURLException) {
            Log.e("SVGA", "Invalid SVGA URL", error)
        }
    }
}
