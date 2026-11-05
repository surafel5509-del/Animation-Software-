package com.animationstudio.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ToolType {
    SELECTION, DIRECT_SELECTION, MOVE, TRANSFORM, ROTATE, SCALE,
    BRUSH, PENCIL, PEN, BEZIER, ERASER, FILL, GRADIENT, EYEDROPPER,
    RECTANGLE, ELLIPSE, POLYGON, STAR, TEXT, SHAPE_EDITOR,
    BONE, CAMERA, ZOOM, HAND,
}

@Serializable
enum class BrushKind { PENCIL, INK, CALLIGRAPHY, SOFT, HARD, AIRBRUSH, TEXTURE, CUSTOM }

@Serializable
data class BrushPreset(
    val id: String,
    val name: String,
    val kind: BrushKind = BrushKind.INK,
    val size: Float = 12f,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val hardness: Float = 1f,
    val spacing: Float = 0.12f,
    val smoothing: Float = 0.35f,
    val stabilization: Float = 0.15f,
    val pressureSensitive: Boolean = true,
    val tiltSensitive: Boolean = true,
) {
    init {
        require(size > 0f && opacity in 0f..1f && flow in 0f..1f)
        require(hardness in 0f..1f && spacing > 0f)
        require(smoothing in 0f..1f && stabilization in 0f..1f)
    }

    companion object {
        val defaults = listOf(
            BrushPreset("pencil", "Pencil", BrushKind.PENCIL, size = 4f, hardness = 0.8f, smoothing = 0.15f),
            BrushPreset("ink", "Ink", BrushKind.INK, size = 12f, smoothing = 0.45f),
            BrushPreset("calligraphy", "Calligraphy", BrushKind.CALLIGRAPHY, size = 20f, smoothing = 0.5f),
            BrushPreset("soft", "Soft brush", BrushKind.SOFT, size = 36f, hardness = 0f, opacity = 0.7f),
            BrushPreset("hard", "Hard brush", BrushKind.HARD, size = 18f),
            BrushPreset("airbrush", "Airbrush", BrushKind.AIRBRUSH, size = 48f, hardness = 0f, opacity = 0.35f, flow = 0.4f),
        )
    }
}
