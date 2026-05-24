package com.svgafish.library.internal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import com.svgafish.library.bitmap.SVGABitmapByteArrayDecoder
import com.svgafish.library.bitmap.SVGABitmapFileDecoder
import com.svgafish.library.proto.FrameEntity
import com.svgafish.library.proto.MovieEntity
import com.svgafish.library.proto.MovieParams
import com.svgafish.library.proto.ShapeEntity
import com.svgafish.library.proto.SpriteEntity
import com.svgafish.library.utils.SVGARect
import com.svgafish.library.manager.SVGACachePathResolver
import com.svgafish.library.model.SVGAAudioTrack
import com.svgafish.library.model.SVGAVideoModel
import com.svgafish.library.model.SVGAVideoPath
import com.svgafish.library.model.SVGAVideoShape
import com.svgafish.library.model.SVGAVideoShapeStyles
import com.svgafish.library.model.SVGAVideoShapeType
import com.svgafish.library.model.SVGAVideoSprite
import com.svgafish.library.model.SVGAVideoSpriteFrame
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap

internal class SVGAVideoModelFactory(
    private val cachePathResolver: SVGACachePathResolver
) {

    fun fromJson(
        json: JSONObject,
        cacheKey: String,
        frameWidth: Int = 0,
        frameHeight: Int = 0
    ): SVGAVideoModel {
        val movieJsonObject = json.optJSONObject("movie")
        val sizeAndFrames = movieJsonObject?.let(::parseMovieJson) ?: VideoSpec.EMPTY
        val images = runCatching { parseImages(json, cacheKey, frameWidth, frameHeight) }
            .getOrDefault(emptyMap())
        val sprites = parseSprites(json)
        return SVGAVideoModel(
            requestKey = cacheKey,
            videoSize = sizeAndFrames.videoSize,
            fps = sizeAndFrames.fps,
            frames = sizeAndFrames.frames,
            spriteList = sprites,
            imageMap = images,
            audioTracks = emptyList()
        )
    }

    fun fromMovieEntity(
        entity: MovieEntity,
        cacheKey: String,
        frameWidth: Int = 0,
        frameHeight: Int = 0
    ): SVGAVideoModel {
        val sizeAndFrames = entity.params?.let(::parseMovieParams) ?: VideoSpec.EMPTY
        val images = runCatching { parseImages(entity, cacheKey, frameWidth, frameHeight) }
            .getOrDefault(emptyMap())
        val sprites = entity.sprites?.map(::parseSprite).orEmpty()
        val audioTracks = parseAudioTracks(entity)
        return SVGAVideoModel(
            requestKey = cacheKey,
            videoSize = sizeAndFrames.videoSize,
            fps = sizeAndFrames.fps,
            frames = sizeAndFrames.frames,
            spriteList = sprites,
            imageMap = images,
            audioTracks = audioTracks
        )
    }

    private fun parseMovieJson(movieObject: JSONObject): VideoSpec {
        var videoSize = SVGARect(0.0, 0.0, 0.0, 0.0)
        movieObject.optJSONObject("viewBox")?.let { viewBoxObject ->
            val width = viewBoxObject.optDouble("width", 0.0)
            val height = viewBoxObject.optDouble("height", 0.0)
            videoSize = SVGARect(0.0, 0.0, width, height)
        }
        return VideoSpec(
            videoSize = videoSize,
            fps = movieObject.optInt("fps", 20),
            frames = movieObject.optInt("frames", 0)
        )
    }

    private fun parseMovieParams(movieParams: MovieParams): VideoSpec {
        val width = (movieParams.viewBoxWidth ?: 0.0f).toDouble()
        val height = (movieParams.viewBoxHeight ?: 0.0f).toDouble()
        return VideoSpec(
            videoSize = SVGARect(0.0, 0.0, width, height),
            fps = movieParams.fps ?: 20,
            frames = movieParams.frames ?: 0
        )
    }

    private fun parseImages(
        json: JSONObject,
        cacheKey: String,
        frameWidth: Int,
        frameHeight: Int
    ): Map<String, Bitmap> {
        val imageMap = LinkedHashMap<String, Bitmap>()
        val imgJson = json.optJSONObject("images") ?: return emptyMap()
        imgJson.keys().forEach { imgKey ->
            val filePath = generateBitmapFilePath(cacheKey, imgJson[imgKey].toString(), imgKey)
            if (filePath.isEmpty()) {
                return@forEach
            }
            val bitmapKey = imgKey.replace(".matte", "")
            val bitmap = SVGABitmapFileDecoder.decodeBitmapFrom(filePath, frameWidth, frameHeight)
            if (bitmap != null) {
                imageMap[bitmapKey] = bitmap
            }
        }
        return imageMap
    }

    private fun parseImages(
        entity: MovieEntity,
        cacheKey: String,
        frameWidth: Int,
        frameHeight: Int
    ): Map<String, Bitmap> {
        val imageMap = LinkedHashMap<String, Bitmap>()
        entity.images?.entries?.forEach { entry ->
            val byteArray = entry.value.toByteArray()
            if (byteArray.count() < 4) {
                return@forEach
            }
            val fileTag = byteArray.slice(IntRange(0, 3))
            if (fileTag[0].toInt() == 73 && fileTag[1].toInt() == 68 && fileTag[2].toInt() == 51) {
                return@forEach
            }
            val filePath = generateBitmapFilePath(cacheKey, entry.value.utf8(), entry.key)
            val bitmap = SVGABitmapByteArrayDecoder.decodeBitmapFrom(byteArray, frameWidth, frameHeight)
                ?: SVGABitmapFileDecoder.decodeBitmapFrom(filePath, frameWidth, frameHeight)
            if (bitmap != null) {
                imageMap[entry.key] = bitmap
            }
        }
        return imageMap
    }

    private fun parseSprites(json: JSONObject): List<SVGAVideoSprite> {
        val sprites = mutableListOf<SVGAVideoSprite>()
        json.optJSONArray("sprites")?.let { items ->
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { sprite ->
                    sprites.add(parseSprite(sprite))
                }
            }
        }
        return sprites
    }

    private fun parseSprite(obj: JSONObject): SVGAVideoSprite {
        val imageKey = obj.optString("imageKey")
        val matteKey = obj.optString("matteKey")
        val mutableFrames = mutableListOf<SVGAVideoSpriteFrame>()
        obj.optJSONArray("frames")?.let { frames ->
            for (i in 0 until frames.length()) {
                frames.optJSONObject(i)?.let { frameJson ->
                    var frameItem = parseFrame(frameJson)
                    if (frameItem.shapes.firstOrNull()?.isKeep == true && mutableFrames.isNotEmpty()) {
                        frameItem = frameItem.copy(shapes = mutableFrames.last().shapes)
                    }
                    mutableFrames.add(frameItem)
                }
            }
        }
        return SVGAVideoSprite(
            imageKey = imageKey,
            matteKey = matteKey,
            frames = mutableFrames.toList()
        )
    }

    private fun parseSprite(obj: SpriteEntity): SVGAVideoSprite {
        var lastFrame: SVGAVideoSpriteFrame? = null
        val frames = obj.frames?.map { frame ->
            var frameItem = parseFrame(frame)
            if (frameItem.shapes.firstOrNull()?.isKeep == true && lastFrame != null) {
                frameItem = frameItem.copy(shapes = lastFrame.shapes)
            }
            lastFrame = frameItem
            frameItem
        }.orEmpty()
        return SVGAVideoSprite(
            imageKey = obj.imageKey,
            matteKey = obj.matteKey,
            frames = frames
        )
    }

    private fun parseFrame(obj: JSONObject): SVGAVideoSpriteFrame {
        var layout = SVGARect(0.0, 0.0, 0.0, 0.0)
        val transform = Matrix()
        var maskPath: SVGAVideoPath? = null
        var shapes: List<SVGAVideoShape> = emptyList()

        obj.optJSONObject("layout")?.let {
            layout = SVGARect(
                it.optDouble("x", 0.0),
                it.optDouble("y", 0.0),
                it.optDouble("width", 0.0),
                it.optDouble("height", 0.0)
            )
        }
        obj.optJSONObject("transform")?.let { applyTransform(transform, it) }
        obj.optString("clipPath").takeIf { it.isNotEmpty() }?.let { maskPath = SVGAVideoPath(it) }
        obj.optJSONArray("shapes")?.let { shapesJson ->
            val mutableShapes = mutableListOf<SVGAVideoShape>()
            for (i in 0 until shapesJson.length()) {
                shapesJson.optJSONObject(i)?.let { shapeJson ->
                    mutableShapes.add(parseShape(shapeJson))
                }
            }
            shapes = mutableShapes.toList()
        }
        return SVGAVideoSpriteFrame(
            alpha = obj.optDouble("alpha", 0.0),
            layout = layout,
            transform = transform,
            maskPath = maskPath,
            shapes = shapes
        )
    }

    private fun parseFrame(obj: FrameEntity): SVGAVideoSpriteFrame {
        var layout = SVGARect(0.0, 0.0, 0.0, 0.0)
        val transform = Matrix()
        obj.layout?.let {
            layout = SVGARect(
                (it.x ?: 0.0f).toDouble(),
                (it.y ?: 0.0f).toDouble(),
                (it.width ?: 0.0f).toDouble(),
                (it.height ?: 0.0f).toDouble()
            )
        }
        obj.transform?.let {
            applyTransformValues(
                transform = transform,
                a = it.a ?: 1.0f,
                b = it.b ?: 0.0f,
                c = it.c ?: 0.0f,
                d = it.d ?: 1.0f,
                tx = it.tx ?: 0.0f,
                ty = it.ty ?: 0.0f
            )
        }
        return SVGAVideoSpriteFrame(
            alpha = (obj.alpha ?: 0.0f).toDouble(),
            layout = layout,
            transform = transform,
            maskPath = obj.clipPath?.takeIf { it.isNotEmpty() }?.let(::SVGAVideoPath),
            shapes = obj.shapes.map(::parseShape)
        )
    }

    private fun parseShape(obj: JSONObject): SVGAVideoShape {
        val args = LinkedHashMap<String, Any>()
        obj.optJSONObject("args")?.let { values ->
            values.keys().forEach { key ->
                values.get(key).let { args[key] = it }
            }
        }
        return SVGAVideoShape(
            type = parseShapeType(obj.optString("type")),
            args = args,
            styles = obj.optJSONObject("styles")?.let(::parseStyles),
            transform = obj.optJSONObject("transform")?.let(::parseMatrix)
        )
    }

    private fun parseShape(obj: ShapeEntity): SVGAVideoShape {
        val args = LinkedHashMap<String, Any>()
        obj.shape?.d?.let { args["d"] = it }
        obj.ellipse?.let {
            args["x"] = it.x ?: 0.0f
            args["y"] = it.y ?: 0.0f
            args["radiusX"] = it.radiusX ?: 0.0f
            args["radiusY"] = it.radiusY ?: 0.0f
        }
        obj.rect?.let {
            args["x"] = it.x ?: 0.0f
            args["y"] = it.y ?: 0.0f
            args["width"] = it.width ?: 0.0f
            args["height"] = it.height ?: 0.0f
            args["cornerRadius"] = it.cornerRadius ?: 0.0f
        }
        return SVGAVideoShape(
            type = parseShapeType(obj.type),
            args = args,
            styles = obj.styles?.let(::parseStyles),
            transform = obj.transform?.let {
                Matrix().also { matrix ->
                    applyTransformValues(
                        transform = matrix,
                        a = it.a ?: 1.0f,
                        b = it.b ?: 0.0f,
                        c = it.c ?: 0.0f,
                        d = it.d ?: 1.0f,
                        tx = it.tx ?: 0.0f,
                        ty = it.ty ?: 0.0f
                    )
                }
            }
        )
    }

    private fun parseShapeType(type: String): SVGAVideoShapeType {
        return when {
            type.equals("shape", ignoreCase = true) -> SVGAVideoShapeType.SHAPE
            type.equals("rect", ignoreCase = true) -> SVGAVideoShapeType.RECT
            type.equals("ellipse", ignoreCase = true) -> SVGAVideoShapeType.ELLIPSE
            type.equals("keep", ignoreCase = true) -> SVGAVideoShapeType.KEEP
            else -> SVGAVideoShapeType.SHAPE
        }
    }

    private fun parseShapeType(type: ShapeEntity.ShapeType?): SVGAVideoShapeType {
        return when (type) {
            ShapeEntity.ShapeType.SHAPE, null -> SVGAVideoShapeType.SHAPE
            ShapeEntity.ShapeType.RECT -> SVGAVideoShapeType.RECT
            ShapeEntity.ShapeType.ELLIPSE -> SVGAVideoShapeType.ELLIPSE
            ShapeEntity.ShapeType.KEEP -> SVGAVideoShapeType.KEEP
        }
    }

    private fun parseStyles(obj: JSONObject): SVGAVideoShapeStyles {
        var fill = 0x00000000
        var stroke = 0x00000000
        val strokeWidth = obj.optDouble("strokeWidth", 0.0).toFloat()
        val lineCap = obj.optString("lineCap", "butt")
        val lineJoin = obj.optString("lineJoin", "miter")
        val miterLimit = obj.optInt("miterLimit", 0)
        val lineDash = obj.optJSONArray("lineDash")?.let { jsonArray ->
            FloatArray(jsonArray.length()).also { dash ->
                for (i in 0 until jsonArray.length()) {
                    dash[i] = jsonArray.optDouble(i, 0.0).toFloat()
                }
            }
        } ?: FloatArray(0)
        obj.optJSONArray("fill")?.takeIf { it.length() == 4 }?.let {
            val mulValue = checkValueRange(it)
            val alphaRangeValue = checkAlphaValueRange(it)
            fill = Color.argb(
                (it.optDouble(3) * alphaRangeValue).toInt(),
                (it.optDouble(0) * mulValue).toInt(),
                (it.optDouble(1) * mulValue).toInt(),
                (it.optDouble(2) * mulValue).toInt()
            )
        }
        obj.optJSONArray("stroke")?.takeIf { it.length() == 4 }?.let {
            val mulValue = checkValueRange(it)
            val alphaRangeValue = checkAlphaValueRange(it)
            stroke = Color.argb(
                (it.optDouble(3) * alphaRangeValue).toInt(),
                (it.optDouble(0) * mulValue).toInt(),
                (it.optDouble(1) * mulValue).toInt(),
                (it.optDouble(2) * mulValue).toInt()
            )
        }
        return SVGAVideoShapeStyles(fill, stroke, strokeWidth, lineCap, lineJoin, miterLimit, lineDash)
    }

    private fun parseStyles(obj: ShapeEntity.ShapeStyle): SVGAVideoShapeStyles {
        var fill = 0x00000000
        var stroke = 0x00000000
        obj.fill?.let {
            val mulValue = checkValueRange(it)
            val alphaRangeValue = checkAlphaValueRange(it)
            fill = Color.argb(
                ((it.a ?: 0f) * alphaRangeValue).toInt(),
                ((it.r ?: 0f) * mulValue).toInt(),
                ((it.g ?: 0f) * mulValue).toInt(),
                ((it.b ?: 0f) * mulValue).toInt()
            )
        }
        obj.stroke?.let {
            val mulValue = checkValueRange(it)
            val alphaRangeValue = checkAlphaValueRange(it)
            stroke = Color.argb(
                ((it.a ?: 0f) * alphaRangeValue).toInt(),
                ((it.r ?: 0f) * mulValue).toInt(),
                ((it.g ?: 0f) * mulValue).toInt(),
                ((it.b ?: 0f) * mulValue).toInt()
            )
        }
        val lineCap = when (obj.lineCap) {
            ShapeEntity.ShapeStyle.LineCap.LineCap_ROUND -> "round"
            ShapeEntity.ShapeStyle.LineCap.LineCap_SQUARE -> "square"
            else -> "butt"
        }
        val lineJoin = when (obj.lineJoin) {
            ShapeEntity.ShapeStyle.LineJoin.LineJoin_BEVEL -> "bevel"
            ShapeEntity.ShapeStyle.LineJoin.LineJoin_ROUND -> "round"
            else -> "miter"
        }
        val lineDash = FloatArray(3)
        obj.lineDashI?.let { lineDash[0] = it }
        obj.lineDashII?.let { lineDash[1] = it }
        obj.lineDashIII?.let { lineDash[2] = it }
        return SVGAVideoShapeStyles(
            fill = fill,
            stroke = stroke,
            strokeWidth = obj.strokeWidth ?: 0.0f,
            lineCap = lineCap,
            lineJoin = lineJoin,
            miterLimit = (obj.miterLimit ?: 0.0f).toInt(),
            lineDash = lineDash
        )
    }

    private fun parseMatrix(obj: JSONObject): Matrix {
        return Matrix().also { applyTransform(it, obj) }
    }

    private fun applyTransform(transform: Matrix, obj: JSONObject) {
        applyTransformValues(
            transform = transform,
            a = obj.optDouble("a", 1.0).toFloat(),
            b = obj.optDouble("b", 0.0).toFloat(),
            c = obj.optDouble("c", 0.0).toFloat(),
            d = obj.optDouble("d", 1.0).toFloat(),
            tx = obj.optDouble("tx", 0.0).toFloat(),
            ty = obj.optDouble("ty", 0.0).toFloat()
        )
    }

    private fun applyTransformValues(
        transform: Matrix,
        a: Float,
        b: Float,
        c: Float,
        d: Float,
        tx: Float,
        ty: Float
    ) {
        val arr = FloatArray(9)
        arr[0] = a
        arr[1] = c
        arr[2] = tx
        arr[3] = b
        arr[4] = d
        arr[5] = ty
        arr[6] = 0.0f
        arr[7] = 0.0f
        arr[8] = 1.0f
        transform.setValues(arr)
    }

    private fun checkValueRange(obj: JSONArray): Float {
        return if (
            obj.optDouble(0) <= 1 &&
            obj.optDouble(1) <= 1 &&
            obj.optDouble(2) <= 1
        ) {
            255f
        } else {
            1f
        }
    }

    private fun checkAlphaValueRange(obj: JSONArray): Float {
        return if (obj.optDouble(3) <= 1) 255f else 1f
    }

    private fun checkValueRange(color: ShapeEntity.ShapeStyle.RGBAColor): Float {
        return if (
            (color.r ?: 0f) <= 1 &&
            (color.g ?: 0f) <= 1 &&
            (color.b ?: 0f) <= 1
        ) {
            255f
        } else {
            1f
        }
    }

    private fun checkAlphaValueRange(color: ShapeEntity.ShapeStyle.RGBAColor): Float {
        return if (color.a <= 1f) 255f else 1f
    }

    private fun parseAudioTracks(entity: MovieEntity): List<SVGAAudioTrack> {
        val audioFiles = generateAudioFileMap(entity)
        return entity.audios?.map { audio ->
            val audioKey = audio.audioKey.orEmpty()
            SVGAAudioTrack(
                audioKey = audioKey,
                startFrame = audio.startFrame ?: 0,
                endFrame = audio.endFrame ?: 0,
                startTime = audio.startTime ?: 0,
                totalTime = audio.totalTime ?: 0,
                file = audioFiles[audioKey]
            )
        }.orEmpty()
    }

    private fun generateAudioFileMap(entity: MovieEntity): Map<String, File> {
        val audiosDataMap = generateAudioMap(entity)
        if (audiosDataMap.isEmpty()) {
            return emptyMap()
        }
        return audiosDataMap.mapValues { entry ->
            val audioCache = cachePathResolver.buildAudioFile(entry.key)
            audioCache.takeIf { it.exists() } ?: generateAudioFile(audioCache, entry.value)
        }
    }

    private fun generateAudioMap(entity: MovieEntity): Map<String, ByteArray> {
        val audiosDataMap = LinkedHashMap<String, ByteArray>()
        entity.images?.entries?.forEach {
            val imageKey = it.key
            val byteArray = it.value.toByteArray()
            if (byteArray.count() < 4) {
                return@forEach
            }
            val fileTag = byteArray.slice(IntRange(0, 3))
            if (
                (fileTag[0].toInt() == 73 && fileTag[1].toInt() == 68 && fileTag[2].toInt() == 51) ||
                (fileTag[0].toInt() == -1 && fileTag[1].toInt() == -5 && fileTag[2].toInt() == -108)
            ) {
                audiosDataMap[imageKey] = byteArray
            }
        }
        return audiosDataMap
    }

    private fun generateAudioFile(audioCache: File, value: ByteArray): File {
        audioCache.createNewFile()
        FileOutputStream(audioCache).use { outputStream ->
            outputStream.write(value)
        }
        return audioCache
    }

    private fun generateBitmapFilePath(cacheKey: String, imgName: String, imgKey: String): String {
        val cacheDir = cachePathResolver.buildCacheDir(cacheKey)
        val path = cacheDir.absolutePath + "/" + imgName
        val path1 = "$path.png"
        val path2 = cacheDir.absolutePath + "/" + imgKey + ".png"

        return when {
            File(path).exists() -> path
            File(path1).exists() -> path1
            File(path2).exists() -> path2
            else -> ""
        }
    }

    private data class VideoSpec(
        val videoSize: SVGARect,
        val fps: Int,
        val frames: Int
    ) {
        companion object {
            val EMPTY = VideoSpec(
                videoSize = SVGARect(0.0, 0.0, 0.0, 0.0),
                fps = 20,
                frames = 0
            )
        }
    }
}
