package com.animationstudio.timeline

import com.animationstudio.core.model.*
import java.util.UUID

object TimelineEditor {
    fun upsertKeyframe(
        scene: Scene,
        targetId: String,
        property: AnimatableProperty,
        keyframe: Keyframe,
        trackId: String = UUID.randomUUID().toString(),
    ): Scene {
        val existing = scene.tracks.firstOrNull { it.targetId == targetId && it.property == property }
        val track = existing ?: AnimationTrack(trackId, targetId, property)
        val keys = track.keyframes.filterNot { it.frame == keyframe.frame } + keyframe
        val updated = track.copy(keyframes = keys.sortedBy(Keyframe::frame))
        return scene.copy(tracks = if (existing == null) scene.tracks + updated else scene.tracks.map {
            if (it.id == existing.id) updated else it
        })
    }

    fun removeKeyframe(scene: Scene, targetId: String, property: AnimatableProperty, frame: Int): Scene = scene.copy(
        tracks = scene.tracks.map { track ->
            if (track.targetId == targetId && track.property == property)
                track.copy(keyframes = track.keyframes.filterNot { it.frame == frame }) else track
        }.filterNot { it.targetId == targetId && it.keyframes.isEmpty() },
    )

    fun moveKeyframe(scene: Scene, trackId: String, from: Int, to: Int): Scene {
        require(to >= 0)
        return scene.copy(tracks = scene.tracks.map { track ->
            if (track.id != trackId) track else track.copy(
                keyframes = (track.keyframes.filterNot { it.frame == from || it.frame == to } +
                    track.keyframes.firstOrNull { it.frame == from }?.copy(frame = to)).filterNotNull().sortedBy(Keyframe::frame),
            )
        })
    }

    fun insertFrames(scene: Scene, at: Int, count: Int = 1): Scene {
        require(at >= 0 && count > 0)
        return scene.copy(
            layers = scene.layers.map { layer -> layer.copy(cels = layer.cels.map { cel ->
                if (cel.frame >= at) cel.copy(frame = cel.frame + count) else if (cel.exposedAt(at)) cel.copy(exposure = cel.exposure + count) else cel
            }) },
            tracks = scene.tracks.map { track -> track.copy(keyframes = track.keyframes.map {
                if (it.frame >= at) it.copy(frame = it.frame + count) else it
            }) },
            audio = scene.audio.map { if (it.startFrame >= at) it.copy(startFrame = it.startFrame + count) else it },
            workArea = scene.workArea.copy(end = scene.workArea.end + count),
        )
    }

    fun deleteFrames(scene: Scene, at: Int, count: Int = 1): Scene {
        require(at >= 0 && count > 0 && at + count < scene.workArea.end) { "Cannot delete the entire work area" }
        val end = at + count
        return scene.copy(
            layers = scene.layers.map { layer -> layer.copy(cels = layer.cels.mapNotNull { cel ->
                when {
                    cel.frame >= end -> cel.copy(frame = cel.frame - count)
                    cel.frame >= at -> null
                    cel.frame + cel.exposure > at -> cel.copy(exposure = (cel.exposure - minOf(count, cel.frame + cel.exposure - at)).coerceAtLeast(1))
                    else -> cel
                }
            }) },
            tracks = scene.tracks.map { track -> track.copy(keyframes = track.keyframes.mapNotNull {
                when {
                    it.frame >= end -> it.copy(frame = it.frame - count)
                    it.frame >= at -> null
                    else -> it
                }
            }) },
            audio = scene.audio.map { if (it.startFrame >= end) it.copy(startFrame = it.startFrame - count) else it },
            workArea = scene.workArea.copy(end = scene.workArea.end - count),
        )
    }

    fun duplicateCel(layer: Layer, frame: Int, newCelId: (VectorObject) -> String = { UUID.randomUUID().toString() }): Layer {
        val source = layer.celAt(frame) ?: return layer
        val targetFrame = source.frame + source.exposure
        val copy = source.copy(frame = targetFrame, objects = source.objects.map { obj ->
            val id = newCelId(obj)
            when (obj) {
                is VectorObject.PathObject -> obj.copy(id = id)
                is VectorObject.ShapeObject -> obj.copy(id = id)
                is VectorObject.TextObject -> obj.copy(id = id)
                is VectorObject.ImageObject -> obj.copy(id = id)
            }
        })
        return layer.copy(cels = (layer.cels + copy).sortedBy(DrawingCel::frame))
    }

    fun setExposure(layer: Layer, frame: Int, exposure: Int): Layer {
        require(exposure > 0)
        val cel = layer.cels.firstOrNull { it.frame == frame } ?: return layer
        val next = layer.cels.filter { it.frame > frame }.minOfOrNull { it.frame } ?: Int.MAX_VALUE
        val maximum = if (next == Int.MAX_VALUE) exposure else (next - frame).coerceAtLeast(1)
        return layer.copy(cels = layer.cels.map { if (it.frame == frame) it.copy(exposure = exposure.coerceAtMost(maximum)) else it })
    }
}

object FrameTiming {
    fun advance(frame: Int, workArea: WorkArea, durationFrames: Int): Int {
        val end = minOf(workArea.end, durationFrames)
        val next = frame + 1
        return if (next >= end) {
            if (workArea.loop) workArea.start.coerceIn(0, durationFrames - 1) else frame.coerceAtMost(end - 1)
        } else next.coerceAtLeast(workArea.start)
    }

    fun timeSeconds(frame: Int, fps: Int): Double {
        require(fps > 0)
        return frame.toDouble() / fps
    }
}
