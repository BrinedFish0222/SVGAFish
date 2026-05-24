package com.svga.fish

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.svga.fish.databinding.ActivityDeduplicatedNetworkLoadsBinding
import com.svgafish.library.SVGAResourceManager
import com.svgafish.library.session.SVGAVideoSession
import java.net.URL

class DeduplicatedNetworkLoadsActivity : AppCompatActivity() {

    companion object {
        private const val SHARED_URL = "https://github.com/yyued/SVGA-Samples/blob/master/posche.svga?raw=true"
    }

    private var _binding: ActivityDeduplicatedNetworkLoadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var resourceManager: SVGAResourceManager

    private val sharedUrl = URL(SHARED_URL)
    private val activeLoadHandles = mutableListOf<SVGAResourceManager.LoadHandle>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeduplicatedNetworkLoadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.example_deduplicated_network_loads)

        resourceManager = SVGAResourceManager.create(this)
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

            val loadHandle = resourceManager.loadFromURL(
                sharedUrl,
                object : SVGAResourceManager.LoadCompletion {
                    override fun onComplete(session: SVGAVideoSession) {
                        view.setVideoSession(session)
                        view.startAnimation()
                    }

                    override fun onError() {
                        // No-op: animation simply doesn't play if loading fails
                    }
                }
            )

            if (loadHandle != null) {
                activeLoadHandles += loadHandle
            }
        }
    }

    private fun cancelActiveLoads() {
        activeLoadHandles.forEach { handle ->
            handle.cancel()
        }
        activeLoadHandles.clear()
    }
}
