package com.animationstudio.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CanvasSettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val transparent: Boolean = false,
) {
    init { require(width in 1..16384 && height in 1..16384) { "Canvas must be between 1 and 16384 pixels" } }
}

@Serializable
data class StudioProject(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val canvas: CanvasSettings = CanvasSettings(),
    val fps: Int = 24,
    val durationFrames: Int = 240,
    val scenes: List<Scene>,
    val activeSceneId: String,
    val assets: List<AssetMetadata> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Project name cannot be blank" }
        require(fps in 1..120) { "FPS must be between 1 and 120" }
        require(durationFrames > 0) { "Duration must be positive" }
        require(scenes.isNotEmpty()) { "A project needs at least one scene" }
        require(scenes.any { it.id == activeSceneId }) { "Active scene is missing" }
        require(scenes.map(Scene::id).distinct().size == scenes.size) { "Scene IDs must be unique" }
    }

    val activeScene: Scene get() = scenes.first { it.id == activeSceneId }

    fun updateScene(scene: Scene): StudioProject = copy(
        scenes = scenes.map { if (it.id == scene.id) scene else it },
    )

    fun updateActiveScene(transform: (Scene) -> Scene): StudioProject = updateScene(transform(activeScene))

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

object ProjectFactory {
    fun create(
        name: String,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 24,
        durationFrames: Int = 240,
        backgroundColor: Int = 0xFFFFFFFF.toInt(),
        transparent: Boolean = false,
        now: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
    ): StudioProject {
        val sceneId = UUID.randomUUID().toString()
        val layerId = UUID.randomUUID().toString()
        val cameraId = UUID.randomUUID().toString()
        val scene = Scene(
            id = sceneId,
            layers = listOf(Layer(id = layerId, name = "Drawing 1")),
            cameras = listOf(CameraSettings(id = cameraId)),
            activeCameraId = cameraId,
            workArea = WorkArea(0, durationFrames),
        )
        return StudioProject(
            id = id,
            name = name.trim().ifBlank { "Untitled animation" },
            createdAt = now,
            modifiedAt = now,
            canvas = CanvasSettings(width, height, backgroundColor, transparent),
            fps = fps,
            durationFrames = durationFrames,
            scenes = listOf(scene),
            activeSceneId = sceneId,
        )
    }
}
