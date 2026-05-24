package com.svga.fish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.svga.fish.databinding.ActivityGridSvgaDemoBinding
import com.svga.fish.databinding.ItemGridSvgaPlayerBinding
import com.svgafish.library.SVGAResourceManager
import com.svgafish.library.session.SVGAVideoSession
import java.net.URL

class GridSvgaDemoActivity : AppCompatActivity() {

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

    private var _binding: ActivityGridSvgaDemoBinding? = null
    private val binding get() = _binding!!

    private val resourceManager by lazy { SVGAResourceManager.create(this) }

    private var adapter: GridSvgaAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityGridSvgaDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.example_grid_svga)

        adapter = GridSvgaAdapter()
        binding.gridRecyclerView.layoutManager = GridLayoutManager(this, COLUMN_COUNT)
        binding.gridRecyclerView.adapter = adapter
    }

    override fun onDestroy() {
        adapter?.cancelAllLoads()
        adapter = null
        _binding = null
        super.onDestroy()
    }

    private inner class GridSvgaAdapter : RecyclerView.Adapter<GridSvgaViewHolder>() {

        private val activeHandles = mutableListOf<SVGAResourceManager.LoadHandle>()

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(
            parent: ViewGroup, viewType: Int
        ): GridSvgaViewHolder {
            val binding = ItemGridSvgaPlayerBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            binding.playerView.isMuted = true
            return GridSvgaViewHolder(binding)
        }

        override fun onViewAttachedToWindow(holder: GridSvgaViewHolder) {
            super.onViewAttachedToWindow(holder)
            holder.videoSession?.let {
                holder.binding.playerView.setVideoSession(it)
            }
            holder.binding.playerView.startAnimation()
        }

        override fun onViewDetachedFromWindow(holder: GridSvgaViewHolder) {
            super.onViewDetachedFromWindow(holder)
            holder.binding.playerView.stopAnimation()
        }

        override fun onBindViewHolder(holder: GridSvgaViewHolder, position: Int) {
            val url = items[position]
            val displayName = url.path.substringAfterLast('/').removeSuffix(".svga")
            "${displayName}_${position}".let {
                holder.binding.filenameView.text = it
            }

            holder.activeHandle?.cancel()
            holder.binding.playerView.apply {
                visibility = View.INVISIBLE
                clear()
            }

            val handle = resourceManager.loadFromURL(
                url, object : SVGAResourceManager.LoadCompletion {
                    override fun onComplete(session: SVGAVideoSession) {
                        val currentPos = holder.bindingAdapterPosition
                        if (currentPos == RecyclerView.NO_POSITION || items.getOrNull(currentPos) != url) return

                        holder.videoSession = session
                        holder.binding.playerView.let { view ->
                            view.visibility = View.VISIBLE
                            view.setVideoSession(session)
                            view.startAnimation()
                        }
                    }

                    override fun onError() {}
                })

            if (handle != null) {
                holder.activeHandle = handle
                activeHandles += handle
            }
        }

        override fun onViewRecycled(holder: GridSvgaViewHolder) {
            holder.activeHandle?.cancel()
            activeHandles.remove(holder.activeHandle)
            holder.activeHandle = null
            holder.binding.playerView.clear()
            holder.binding.playerView.visibility = View.INVISIBLE
            super.onViewRecycled(holder)
        }

        fun cancelAllLoads() {
            activeHandles.forEach { it.cancel() }
            activeHandles.clear()
        }
    }

    private class GridSvgaViewHolder(
        val binding: ItemGridSvgaPlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        var activeHandle: SVGAResourceManager.LoadHandle? = null
        var videoSession: SVGAVideoSession? = null
    }
}
