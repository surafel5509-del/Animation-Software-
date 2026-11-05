package com.animationstudio.audio

import android.media.MediaPlayer
import com.animationstudio.core.model.AssetMetadata
import com.animationstudio.core.model.AudioClip
import com.animationstudio.core.model.Scene
import java.io.File

/** Synchronizes independent clip players to the animation playhead and applies trims/fades. */
class AudioPlaybackController {
    private val players = mutableMapOf<String, MediaPlayer>()
    private var clips: List<AudioClip> = emptyList()
    private var fps: Int = 24
    private var playing = false

    @Synchronized
    fun update(scene: Scene, assets: List<AssetMetadata>, assetFile: (AssetMetadata) -> File, frame: Int, fps: Int, play: Boolean) {
        this.fps = fps
        this.playing = play
        clips = scene.audio
        val activeIds = clips.map(AudioClip::id).toSet()
        players.keys.filterNot { it in activeIds }.forEach { id -> players.remove(id)?.release() }
        clips.forEach { clip ->
            val asset = assets.firstOrNull { it.id == clip.assetId } ?: return@forEach
            val player = players.getOrPut(clip.id) {
                MediaPlayer().apply {
                    setDataSource(assetFile(asset).absolutePath)
                    prepare()
                    isLooping = false
                }
            }
            val timelineMs = frame * 1000L / fps
            val clipStartMs = clip.startFrame * 1000L / fps
            val relativeMs = timelineMs - clipStartMs
            val sourceMs = clip.trimStartMs + relativeMs
            val endMs = clip.trimEndMs ?: asset.durationMs ?: Long.MAX_VALUE
            val audible = !clip.muted && relativeMs >= 0 && sourceMs < endMs
            if (audible) {
                if (kotlin.math.abs(player.currentPosition - sourceMs) > 80) player.seekTo(sourceMs.toInt())
                player.setVolume(gainAt(clip, relativeMs, endMs - clip.trimStartMs), gainAt(clip, relativeMs, endMs - clip.trimStartMs))
                if (play && !player.isPlaying) player.start() else if (!play && player.isPlaying) player.pause()
            } else if (player.isPlaying) player.pause()
        }
    }

    fun gainAt(clip: AudioClip, relativeMs: Long, durationMs: Long): Float {
        if (clip.muted) return 0f
        var gain = clip.volume
        if (clip.fadeInMs > 0) gain *= (relativeMs.toFloat() / clip.fadeInMs).coerceIn(0f, 1f)
        if (clip.fadeOutMs > 0) gain *= ((durationMs - relativeMs).toFloat() / clip.fadeOutMs).coerceIn(0f, 1f)
        return gain.coerceIn(0f, 1f)
    }

    @Synchronized fun release() {
        players.values.forEach(MediaPlayer::release)
        players.clear()
        playing = false
    }
}
