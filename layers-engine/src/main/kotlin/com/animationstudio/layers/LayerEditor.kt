package com.animationstudio.layers

import com.animationstudio.core.model.*
import java.util.UUID

object LayerEditor {
    fun add(scene: Scene, layer: Layer): Scene = scene.copy(layers = scene.layers + layer)

    fun update(scene: Scene, id: String, transform: (Layer) -> Layer): Scene = scene.copy(
        layers = scene.layers.map { if (it.id == id) transform(it) else it },
    )

    fun remove(scene: Scene, id: String): Scene {
        val removed = descendants(scene.layers, id) + id
        return scene.copy(
            layers = scene.layers.filterNot { it.id in removed },
            tracks = scene.tracks.filterNot { it.targetId in removed },
        )
    }

    fun duplicate(scene: Scene, id: String, newId: String = UUID.randomUUID().toString()): Scene {
        val index = scene.layers.indexOfFirst { it.id == id }
        if (index < 0) return scene
        val source = scene.layers[index]
        val objectIds = mutableMapOf<String, String>()
        val copiedCels = source.cels.map { cel -> cel.copy(objects = cel.objects.map { obj ->
            copyObject(obj, UUID.randomUUID().toString()).also { copy -> objectIds[obj.id] = copy.id }
        }) }
        val duplicate = source.copy(id = newId, name = "${source.name} copy", cels = copiedCels)
        val copiedTracks = scene.tracks.filter { it.targetId == id || it.targetId in objectIds }.map { track ->
            track.copy(id = UUID.randomUUID().toString(), targetId = if (track.targetId == id) newId else objectIds.getValue(track.targetId))
        }
        return scene.copy(
            layers = scene.layers.toMutableList().apply { add(index + 1, duplicate) },
            tracks = scene.tracks + copiedTracks,
        )
    }

    fun reorder(scene: Scene, id: String, delta: Int): Scene {
        val from = scene.layers.indexOfFirst { it.id == id }
        if (from < 0) return scene
        val to = (from + delta).coerceIn(scene.layers.indices)
        if (from == to) return scene
        return scene.copy(layers = scene.layers.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun setParent(scene: Scene, id: String, parentId: String?): Scene {
        if (parentId == id || (parentId != null && parentId in descendants(scene.layers, id))) return scene
        if (parentId != null && scene.layers.none { it.id == parentId }) return scene
        return update(scene, id) { it.copy(parentId = parentId) }
    }

    fun descendants(layers: List<Layer>, id: String): Set<String> {
        val result = mutableSetOf<String>()
        fun visit(parent: String) {
            layers.filter { it.parentId == parent }.forEach { if (result.add(it.id)) visit(it.id) }
        }
        visit(id)
        return result
    }

    fun updateObject(scene: Scene, layerId: String, frame: Int, objectId: String, update: (VectorObject) -> VectorObject): Scene =
        update(scene, layerId) { layer ->
            val cel = layer.celAt(frame) ?: DrawingCel(frame)
            val updated = cel.copy(objects = cel.objects.map { if (it.id == objectId) update(it) else it })
            layer.copy(cels = layer.cels.filterNot { it.frame == cel.frame } + updated)
        }

    fun addObject(scene: Scene, layerId: String, frame: Int, obj: VectorObject): Scene = update(scene, layerId) { layer ->
        val cel = layer.celAt(frame) ?: DrawingCel(frame)
        val inherited = when {
            cel.frame == frame -> cel.objects
            layer.type == LayerType.RASTER -> emptyList()
            else -> cel.objects.map { copyObject(it, UUID.randomUUID().toString()) }
        }
        val updated = if (cel.frame == frame) cel else DrawingCel(frame, objects = inherited)
        layer.copy(cels = (layer.cels.filterNot { it.frame == updated.frame } + updated.copy(objects = updated.objects + obj)).sortedBy(DrawingCel::frame))
    }

    fun deleteObject(scene: Scene, layerId: String, frame: Int, objectId: String): Scene = update(scene, layerId) { layer ->
        val cel = layer.celAt(frame) ?: return@update layer
        layer.copy(cels = layer.cels.map { if (it.frame == cel.frame) it.copy(objects = it.objects.filterNot { it.id == objectId }) else it })
    }

    private fun copyObject(obj: VectorObject, id: String): VectorObject = when (obj) {
        is VectorObject.PathObject -> obj.copy(id = id)
        is VectorObject.ShapeObject -> obj.copy(id = id)
        is VectorObject.TextObject -> obj.copy(id = id)
        is VectorObject.ImageObject -> obj.copy(id = id)
    }

    fun visibleLayers(scene: Scene): List<Layer> {
        val hiddenGroups = scene.layers.filter { !it.visible }.flatMap { descendants(scene.layers, it.id) }.toSet()
        return scene.layers.filter { it.visible && it.id !in hiddenGroups }
    }
}
