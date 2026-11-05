package com.animationstudio.app.ui.workspace

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.app.ui.EditorUiState
import com.animationstudio.app.ui.StudioViewModel
import com.animationstudio.app.ui.onCanvasCancel
import com.animationstudio.app.ui.onCanvasDown
import com.animationstudio.app.ui.onCanvasMove
import com.animationstudio.app.ui.onCanvasUp
import com.animationstudio.core.model.*
import com.animationstudio.layers.LayerEditor
import com.animationstudio.rigging.SkeletonSolver
import com.animationstudio.vector.VectorGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
fun StudioCanvas(viewModel: StudioViewModel, state: EditorUiState, modifier: Modifier = Modifier) {
    val project = state.project ?: return
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val touch = remember { CanvasTouchState() }
    val images by produceState<Map<String, ImageBitmap>>(emptyMap(), project.id, project.assets) {
        value = withContext(Dispatchers.IO) {
            project.assets.filter { it.kind == AssetKind.IMAGE }.mapNotNull { asset ->
                val file = (context.applicationContext as com.animationstudio.app.AnimationStudioApplication)
                    .container.repository.assets.file(project.id, asset.entryName)
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.let { asset.id to it }
            }.toMap()
        }
    }

    fun screenToCanvas(x: Float, y: Float): Vec2 {
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val zoom = state.viewportZoom.coerceAtLeast(.001f)
        val relative = Offset(x, y) - center - Offset(state.viewportPan.x, state.viewportPan.y)
        val angle = -state.viewportRotation
        val rotated = Offset(relative.x * cos(angle) - relative.y * sin(angle), relative.x * sin(angle) + relative.y * cos(angle))
        return Vec2(rotated.x / zoom + project.canvas.width / 2f, rotated.y / zoom + project.canvas.height / 2f)
    }

    Canvas(modifier.fillMaxSize().background(Color(0xff0c0e12))
        .onSizeChanged { size ->
            canvasSize = size
            if (state.viewportZoom == 0f && size.width > 0 && size.height > 0) {
                val fit = minOf(size.width / project.canvas.width.toFloat(), size.height / project.canvas.height.toFloat()) * .82f
                viewModel.setViewport(fit, Vec2.ZERO, 0f)
            }
        }
        .semantics { contentDescription = "Animation drawing canvas. Use touch or stylus to edit with the selected tool." }
        .pointerInteropFilter { event ->
            fun distance(): Float {
                if (event.pointerCount < 2) return 0f
                val dx = event.getX(0) - event.getX(1); val dy = event.getY(0) - event.getY(1)
                return sqrt(dx * dx + dy * dy)
            }
            fun angle(): Float = if (event.pointerCount < 2) 0f else atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
            fun centroidX() = if (event.pointerCount == 1) event.x else (event.getX(0) + event.getX(1)) / 2f
            fun centroidY() = if (event.pointerCount == 1) event.y else (event.getY(0) + event.getY(1)) / 2f
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touch.viewportGesture = state.currentTool == ToolType.HAND || state.currentTool == ToolType.ZOOM
                    touch.lastDistance = 0f; touch.lastAngle = 0f
                    touch.lastX = event.x; touch.lastY = event.y
                    if (!touch.viewportGesture) viewModel.onCanvasDown(screenToCanvas(event.x, event.y), event.pressure,
                        event.getAxisValue(MotionEvent.AXIS_TILT))
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!touch.viewportGesture) viewModel.onCanvasCancel()
                    touch.viewportGesture = true
                    touch.lastDistance = distance(); touch.lastAngle = angle()
                    touch.lastX = centroidX(); touch.lastY = centroidY()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (touch.viewportGesture && event.pointerCount >= 1) {
                        val cx = centroidX(); val cy = centroidY()
                        if (event.pointerCount >= 2) {
                            val newDistance = distance(); val newAngle = angle()
                            val factor = if (touch.lastDistance > 0f) newDistance / touch.lastDistance else 1f
                            viewModel.transformViewport(factor, Vec2(cx - touch.lastX, cy - touch.lastY), newAngle - touch.lastAngle)
                            touch.lastDistance = newDistance; touch.lastAngle = newAngle
                        } else if (state.currentTool == ToolType.ZOOM) {
                            viewModel.transformViewport(exp(-(cy - touch.lastY) / 300f), Vec2.ZERO, 0f)
                        } else viewModel.transformViewport(1f, Vec2(cx - touch.lastX, cy - touch.lastY), 0f)
                        touch.lastX = cx; touch.lastY = cy
                    } else {
                        val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                        viewModel.onCanvasMove(screenToCanvas(event.getX(index), event.getY(index)), event.getPressure(index),
                            event.getAxisValue(MotionEvent.AXIS_TILT, index))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!touch.viewportGesture) viewModel.onCanvasUp(screenToCanvas(event.x, event.y))
                }
                MotionEvent.ACTION_CANCEL -> if (!touch.viewportGesture) viewModel.onCanvasCancel()
            }
            event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        }) {
        val scene = project.activeScene
        val zoom = state.viewportZoom.coerceAtLeast(.001f)
        drawRect(Color(0xff0c0e12))
        withTransform({
            translate(size.width / 2f + state.viewportPan.x, size.height / 2f + state.viewportPan.y)
            rotate(Math.toDegrees(state.viewportRotation.toDouble()).toFloat(), Offset.Zero)
            scale(zoom, zoom, Offset.Zero)
            translate(-project.canvas.width / 2f, -project.canvas.height / 2f)
        }) {
            clipRect(0f, 0f, project.canvas.width.toFloat(), project.canvas.height.toFloat()) {
                if (state.preferences.checkerboardEnabled) drawCheckerboard(project.canvas)
                if (!project.canvas.transparent) drawRect(Color(project.canvas.backgroundColor))
                if (state.preferences.onionSkinEnabled && !state.isPlaying) {
                    drawOnionLayers(state, scene, images, before = true)
                    drawOnionLayers(state, scene, images, before = false)
                }
                LayerEditor.visibleLayers(scene).forEach { layer ->
                    layer.celAt(state.currentFrame)?.objects?.filter(VectorObject::visible)?.forEach { obj ->
                        drawVectorObject(obj, scene, state.currentFrame, images, alphaMultiplier = layer.opacity)
                    }
                }
                state.draft?.let { drawVectorObject(it, scene, state.currentFrame, images) }
                if (state.preferences.gridEnabled) drawGrid(project.canvas)
                drawRigs(scene, state)
                drawMotionPath(scene, state)
            }
            drawRect(Color(0xff8ab4ff), size = androidx.compose.ui.geometry.Size(project.canvas.width.toFloat(), project.canvas.height.toFloat()),
                style = Stroke(2f / zoom))
            val camera = scene.cameras.firstOrNull { it.id == scene.activeCameraId }
            if (camera != null) {
                val safe = camera.safeArea
                val left = project.canvas.width * (1f - safe) / 2f; val top = project.canvas.height * (1f - safe) / 2f
                drawRect(Color(0x668ab4ff), topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(project.canvas.width * safe, project.canvas.height * safe),
                    style = Stroke(1.5f / zoom, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / zoom, 8f / zoom))))
            }
            drawSelection(state, scene)
        }
        drawRulers(project, state)
    }
}

private fun DrawScope.drawCheckerboard(canvas: CanvasSettings) {
    drawRect(Color.White)
    val tile = 32f
    var row = 0
    var y = 0f
    while (y < canvas.height) {
        var column = 0
        var x = 0f
        while (x < canvas.width) {
            if ((row + column) % 2 == 0) drawRect(Color(0xffd8dbe0), topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(minOf(tile, canvas.width - x), minOf(tile, canvas.height - y)))
            x += tile; column++
        }
        y += tile; row++
    }
}

private fun DrawScope.drawGrid(canvas: CanvasSettings) {
    val color = Color(0x30353540)
    val step = 100f
    var x = step
    while (x < canvas.width) { drawLine(color, Offset(x, 0f), Offset(x, canvas.height.toFloat()), .7f); x += step }
    var y = step
    while (y < canvas.height) { drawLine(color, Offset(0f, y), Offset(canvas.width.toFloat(), y), .7f); y += step }
}

private fun DrawScope.drawOnionLayers(state: EditorUiState, scene: Scene, images: Map<String, ImageBitmap>, before: Boolean) {
    val range = if (before) state.preferences.onionBefore else state.preferences.onionAfter
    val tint = if (before) Color(0xffff6276) else Color(0xff52b9ff)
    for (offset in 1..range) {
        val frame = if (before) state.currentFrame - offset else state.currentFrame + offset
        if (frame !in 0 until (state.project?.durationFrames ?: 0)) continue
        LayerEditor.visibleLayers(scene).forEach { layer ->
            val cel = layer.celAt(frame) ?: return@forEach
            if (cel.frame == layer.celAt(state.currentFrame)?.frame) return@forEach
            cel.objects.filter(VectorObject::visible).forEach { obj ->
                drawVectorObject(obj, scene, frame, images, tint, state.preferences.onionOpacity / offset)
            }
        }
    }
}

private fun DrawScope.drawSelection(state: EditorUiState, scene: Scene) {
    val obj = state.project?.activeScene?.layers?.firstOrNull { it.id == state.selectedLayerId }
        ?.celAt(state.currentFrame)?.objects?.firstOrNull { it.id == state.selectedObjectId } ?: return
    val evaluated = AnimationEvaluator.transform(scene, obj.id, obj.transform, state.currentFrame)
    val bounds = VectorGeometry.bounds(VectorGeometry.withTransform(obj, evaluated)) ?: return
    val zoom = state.viewportZoom.coerceAtLeast(.02f)
    drawRect(Color(0xff8ab4ff), topLeft = Offset(bounds.left, bounds.top),
        size = androidx.compose.ui.geometry.Size(bounds.width, bounds.height), style = Stroke(1.5f / zoom))
    listOf(Offset(bounds.left, bounds.top), Offset(bounds.right, bounds.top), Offset(bounds.right, bounds.bottom), Offset(bounds.left, bounds.bottom),
        Offset(bounds.center.x, bounds.top), Offset(bounds.right, bounds.center.y), Offset(bounds.center.x, bounds.bottom), Offset(bounds.left, bounds.center.y)).forEach {
        drawRect(Color.White, topLeft = it - Offset(5f / zoom, 5f / zoom), size = androidx.compose.ui.geometry.Size(10f / zoom, 10f / zoom))
        drawRect(Color(0xff4268a0), topLeft = it - Offset(5f / zoom, 5f / zoom), size = androidx.compose.ui.geometry.Size(10f / zoom, 10f / zoom), style = Stroke(1f / zoom))
    }
    if (obj is VectorObject.PathObject && (state.currentTool == ToolType.DIRECT_SELECTION || state.currentTool == ToolType.SHAPE_EDITOR)) {
        obj.points.forEachIndexed { index, point ->
            val position = evaluated.apply(point.position).toOffset()
            drawCircle(if (index == state.selectedPointIndex) Color(0xffffff00) else Color(0xff8ab4ff), 6f / zoom, position)
            point.controlIn?.let { drawLine(Color(0xff75d5c3), position, evaluated.apply(it).toOffset(), 1f / zoom); drawCircle(Color(0xff75d5c3), 4f / zoom, evaluated.apply(it).toOffset()) }
            point.controlOut?.let { drawLine(Color(0xff75d5c3), position, evaluated.apply(it).toOffset(), 1f / zoom); drawCircle(Color(0xff75d5c3), 4f / zoom, evaluated.apply(it).toOffset()) }
        }
    }
}

private fun DrawScope.drawRigs(scene: Scene, state: EditorUiState) {
    scene.rigs.forEach { original ->
        val animated = original.copy(bones = original.bones.map { bone ->
            val rotation = scene.tracks.firstOrNull { it.targetId == bone.id && it.property == AnimatableProperty.BONE_ROTATION }
                ?.let { AnimationEvaluator.sample(it, state.currentFrame, bone.rotation) } ?: bone.rotation
            bone.copy(rotation = rotation)
        })
        SkeletonSolver.forwardKinematics(animated).values.forEach { pose ->
            drawLine(Color(0xffffb86b), pose.start.toOffset(), pose.end.toOffset(), 5f / state.viewportZoom.coerceAtLeast(.05f))
            drawCircle(if (pose.bone.id == state.selectedBoneId) Color.White else Color(0xffffb86b), 8f / state.viewportZoom.coerceAtLeast(.05f), pose.start.toOffset())
            drawCircle(Color(0xff332315), 3f / state.viewportZoom.coerceAtLeast(.05f), pose.start.toOffset())
        }
    }
}

private fun DrawScope.drawMotionPath(scene: Scene, state: EditorUiState) {
    val id = state.selectedObjectId ?: return
    val xTrack = scene.tracks.firstOrNull { it.targetId == id && it.property == AnimatableProperty.POSITION_X } ?: return
    val yTrack = scene.tracks.firstOrNull { it.targetId == id && it.property == AnimatableProperty.POSITION_Y } ?: return
    val frames = (xTrack.keyframes.map(Keyframe::frame) + yTrack.keyframes.map(Keyframe::frame)).distinct().sorted()
    val points = frames.map { frame -> Offset(AnimationEvaluator.sample(xTrack, frame), AnimationEvaluator.sample(yTrack, frame)) }
    if (points.size > 1) points.zipWithNext().forEach { (a, b) -> drawLine(Color(0xff75d5c3), a, b, 2f / state.viewportZoom.coerceAtLeast(.05f)) }
    points.forEach { drawCircle(Color(0xff75d5c3), 5f / state.viewportZoom.coerceAtLeast(.05f), it) }
}

private fun DrawScope.drawRulers(project: StudioProject, state: EditorUiState) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff8c94a3.toInt(); textSize = 20f }
    drawContext.canvas.nativeCanvas.drawText("${project.canvas.width} × ${project.canvas.height}  •  ${(state.viewportZoom * 100).roundToInt()}%",
        14f, 24f, paint)
}

private class CanvasTouchState {
    var viewportGesture = false
    var lastX = 0f
    var lastY = 0f
    var lastDistance = 0f
    var lastAngle = 0f
}
