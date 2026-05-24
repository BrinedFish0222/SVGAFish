package com.svgafish.library.session

import android.media.AudioAttributes
import android.media.SoundPool
import com.svgafish.library.model.SVGAAudioTrack
import com.svgafish.library.model.SVGAVideoModel
import java.io.File
import java.io.FileInputStream

/**
 * 单次播放期的运行时上下文，不参与共享缓存。
 */
class SVGAVideoSession internal constructor(
    val model: SVGAVideoModel,
    private val onClose: (() -> Unit)? = null,
    private val onPlaybackFinished: (() -> Unit)? = null
) {

    internal fun interface PlayCallback {
        fun onPlay(file: List<File>)
    }

    private val lifecycleLock = Any()
    private var closed = false

    var antiAlias: Boolean = true

    private var audioVolume: Float = 1.0f

    internal var audioList: List<SVGAAudioPlayback> = emptyList()
    internal var soundPool: SoundPool? = null
        set(value) {
            field?.let { pendingPools.remove(it) }
            field = value
            if (value != null) {
                pendingPools.add(value)
            }
        }

    companion object {
        private val pendingPools = ArrayList<SoundPool>()
    }

    internal fun prepare(callback: () -> Unit, playCallback: PlayCallback? = null) {
        val audioTracks = model.audioTracks
        if (audioTracks.isEmpty()) {
            callback()
            return
        }

        audioList = audioTracks.map(::SVGAAudioPlayback)

        val playableTracks = audioTracks.filter { track ->
            track.file?.exists() == true && track.totalTime > 0
        }
        if (playableTracks.isEmpty()) {
            callback()
            return
        }

        playCallback?.let {
            it.onPlay(playableTracks.mapNotNull(SVGAAudioTrack::file))
            callback()
            return
        }

        setupSoundPool(playableTracks.size, callback)
        audioList = audioList.map { state ->
            val track = playableTracks.firstOrNull { it.audioKey == state.track.audioKey } ?: return@map state
            loadAudio(state, track)
        }
    }

    private fun loadAudio(state: SVGAAudioPlayback, track: SVGAAudioTrack): SVGAAudioPlayback {
        val file = track.file ?: return state
        FileInputStream(file).use {
            val length = it.available().toDouble()
            val offset = ((track.startTime.toDouble() / track.totalTime.toDouble()) * length).toLong()
            state.soundId = soundPool?.load(it.fd, offset, length.toLong(), 1)
        }
        return state
    }

    private fun setupSoundPool(audioCount: Int, completionBlock: () -> Unit) {
        var soundLoaded = 0
        soundPool = generateSoundPool(audioCount)
        if (soundPool == null) {
            completionBlock()
            return
        }

        soundPool!!.setOnLoadCompleteListener { _, _, _ ->
            soundLoaded++
            if (soundLoaded >= audioCount) {
                completionBlock()
            }
        }
    }

    private fun generateSoundPool(audioCount: Int): SoundPool? {
        return try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(12.coerceAtMost(audioCount))
                .build()
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (!shouldClose) {
            return
        }
        stopAudio()
        soundPool?.release()
        soundPool = null
        audioList = emptyList()
        onClose?.invoke()
    }

    internal fun notifyPlaybackFinished() {
        val isClosed = synchronized(lifecycleLock) { closed }
        if (!isClosed) {
            onPlaybackFinished?.invoke()
        }
    }

    internal fun playAudio(frameIndex: Int) {
        audioList.forEach { audio ->
            if (audio.track.startFrame == frameIndex) {
                audio.soundId?.let { soundId ->
                    audio.playId = soundPool?.play(soundId, audioVolume, audioVolume, 1, 0, 1.0f)
                }
            }
            if (audio.track.endFrame in 1..frameIndex) {
                audio.playId?.let { playId ->
                    soundPool?.stop(playId)
                }
                audio.playId = null
            }
        }
    }

    internal fun resumeAudio() {
        audioList.forEach { audio ->
            audio.playId?.let { playId ->
                soundPool?.resume(playId)
            }
        }
    }

    internal fun pauseAudio() {
        audioList.forEach { audio ->
            audio.playId?.let { playId ->
                soundPool?.pause(playId)
            }
        }
    }

    internal fun stopAudio() {
        audioList.forEach { audio ->
            audio.playId?.let { playId ->
                soundPool?.stop(playId)
            }
            audio.playId = null
        }
    }

    internal fun setAudioVolume(volume: Float) {
        audioVolume = volume
        audioList.forEach { audio ->
            audio.playId?.let { playId ->
                soundPool?.setVolume(playId, volume, volume)
            }
        }
    }
}

internal data class SVGAAudioPlayback(
    val track: SVGAAudioTrack,
    var soundId: Int? = null,
    var playId: Int? = null
)
