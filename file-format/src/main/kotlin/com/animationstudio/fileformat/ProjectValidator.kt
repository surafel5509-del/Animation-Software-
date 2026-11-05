package com.animationstudio.fileformat

import com.animationstudio.core.model.*

object ProjectValidator {
    fun validate(project: StudioProject) {
        try {
            require(project.formatVersion in 1..StudioProject.CURRENT_FORMAT_VERSION) { "Unsupported project version" }
            require(project.durationFrames <= 1_000_000) { "Duration exceeds supported limit" }
            require(project.scenes.size <= 1000) { "Too many scenes" }
            val assetIds = project.assets.map(AssetMetadata::id)
            require(assetIds.distinct().size == assetIds.size) { "Duplicate asset IDs" }
            project.scenes.forEach { validateScene(it, assetIds.toSet()) }
        } catch (error: IllegalArgumentException) {
            throw ProjectFormatException(error.message ?: "Project validation failed", error)
        }
    }

    private fun validateScene(scene: Scene, assetIds: Set<String>) {
        require(scene.layers.size <= 10_000) { "Too many layers" }
        val layerIds = scene.layers.map(Layer::id)
        require(layerIds.distinct().size == layerIds.size) { "Duplicate layer IDs" }
        val objectIds = scene.layers.flatMap { it.cels }.flatMap { it.objects }.map(VectorObject::id)
        require(objectIds.distinct().size == objectIds.size) { "Duplicate object IDs" }
        scene.layers.forEach { layer ->
            require(layer.parentId == null || layer.parentId in layerIds) { "Missing parent layer" }
            require(layer.opacity in 0f..1f) { "Invalid layer opacity" }
            require(layer.cels.map(DrawingCel::frame).distinct().size == layer.cels.size) { "Duplicate cel frames" }
            layer.cels.forEach { cel ->
                require(cel.frame < 1_000_000) { "Cel frame exceeds supported limit" }
                require(cel.objects.size <= 100_000) { "Too many objects in cel" }
                cel.objects.forEach(::validateObject)
            }
        }
        scene.tracks.forEach { track ->
            require(track.keyframes.map(Keyframe::frame).distinct().size == track.keyframes.size) { "Duplicate track keyframes" }
            require(track.keyframes.all { it.value.isFinite() && it.frame < 1_000_000 }) { "Invalid keyframe" }
        }
        scene.rigs.forEach { rig ->
            val ids = rig.bones.map(Bone::id)
            require(ids.distinct().size == ids.size) { "Duplicate bone IDs" }
            require(rig.bones.all { it.parentId == null || it.parentId in ids }) { "Missing parent bone" }
        }
        require(scene.audio.all { it.assetId in assetIds }) { "Audio clip references a missing asset" }
    }

    private fun validateObject(obj: VectorObject) {
        require(obj.id.isNotBlank()) { "Blank object ID" }
        require(obj.transform.position.x.isFinite() && obj.transform.position.y.isFinite()) { "Invalid transform" }
        require(obj.transform.rotation.isFinite() && obj.transform.scale.x.isFinite() && obj.transform.scale.y.isFinite()) { "Invalid transform" }
        when (obj) {
            is VectorObject.PathObject -> {
                require(obj.points.size <= 1_000_000) { "Path has too many points" }
                require(obj.points.all { it.position.x.isFinite() && it.position.y.isFinite() && it.pressure.isFinite() }) { "Invalid path point" }
                require((obj.stroke?.width ?: 1f) >= 0f) { "Invalid stroke width" }
            }
            is VectorObject.ShapeObject -> require(obj.vertices in 3..100 && obj.bounds.width.isFinite() && obj.bounds.height.isFinite()) { "Invalid shape" }
            is VectorObject.TextObject -> require(obj.text.length <= 100_000 && obj.size > 0f && obj.size.isFinite()) { "Invalid text" }
            is VectorObject.ImageObject -> require(obj.assetId.isNotBlank()) { "Missing image asset" }
        }
    }
}
