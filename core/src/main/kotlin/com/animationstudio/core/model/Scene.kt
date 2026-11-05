package com.animationstudio.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LayerType { VECTOR, RASTER, GROUP, SKELETON, BONE, IMAGE, TEXT, AUDIO, CAMERA, MASK, EFFECT }

@Serializable
enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD }

@Serializable
sealed class FillStyle {
    @Serializable @SerialName("solid")
    data class Solid(val color: Int) : FillStyle()

    @Serializable @SerialName("linear")
    data class LinearGradient(
        val colors: List<Int>,
        val stops: List<Float> = colors.indices.map { it.toFloat() / (colors.size - 1).coerceAtLeast(1) },
        val start: Vec2 = Vec2.ZERO,
        val end: Vec2 = Vec2(100f, 0f),
    ) : FillStyle()

    @Serializable @SerialName("radial")
    data class RadialGradient(
        val colors: List<Int>,
        val stops: List<Float> = colors.indices.map { it.toFloat() / (colors.size - 1).coerceAtLeast(1) },
        val center: Vec2 = Vec2.ZERO,
        val radius: Float = 100f,
    ) : FillStyle()
}

@Serializable
data class StrokeStyle(
    val color: Int = 0xFF222222.toInt(),
    val width: Float = 8f,
    val opacity: Float = 1f,
    val cap: StrokeCap = StrokeCap.ROUND,
    val join: StrokeJoin = StrokeJoin.ROUND,
    val dashPattern: List<Float> = emptyList(),
)

@Serializable
enum class StrokeCap { BUTT, ROUND, SQUARE }
@Serializable
enum class StrokeJoin { MITER, ROUND, BEVEL }

@Serializable
data class PathPoint(
    val position: Vec2,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val controlIn: Vec2? = null,
    val controlOut: Vec2? = null,
)

@Serializable
sealed class VectorObject {
    abstract val id: String
    abstract val name: String
    abstract val transform: Transform
    abstract val visible: Boolean
    abstract val locked: Boolean

    @Serializable @SerialName("path")
    data class PathObject(
        override val id: String,
        override val name: String = "Path",
        val points: List<PathPoint>,
        val closed: Boolean = false,
        val brush: BrushKind = BrushKind.INK,
        val stroke: StrokeStyle? = StrokeStyle(),
        val fill: FillStyle? = null,
        override val transform: Transform = Transform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
    ) : VectorObject()

    @Serializable @SerialName("shape")
    data class ShapeObject(
        override val id: String,
        override val name: String = "Shape",
        val shape: ShapeType,
        val bounds: RectF,
        val stroke: StrokeStyle? = StrokeStyle(),
        val fill: FillStyle? = FillStyle.Solid(0xFF4F8CFF.toInt()),
        val cornerRadius: Float = 0f,
        val vertices: Int = 5,
        val innerRadiusRatio: Float = 0.45f,
        override val transform: Transform = Transform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
    ) : VectorObject()

    @Serializable @SerialName("text")
    data class TextObject(
        override val id: String,
        override val name: String = "Text",
        val text: String,
        val position: Vec2,
        val size: Float = 64f,
        val color: Int = 0xFFFFFFFF.toInt(),
        val fontFamily: String = "sans-serif",
        val bold: Boolean = false,
        override val transform: Transform = Transform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
    ) : VectorObject()

    @Serializable @SerialName("image")
    data class ImageObject(
        override val id: String,
        override val name: String = "Image",
        val assetId: String,
        val bounds: RectF,
        override val transform: Transform = Transform(),
        override val visible: Boolean = true,
        override val locked: Boolean = false,
    ) : VectorObject()
}

@Serializable
enum class ShapeType { RECTANGLE, ELLIPSE, POLYGON, STAR }

@Serializable
data class DrawingCel(
    val frame: Int,
    val exposure: Int = 1,
    val objects: List<VectorObject> = emptyList(),
) {
    init { require(frame >= 0 && exposure > 0) { "Invalid cel timing" } }
    fun exposedAt(frame: Int) = frame >= this.frame && frame < this.frame + exposure
}

@Serializable
enum class EffectType { GAUSSIAN_BLUR, DROP_SHADOW, GLOW, BRIGHTNESS, CONTRAST, SATURATION, HUE, OPACITY, MOTION_BLUR, MASK }

@Serializable
data class EffectSettings(
    val id: String,
    val type: EffectType,
    val enabled: Boolean = true,
    val radius: Float = 8f,
    val intensity: Float = 1f,
    val offset: Vec2 = Vec2.ZERO,
    val color: Int = 0xFF000000.toInt(),
)

@Serializable
data class Layer(
    val id: String,
    val name: String,
    val type: LayerType = LayerType.VECTOR,
    val parentId: String? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val cels: List<DrawingCel> = listOf(DrawingCel(frame = 0, exposure = 1)),
    val effects: List<EffectSettings> = emptyList(),
) {
    fun celAt(frame: Int): DrawingCel? = cels.filter { it.exposedAt(frame) }.maxByOrNull { it.frame }
}

@Serializable
data class Bone(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val start: Vec2,
    val length: Float = 100f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val inheritRotation: Boolean = true,
    val inheritScale: Boolean = true,
    val minRotation: Float = -3.141592653589793f,
    val maxRotation: Float = 3.141592653589793f,
    val pinned: Boolean = false,
) {
    init { require(length > 0f && scale > 0f && minRotation <= maxRotation) }
}

@Serializable
data class SkinWeight(val vertexIndex: Int, val boneId: String, val weight: Float) {
    init { require(vertexIndex >= 0 && weight >= 0f) }
}

@Serializable
data class CharacterRig(
    val id: String,
    val name: String = "Character rig",
    val bones: List<Bone> = emptyList(),
    val bindings: Map<String, List<SkinWeight>> = emptyMap(),
)

@Serializable
data class CameraSettings(
    val id: String,
    val name: String = "Camera",
    val transform: Transform = Transform(),
    val zoom: Float = 1f,
    val safeArea: Float = 0.9f,
) {
    init { require(zoom > 0f && safeArea in 0f..1f) }
}

@Serializable
data class AudioClip(
    val id: String,
    val assetId: String,
    val name: String,
    val startFrame: Int = 0,
    val trimStartMs: Long = 0,
    val trimEndMs: Long? = null,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val muted: Boolean = false,
) {
    init { require(startFrame >= 0 && trimStartMs >= 0 && volume in 0f..1f) }
}

@Serializable
data class Scene(
    val id: String,
    val name: String = "Scene 1",
    val layers: List<Layer> = emptyList(),
    val tracks: List<AnimationTrack> = emptyList(),
    val rigs: List<CharacterRig> = emptyList(),
    val cameras: List<CameraSettings> = emptyList(),
    val activeCameraId: String? = null,
    val audio: List<AudioClip> = emptyList(),
    val workArea: WorkArea = WorkArea(),
)

@Serializable
enum class AssetKind { IMAGE, AUDIO, VIDEO, FONT, OTHER }

@Serializable
data class AssetMetadata(
    val id: String,
    val kind: AssetKind,
    val displayName: String,
    val mimeType: String,
    val entryName: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
)
