package com.svga.fish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.opensource.svgaplayer.SVGACache
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAParser.PlayCallback
import com.opensource.svgaplayer.SVGAVideoEntity
import com.svga.fish.databinding.ActivityOldApiGridSvgaDemoBinding
import com.svga.fish.databinding.ItemOldApiGridSvgaPlayerBinding
import java.io.File
import java.net.URL

class OldApiGridSvgaDemoActivity : AppCompatActivity() {

    companion object {
        private val svgaUrls = listOf(
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/Castle.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/MerryChristmas.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/angel.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/heartbeat.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/jojo_audio.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/matteBitmap.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/matteRect.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/mp3_to_long.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/rose.svga"),
            URL("https://github.com/svga/SVGAPlayer-Android/raw/master/app/src/main/assets/rose_2.0.0.svga")
        )

        private const val COLUMN_COUNT = 4
        private const val REPEAT_COUNT = 1

        private val items = List(REPEAT_COUNT) { svgaUrls }.flatten().flatMap { url -> List(COLUMN_COUNT) { url } }
    }

    private var _binding: ActivityOldApiGridSvgaDemoBinding? = null
    private val binding get() = _binding!!

    private val parser by lazy {
        SVGACache.onCreate(this, SVGACache.Type.FILE)
        SVGAParser(this)
    }

    private var adapter: OldApiGridSvgaAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOldApiGridSvgaDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.example_old_api_grid_svga)

        adapter = OldApiGridSvgaAdapter(parser)
        binding.gridRecyclerView.layoutManager = GridLayoutManager(this, COLUMN_COUNT)
        binding.gridRecyclerView.adapter = adapter
    }

    override fun onDestroy() {
        adapter?.cancelAllLoads()
        adapter = null
        _binding = null
        super.onDestroy()
    }

    private class OldApiGridSvgaAdapter(
        private val parser: SVGAParser
    ) : RecyclerView.Adapter<OldApiGridSvgaViewHolder>() {

        private val activeCancelFns = mutableListOf<() -> Unit>()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(
            parent: ViewGroup, viewType: Int
        ): OldApiGridSvgaViewHolder {
            val binding = ItemOldApiGridSvgaPlayerBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return OldApiGridSvgaViewHolder(binding)
        }

        override fun onViewAttachedToWindow(holder: OldApiGridSvgaViewHolder) {
            super.onViewAttachedToWindow(holder)
            holder.svgaVideo?.let {
                holder.binding.playerView.setVideoItem(it)
                holder.binding.playerView.startAnimation()
            }

        }

        override fun onViewDetachedFromWindow(holder: OldApiGridSvgaViewHolder) {
            super.onViewDetachedFromWindow(holder)
            holder.binding.playerView.stopAnimation()
        }

        override fun onBindViewHolder(holder: OldApiGridSvgaViewHolder, position: Int) {
            val url = items[position]
            val displayName = url.path.substringAfterLast('/').removeSuffix(".svga")
            "${displayName}_${position}".let {
                holder.binding.filenameView.text = it
            }

            holder.cancelLoad?.let { fn ->
                fn()
                activeCancelFns.remove(fn)
            }
            holder.currentUrl = url

            holder.binding.playerView.apply {
                visibility = View.INVISIBLE
                clear()
            }

            val cancelFn = parser.decodeFromURL(
                url,
                object : SVGAParser.ParseCompletion {
                    override fun onComplete(videoItem: SVGAVideoEntity) {
                        val currentPos = holder.bindingAdapterPosition
                        if (currentPos == RecyclerView.NO_POSITION || items.getOrNull(currentPos) != url) return

                        holder.svgaVideo = videoItem
                        holder.binding.playerView.apply {
                            setVideoItem(videoItem)
                            visibility = View.VISIBLE
                            startAnimation()
                        }
                    }

                    override fun onError() {}
                },
                playCallback = object : PlayCallback {
                    override fun onPlay(file: List<File>) {

                    }
                },
            )

            holder.cancelLoad = cancelFn
            if (cancelFn != null) {
                activeCancelFns += cancelFn
            }
        }

        override fun onViewRecycled(holder: OldApiGridSvgaViewHolder) {
            holder.cancelLoad?.let { fn ->
                fn()
                activeCancelFns.remove(fn)
            }
            holder.cancelLoad = null
            holder.currentUrl = null
            holder.svgaVideo = null
            holder.binding.playerView.clear()
            holder.binding.playerView.visibility = View.INVISIBLE
            super.onViewRecycled(holder)
        }

        fun cancelAllLoads() {
            activeCancelFns.forEach { it.invoke() }
            activeCancelFns.clear()
        }
    }

    private class OldApiGridSvgaViewHolder(
        val binding: ItemOldApiGridSvgaPlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        var cancelLoad: (() -> Unit)? = null
        var currentUrl: URL? = null
        var svgaVideo: SVGAVideoEntity? = null
    }
}
