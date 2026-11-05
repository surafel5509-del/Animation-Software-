package com.animationstudio.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnimatableProperty {
    POSITION_X, POSITION_Y, ROTATION, SCALE_X, SCALE_Y, OPACITY,
    COLOR, SHAPE_PARAMETER, CAMERA_ZOOM, BONE_ROTATION, BONE_SCALE,
}

@Serializable
enum class InterpolationType { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUBIC_BEZIER, HOLD }

@Serializable
data class Keyframe(
    val frame: Int,
    val value: Float = 0f,
    val interpolation: InterpolationType = InterpolationType.LINEAR,
    val controlIn: Vec2 = Vec2(0.25f, 0f),
    val controlOut: Vec2 = Vec2(0.75f, 1f),
    val colorValue: Int? = null,
) {
    init { require(frame >= 0) { "Keyframe frame cannot be negative" } }
}

@Serializable
data class AnimationTrack(
    val id: String,
    val targetId: String,
    val property: AnimatableProperty,
    val keyframes: List<Keyframe> = emptyList(),
) {
    fun sorted() = copy(keyframes = keyframes.sortedBy(Keyframe::frame))
}

@Serializable
data class WorkArea(val start: Int = 0, val end: Int = 240, val loop: Boolean = true) {
    init {
        require(start >= 0 && end > start) { "Invalid work area" }
    }
}
