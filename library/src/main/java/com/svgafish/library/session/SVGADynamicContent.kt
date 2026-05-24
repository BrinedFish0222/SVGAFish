package com.svgafish.library.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.text.BoringLayout
import android.text.StaticLayout
import android.text.TextPaint
import com.svgafish.library.callback.IClickAreaListener
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SVGADynamicContent {

    companion object {
        private val threadNum = AtomicInteger(0)

        internal var threadPoolExecutor = Executors.newCachedThreadPool { r ->
            Thread(r, "SVGA-SVGADynamicContent-${threadNum.getAndIncrement()}")
        }
    }

    internal var dynamicHidden: HashMap<String, Boolean> = hashMapOf()

    internal var dynamicImage: HashMap<String, Bitmap> = hashMapOf()

    internal var dynamicText: HashMap<String, String> = hashMapOf()

    internal var dynamicTextPaint: HashMap<String, TextPaint> = hashMapOf()

    internal var dynamicStaticLayoutText: HashMap<String, StaticLayout> = hashMapOf()

    internal var dynamicBoringLayoutText: HashMap<String, BoringLayout> = hashMapOf()

    internal var dynamicDrawer: HashMap<String, (canvas: Canvas, frameIndex: Int) -> Boolean> = hashMapOf()

    //点击事件回调map
    internal var mClickMap : HashMap<String, IntArray> = hashMapOf()
    internal var dynamicIClickArea: HashMap<String, IClickAreaListener> = hashMapOf()

    internal var dynamicDrawerSized: HashMap<String, (canvas: Canvas, frameIndex: Int, width: Int, height: Int) -> Boolean> = hashMapOf()


    internal var isTextDirty = false

    fun setHidden(value: Boolean, forKey: String) {
        this.dynamicHidden[forKey] = value
    }

    fun setDynamicImage(bitmap: Bitmap, forKey: String) {
        this.dynamicImage[forKey] = bitmap
    }

    fun setDynamicImage(url: String, forKey: String) {
        val handler = Handler()
        threadPoolExecutor.execute {
            (URL(url).openConnection() as? HttpURLConnection)?.let {
                try {
                    it.connectTimeout = 20 * 1000
                    it.requestMethod = "GET"
                    it.connect()
                    it.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap ->
                            handler.post { setDynamicImage(bitmap, forKey) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        it.disconnect()
                    } catch (disconnectException: Throwable) {
                        // ignored here
                    }
                }
            }
        }
    }

    fun setDynamicText(text: String, textPaint: TextPaint, forKey: String) {
        this.isTextDirty = true
        this.dynamicText[forKey] = text
        this.dynamicTextPaint[forKey] = textPaint
    }

    fun setDynamicText(layoutText: StaticLayout, forKey: String) {
        this.isTextDirty = true
        this.dynamicStaticLayoutText[forKey] = layoutText
    }

    fun setDynamicText(layoutText: BoringLayout, forKey: String) {
        this.isTextDirty = true
        BoringLayout.isBoring(layoutText.text,layoutText.paint)?.let {
            this.dynamicBoringLayoutText.put(forKey,layoutText)
        }
    }

    fun setDynamicDrawer(drawer: (canvas: Canvas, frameIndex: Int) -> Boolean, forKey: String) {
        this.dynamicDrawer[forKey] = drawer
    }

    fun setClickArea(clickKey: List<String>) {
        for(itemKey in clickKey){
            dynamicIClickArea[itemKey] = object : IClickAreaListener {
                override fun onResponseArea(key: String, x0: Int, y0: Int, x1: Int, y1: Int) {
                    mClickMap.let {
                        if(it[key] == null){
                            it.put(key, intArrayOf(x0,y0,x1,y1))
                        }else{
                            it[key]?.let { array ->
                                array[0] = x0
                                array[1] = y0
                                array[2] = x1
                                array[3] = y1
                            }
                        }
                    }
                }
            }
        }
    }

    fun setClickArea(clickKey: String) {
        dynamicIClickArea[clickKey] = object : IClickAreaListener {
            override fun onResponseArea(key: String, x0: Int, y0: Int, x1: Int, y1: Int) {
                mClickMap.let {
                    if (it[key] == null) {
                        it.put(key, intArrayOf(x0, y0, x1, y1))
                    } else {
                        it[key]?.let { array ->
                            array[0] = x0
                            array[1] = y0
                            array[2] = x1
                            array[3] = y1
                        }
                    }
                }
            }
        }
    }

    fun setDynamicDrawerSized(drawer: (canvas: Canvas, frameIndex: Int, width: Int, height: Int) -> Boolean, forKey: String) {
        this.dynamicDrawerSized[forKey] = drawer
    }

    fun clearDynamicObjects() {
        this.isTextDirty = true
        this.dynamicHidden.clear()
        this.dynamicImage.clear()
        this.dynamicText.clear()
        this.dynamicTextPaint.clear()
        this.dynamicStaticLayoutText.clear()
        this.dynamicBoringLayoutText.clear()
        this.dynamicDrawer.clear()
        this.dynamicIClickArea.clear()
        this.mClickMap.clear()
        this.dynamicDrawerSized.clear()
    }
}
