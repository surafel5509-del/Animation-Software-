package com.animationstudio.app.ui

import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.core.model.*
import com.animationstudio.drawing.ShapeFactory
import com.animationstudio.drawing.StrokeBuilder
import com.animationstudio.graphics.Matrix3
import com.animationstudio.layers.LayerEditor
import com.animationstudio.rigging.SkeletonSolver
import com.animationstudio.vector.VectorGeometry
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class CanvasGesture(
    val tool: ToolType,
    val start: Vec2,
    val layerId: String? = null,
    val objectId: String? = null,
    val originalTransform: Transform? = null,
    val pointIndex: Int? = null,
    val stroke: StrokeBuilder? = null,
    val originalProject: StudioProject? = null,
    var historyRecorded: Boolean = false,
)

private val canvasGestures = WeakHashMap<StudioViewModel, CanvasGesture>()

fun StudioViewModel.onCanvasDown(point: Vec2, pressure: Float = 1f, tilt: Float = 0f) {
    val s = state.value
    val project = s.project ?: return
    val snapped = snap(point)
    when (s.currentTool) {
        ToolType.HAND, ToolType.ZOOM -> Unit
        ToolType.BRUSH, ToolType.PENCIL -> {
            val preset = if (s.currentTool == ToolType.PENCIL) s.brush.copy(kind = BrushKind.PENCIL) else s.brush
            val stroke = StrokeBuilder(preset, s.currentColor)
            stroke.add(point, pressure, tilt)
            canvasGestures[this] = CanvasGesture(s.currentTool, point, stroke = stroke)
            mutableState.update { it.copy(draft = stroke.preview()) }
        }
        ToolType.RECTANGLE, ToolType.ELLIPSE, ToolType.POLYGON, ToolType.STAR -> {
            canvasGestures[this] = CanvasGesture(s.currentTool, snapped)
            mutableState.update { it.copy(draft = ShapeFactory.create(s.currentTool, snapped, snapped, s.currentColor, s.brush.size)) }
        }
        ToolType.PEN, ToolType.BEZIER -> beginOrAppendPath(snapped)
        ToolType.TEXT -> showTextEntry(snapped)
        ToolType.ERASER -> hitTest(point)?.let { (layer, obj) ->
            editProject { it.updateActiveScene { scene -> LayerEditor.deleteObject(scene, layer.id, s.currentFrame, obj.id) } }
            selectObject(null)
        }
        ToolType.FILL -> hitTest(point)?.let { (_, obj) ->
            selectObject(obj.id)
            editProject { it.updateActiveScene { scene -> LayerEditor.updateObject(scene, findLayerForObject(obj.id) ?: return@updateActiveScene scene,
                s.currentFrame, obj.id) { VectorGeometry.withFill(it, FillStyle.Solid(s.currentColor)) } } }
        }
        ToolType.GRADIENT -> hitTest(point)?.let { (_, obj) ->
            val bounds = VectorGeometry.bounds(obj) ?: RectF.around(point, 100f, 100f)
            selectObject(obj.id)
            editProject { it.updateActiveScene { scene -> LayerEditor.updateObject(scene, findLayerForObject(obj.id) ?: return@updateActiveScene scene,
                s.currentFrame, obj.id) { VectorGeometry.withFill(it, FillStyle.LinearGradient(s.gradientColors,
                start = Vec2(bounds.left, bounds.top), end = Vec2(bounds.right, bounds.bottom))) } } }
        }
        ToolType.EYEDROPPER -> hitTest(point)?.let { (_, obj) -> VectorGeometry.colorOf(obj)?.let(::setColor) }
        ToolType.BONE -> {
            canvasGestures[this] = CanvasGesture(ToolType.BONE, snapped, originalProject = project)
            mutableState.update { it.copy(draft = boneGuide(snapped, snapped, s.currentColor)) }
        }
        ToolType.CAMERA -> {
            val camera = project.activeScene.cameras.firstOrNull { it.id == project.activeScene.activeCameraId }
            canvasGestures[this] = CanvasGesture(ToolType.CAMERA, point, objectId = camera?.id,
                originalTransform = camera?.transform, originalProject = project)
        }
        else -> beginObjectGesture(point)
    }
}

fun StudioViewModel.onCanvasMove(point: Vec2, pressure: Float = 1f, tilt: Float = 0f) {
    val gesture = canvasGestures[this]
    val s = state.value
    if (s.currentTool == ToolType.BEZIER && s.pathBuilding) {
        updateBezierHandle(point)
        return
    }
    when (gesture?.tool) {
        ToolType.BRUSH, ToolType.PENCIL -> {
            gesture.stroke?.add(point, pressure, tilt)
            mutableState.update { it.copy(draft = gesture.stroke?.preview()) }
        }
        ToolType.RECTANGLE, ToolType.ELLIPSE, ToolType.POLYGON, ToolType.STAR -> mutableState.update {
            it.copy(draft = ShapeFactory.create(gesture.tool, gesture.start, snap(point), s.currentColor, s.brush.size))
        }
        ToolType.BONE -> mutableState.update { it.copy(draft = boneGuide(gesture.start, snap(point), s.currentColor)) }
        ToolType.CAMERA -> {
            recordGestureCheckpoint(gesture)
            val original = gesture.originalTransform ?: Transform()
            val delta = point - gesture.start
            editProject(recordHistory = false) { it.updateActiveScene { scene -> scene.copy(cameras = scene.cameras.map { camera ->
                if (camera.id == gesture.objectId) camera.copy(transform = original.copy(position = original.position + delta)) else camera
            }) } }
        }
        ToolType.SELECTION, ToolType.MOVE, ToolType.TRANSFORM, ToolType.ROTATE, ToolType.SCALE,
        ToolType.DIRECT_SELECTION, ToolType.SHAPE_EDITOR -> moveObjectGesture(gesture, point)
        else -> Unit
    }
}

fun StudioViewModel.onCanvasUp(point: Vec2) {
    val gesture = canvasGestures[this] ?: return
    when (gesture.tool) {
        ToolType.BRUSH, ToolType.PENCIL -> gesture.stroke?.finish()?.takeIf { it.points.isNotEmpty() }?.let(::addObjectToCurrentCel)
        ToolType.RECTANGLE, ToolType.ELLIPSE, ToolType.POLYGON, ToolType.STAR -> {
            val shape = state.value.draft
            if (shape is VectorObject.ShapeObject && shape.bounds.width > 1f && shape.bounds.height > 1f) addObjectToCurrentCel(shape)
        }
        ToolType.BONE -> createBone(gesture.start, snap(point))
        else -> Unit
    }
    canvasGestures.remove(this)
    if (state.value.currentTool !in setOf(ToolType.PEN, ToolType.BEZIER)) mutableState.update { it.copy(draft = null) }
}

fun StudioViewModel.onCanvasCancel() {
    val gesture = canvasGestures.remove(this)
    if (gesture != null && gesture.historyRecorded && gesture.originalProject != null) replaceWithoutHistory(gesture.originalProject)
    mutableState.update { it.copy(draft = null) }
}

private fun StudioViewModel.beginObjectGesture(point: Vec2) {
    val s = state.value
    val hit = hitTest(point)
    if (hit == null) {
        selectObject(null)
        return
    }
    val (layer, obj) = hit
    mutableState.update { it.copy(selectedLayerId = layer.id, selectedObjectId = obj.id) }
    var pointIndex: Int? = null
    if (s.currentTool == ToolType.DIRECT_SELECTION || s.currentTool == ToolType.SHAPE_EDITOR) {
        if (obj is VectorObject.PathObject) {
            val evaluated = AnimationEvaluator.transform(s.project!!.activeScene, obj.id, obj.transform, s.currentFrame)
            pointIndex = obj.points.indices.minByOrNull { index ->
                VectorGeometry.distance(evaluated.apply(obj.points[index].position), point)
            }?.takeIf { index -> VectorGeometry.distance(evaluated.apply(obj.points[index].position), point) <= 24f / s.viewportZoom.coerceAtLeast(.1f) }
            mutableState.update { it.copy(selectedPointIndex = pointIndex) }
        }
    }
    canvasGestures[this] = CanvasGesture(s.currentTool, point, layer.id, obj.id, obj.transform, pointIndex, originalProject = s.project)
}

private fun StudioViewModel.moveObjectGesture(gesture: CanvasGesture, point: Vec2) {
    val s = state.value
    val layerId = gesture.layerId ?: return
    val objectId = gesture.objectId ?: return
    val original = gesture.originalTransform ?: return
    recordGestureCheckpoint(gesture)
    editProject(recordHistory = false) { project -> project.updateActiveScene { scene ->
        LayerEditor.updateObject(scene, layerId, s.currentFrame, objectId) { obj ->
            if (gesture.pointIndex != null && obj is VectorObject.PathObject) {
                val inverse = Matrix3.from(original).inverse()
                val local = inverse * point
                obj.copy(points = obj.points.mapIndexed { index, pathPoint -> if (index == gesture.pointIndex) pathPoint.copy(position = local) else pathPoint })
            } else {
                val delta = point - gesture.start
                val transform = when (gesture.tool) {
                    ToolType.ROTATE -> {
                        val center = VectorGeometry.bounds(obj)?.center ?: gesture.start
                        val a = atan2(gesture.start.y - center.y, gesture.start.x - center.x)
                        val b = atan2(point.y - center.y, point.x - center.x)
                        original.copy(rotation = original.rotation + b - a)
                    }
                    ToolType.SCALE -> {
                        val center = VectorGeometry.bounds(obj)?.center ?: gesture.start
                        val startDistance = VectorGeometry.distance(gesture.start, center).coerceAtLeast(1f)
                        val ratio = (VectorGeometry.distance(point, center) / startDistance).coerceIn(.01f, 100f)
                        original.copy(scale = Vec2(original.scale.x * ratio, original.scale.y * ratio))
                    }
                    else -> original.copy(position = original.position + delta)
                }
                VectorGeometry.withTransform(obj, transform)
            }
        }
    } }
}

private fun StudioViewModel.recordGestureCheckpoint(gesture: CanvasGesture) {
    if (!gesture.historyRecorded) {
        editProject { it }
        gesture.historyRecorded = true
    }
}

private fun StudioViewModel.hitTest(point: Vec2): Pair<Layer, VectorObject>? {
    val s = state.value
    val scene = s.project?.activeScene ?: return null
    return scene.layers.asReversed().firstNotNullOfOrNull { layer ->
        if (!layer.visible || layer.locked) null else layer.celAt(s.currentFrame)?.objects?.asReversed()?.firstNotNullOfOrNull { obj ->
            val evaluated = AnimationEvaluator.transform(scene, obj.id, obj.transform, s.currentFrame)
            val evaluatedObject = VectorGeometry.withTransform(obj, evaluated)
            if (VectorGeometry.hitTest(evaluatedObject, point, 10f / s.viewportZoom.coerceAtLeast(.1f))) layer to obj else null
        }
    }
}

private fun StudioViewModel.findLayerForObject(objectId: String): String? = state.value.project?.activeScene?.layers
    ?.firstOrNull { layer -> layer.celAt(state.value.currentFrame)?.objects?.any { it.id == objectId } == true }?.id

private fun StudioViewModel.beginOrAppendPath(point: Vec2) {
    val existing = state.value.draft as? VectorObject.PathObject
    val path = existing ?: VectorObject.PathObject(UUID.randomUUID().toString(), "Bezier path", emptyList(),
        stroke = StrokeStyle(state.value.currentColor, state.value.brush.size))
    mutableState.update { it.copy(draft = path.copy(points = path.points + PathPoint(point)), pathBuilding = true) }
}

private fun StudioViewModel.updateBezierHandle(point: Vec2) {
    val path = state.value.draft as? VectorObject.PathObject ?: return
    val last = path.points.lastOrNull() ?: return
    val control = last.position + (last.position - point)
    mutableState.update { it.copy(draft = path.copy(points = path.points.dropLast(1) + last.copy(controlOut = point, controlIn = control))) }
}

fun StudioViewModel.finishPath() {
    val path = state.value.draft as? VectorObject.PathObject
    mutableState.update { it.copy(pathBuilding = false) }
    if (path != null && path.points.size >= 2) addObjectToCurrentCel(path)
    mutableState.update { it.copy(draft = null) }
}

fun StudioViewModel.closePath() {
    val path = state.value.draft as? VectorObject.PathObject ?: return
    mutableState.update { it.copy(draft = path.copy(closed = true, fill = FillStyle.Solid(state.value.currentColor))) }
    finishPath()
}

private fun StudioViewModel.createBone(start: Vec2, end: Vec2) {
    val delta = end - start
    val length = sqrt(delta.lengthSquared())
    if (length < 8f) return
    val s = state.value
    val project = s.project ?: return
    val scene = project.activeScene
    val rigId = scene.rigs.firstOrNull()?.id ?: UUID.randomUUID().toString()
    val id = UUID.randomUUID().toString()
    val bone = Bone(id, "Bone ${scene.rigs.flatMap(CharacterRig::bones).size + 1}", s.selectedBoneId,
        start = if (s.selectedBoneId == null) start else SkeletonSolver.forwardKinematics(scene.rigs.first())[s.selectedBoneId]?.end ?: start,
        length = length, rotation = atan2(delta.y, delta.x))
    editProject { it.updateActiveScene { current ->
        val rig = current.rigs.firstOrNull() ?: CharacterRig(rigId)
        val updatedScene = current.copy(rigs = if (current.rigs.isEmpty()) listOf(rig.copy(bones = rig.bones + bone))
            else current.rigs.map { if (it.id == rig.id) it.copy(bones = rig.bones + bone) else it })
        if (updatedScene.layers.any { it.type == LayerType.SKELETON }) updatedScene else
            LayerEditor.add(updatedScene, Layer(UUID.randomUUID().toString(), "Skeleton", LayerType.SKELETON, cels = emptyList()))
    } }
    mutableState.update { it.copy(selectedBoneId = id, selectedObjectId = null, inspectorTab = InspectorTab.RIGGING) }
}

private fun boneGuide(start: Vec2, end: Vec2, color: Int) = VectorObject.PathObject("bone-guide",
    points = listOf(PathPoint(start), PathPoint(end)), stroke = StrokeStyle(color, 5f))

private fun StudioViewModel.snap(point: Vec2): Vec2 {
    if (!state.value.preferences.gridEnabled) return point
    val grid = 50f
    return Vec2((point.x / grid).roundToInt() * grid, (point.y / grid).roundToInt() * grid)
}

fun StudioViewModel.selectBone(id: String?) = mutableState.update { it.copy(selectedBoneId = id, selectedObjectId = null) }

fun StudioViewModel.updateBone(id: String, transform: (Bone) -> Bone) = editProject { it.updateActiveScene { scene ->
    scene.copy(rigs = scene.rigs.map { rig -> rig.copy(bones = rig.bones.map { bone -> if (bone.id == id) transform(bone) else bone }) })
} }

fun StudioViewModel.deleteBone(id: String) = editProject { it.updateActiveScene { scene ->
    val remaining = scene.rigs.flatMap(CharacterRig::bones).filter { it.id != id }.map { bone ->
        if (bone.parentId == id) bone.copy(parentId = null) else bone
    }
    scene.copy(rigs = scene.rigs.map { it.copy(bones = it.bones.filter { bone -> bone.id != id }.map { bone ->
        if (bone.parentId == id) bone.copy(parentId = null) else bone }) })
} }

fun StudioViewModel.solveIk(id: String, target: Vec2) = editProject { it.updateActiveScene { scene ->
    scene.copy(rigs = scene.rigs.map { rig -> SkeletonSolver.solveIk(rig, id, target) })
} }

fun StudioViewModel.createBlankCel() {
    val s = state.value; val layerId = s.selectedLayerId ?: return
    editProject { it.updateActiveScene { scene -> LayerEditor.update(scene, layerId) { layer ->
        if (layer.cels.any { cel -> cel.frame == s.currentFrame }) layer else layer.copy(cels = (layer.cels + DrawingCel(s.currentFrame)).sortedBy(DrawingCel::frame))
    } } }
}
