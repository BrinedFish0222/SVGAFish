package com.svga.fish

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.opensource.svgaplayer.SVGACache
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.svga.fish.databinding.ActivityOldApiDeduplicatedNetworkLoadsBinding
import java.net.URL

class OldApiDeduplicatedNetworkLoadsActivity : AppCompatActivity() {

    companion object {
        private const val SHARED_URL = "https://github.com/yyued/SVGA-Samples/blob/master/posche.svga?raw=true"
    }

    private var _binding: ActivityOldApiDeduplicatedNetworkLoadsBinding? = null
    private val binding get() = _binding!!

    private val parser by lazy {
        SVGACache.onCreate(this, SVGACache.Type.FILE)
        SVGAParser(this)
    }

    private val sharedUrl = URL(SHARED_URL)
    private val activeCancelFns = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOldApiDeduplicatedNetworkLoadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.example_old_api_deduplicated_network_loads)

        startSameUrlLoads()
    }

    override fun onDestroy() {
        cancelActiveLoads()
        _binding = null
        super.onDestroy()
    }

    private fun startSameUrlLoads() {
        cancelActiveLoads()

        val playerViews = listOf(
            binding.playerOneView,
            binding.playerTwoView,
            binding.playerThreeView
        )

        playerViews.forEach { view ->
            view.clear()

            val cancelFn = parser.decodeFromURL(sharedUrl, object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    view.setVideoItem(videoItem)
                    view.startAnimation()
                }

                override fun onError() {
                    // No-op: animation simply doesn't play if loading fails
                }
            })

            if (cancelFn != null) {
                activeCancelFns += cancelFn
            }
        }
    }

    private fun cancelActiveLoads() {
        activeCancelFns.forEach { it.invoke() }
        activeCancelFns.clear()
    }
}
