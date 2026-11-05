package com.animationstudio.animation

import com.animationstudio.core.model.AnimatableProperty
import com.animationstudio.core.model.AnimationTrack
import com.animationstudio.core.model.InterpolationType
import com.animationstudio.core.model.Keyframe
import com.animationstudio.core.model.Scene
import com.animationstudio.core.model.Transform
import com.animationstudio.core.model.Vec2
import kotlin.math.roundToInt

/** Immutable, deterministic keyframe sampling. All rotations are radians. */
object AnimationEvaluator {
    fun sample(track: AnimationTrack, frame: Int, fallback: Float = 0f): Float {
        val keys = track.keyframes.sortedBy(Keyframe::frame)
        if (keys.isEmpty()) return fallback
        if (frame <= keys.first().frame) return keys.first().value
        if (frame >= keys.last().frame) return keys.last().value
        val next = keys.first { it.frame > frame }
        val previous = keys.last { it.frame <= frame }
        val span = next.frame - previous.frame
        if (span <= 0) return next.value
        val progress = Interpolation.solve(previous, (frame - previous.frame).toFloat() / span)
        return previous.value + (next.value - previous.value) * progress
    }

    fun sampleColor(track: AnimationTrack, frame: Int, fallback: Int): Int {
        val keys = track.keyframes.sortedBy(Keyframe::frame)
        if (keys.isEmpty()) return fallback
        if (frame <= keys.first().frame) return keys.first().colorValue ?: fallback
        if (frame >= keys.last().frame) return keys.last().colorValue ?: fallback
        val next = keys.first { it.frame > frame }
        val previous = keys.last { it.frame <= frame }
        val start = previous.colorValue ?: fallback
        val end = next.colorValue ?: fallback
        val span = (next.frame - previous.frame).coerceAtLeast(1)
        val t = Interpolation.solve(previous, (frame - previous.frame).toFloat() / span)
        val channel = { shift: Int ->
            val a = (start ushr shift) and 0xff
            val b = (end ushr shift) and 0xff
            (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun transform(scene: Scene, targetId: String, base: Transform, frame: Int): Transform {
        fun value(property: AnimatableProperty, fallback: Float): Float = scene.tracks
            .firstOrNull { it.targetId == targetId && it.property == property }
            ?.let { sample(it, frame, fallback) } ?: fallback
        return base.copy(
            position = Vec2(
                value(AnimatableProperty.POSITION_X, base.position.x),
                value(AnimatableProperty.POSITION_Y, base.position.y),
            ),
            rotation = value(AnimatableProperty.ROTATION, base.rotation),
            scale = Vec2(
                value(AnimatableProperty.SCALE_X, base.scale.x),
                value(AnimatableProperty.SCALE_Y, base.scale.y),
            ),
            opacity = value(AnimatableProperty.OPACITY, base.opacity).coerceIn(0f, 1f),
        )
    }
}

object Interpolation {
    fun solve(key: Keyframe, linearProgress: Float): Float {
        val t = linearProgress.coerceIn(0f, 1f)
        return when (key.interpolation) {
            InterpolationType.HOLD -> 0f
            InterpolationType.LINEAR -> t
            InterpolationType.EASE_IN -> t * t * t
            InterpolationType.EASE_OUT -> 1f - (1f - t) * (1f - t) * (1f - t)
            InterpolationType.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
            InterpolationType.CUBIC_BEZIER -> cubicBezier(
                t,
                key.controlOut.x.coerceIn(0f, 1f), key.controlOut.y,
                key.controlIn.x.coerceIn(0f, 1f), key.controlIn.y,
            )
        }
    }

    /** Solves x(t)=time using Newton iterations, then bisection as a stable fallback. */
    fun cubicBezier(time: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = time.coerceIn(0f, 1f)
        if (x == 0f || x == 1f) return x
        fun coordinate(t: Float, a: Float, b: Float): Float {
            val inv = 1f - t
            return 3f * inv * inv * t * a + 3f * inv * t * t * b + t * t * t
        }
        fun derivative(t: Float, a: Float, b: Float): Float {
            val inv = 1f - t
            return 3f * inv * inv * a + 6f * inv * t * (b - a) + 3f * t * t * (1f - b)
        }
        var t = x
        repeat(8) {
            val error = coordinate(t, x1, x2) - x
            if (kotlin.math.abs(error) < 0.00001f) return coordinate(t, y1, y2)
            val slope = derivative(t, x1, x2)
            if (kotlin.math.abs(slope) < 0.00001f) return@repeat
            t = (t - error / slope).coerceIn(0f, 1f)
        }
        var low = 0f
        var high = 1f
        t = x
        repeat(24) {
            val estimated = coordinate(t, x1, x2)
            if (kotlin.math.abs(estimated - x) < 0.00001f) return coordinate(t, y1, y2)
            if (estimated > x) high = t else low = t
            t = (low + high) / 2f
        }
        return coordinate(t, y1, y2)
    }
}
