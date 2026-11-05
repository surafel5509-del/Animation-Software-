package com.animationstudio.app.ui.workspace

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asFrameworkPaint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.*
import com.animationstudio.core.model.*
import kotlin.math.roundToInt

@Composable
fun TimelinePanel(viewModel: StudioViewModel, state: EditorUiState, modifier: Modifier = Modifier) {
    val project = state.project ?: return
    val scene = project.activeScene
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val frameWidth = 30.dp * state.timelineZoom
    val frameWidthPx = with(LocalDensity.current) { frameWidth.toPx() }
    val timelineWidth = frameWidth * project.durationFrames
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ viewModel.seek(scene.workArea.start) }) { Icon(Icons.Default.SkipPrevious, "Work area start") }
            IconButton(viewModel::togglePlayback) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else "Play") }
            IconButton({ viewModel.seek((state.currentFrame - 1).coerceAtLeast(0)) }) { Icon(Icons.Default.ChevronLeft, "Previous frame") }
            IconButton({ viewModel.seek((state.currentFrame + 1).coerceAtMost(project.durationFrames - 1)) }) { Icon(Icons.Default.ChevronRight, "Next frame") }
            IconButton({ viewModel.seek((minOf(scene.workArea.end, project.durationFrames) - 1).coerceAtLeast(0)) }) { Icon(Icons.Default.SkipNext, "Work area end") }
            IconButton({ viewModel.setWorkArea(scene.workArea.start, scene.workArea.end, !scene.workArea.loop) }) {
                Icon(Icons.Default.Repeat, "Loop", tint = if (scene.workArea.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${state.currentFrame} / ${project.durationFrames}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
            Text("${project.fps} fps", style = MaterialTheme.typography.labelMedium)
            IconButton(viewModel::insertFrame) { Icon(Icons.Default.Add, "Insert frame") }
            IconButton(viewModel::deleteFrame) { Icon(Icons.Default.Remove, "Delete frame") }
            IconButton(viewModel::duplicateCel) { Icon(Icons.Default.ContentCopy, "Duplicate cel") }
            TextButton(viewModel::createBlankCel) { Text("Blank cel") }
            TextButton({ viewModel.setExposure((scene.layers.firstOrNull { it.id == state.selectedLayerId }?.celAt(state.currentFrame)?.exposure ?: 1) + 1) }) { Text("+ Exposure") }
            TextButton({ viewModel.setWorkArea(state.currentFrame, scene.workArea.end) }, enabled = state.currentFrame < scene.workArea.end - 1) { Text("Work in") }
            TextButton({ viewModel.setWorkArea(scene.workArea.start, state.currentFrame + 1) }, enabled = state.currentFrame > scene.workArea.start) { Text("Work out") }
            Text("Zoom", style = MaterialTheme.typography.labelSmall)
            Slider(state.timelineZoom, viewModel::setTimelineZoom, valueRange = .4f..5f, modifier = Modifier.width(110.dp))
        }
        HorizontalDivider()
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(156.dp).fillMaxHeight()) {
                Box(Modifier.height(30.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { Text("  TRACKS", style = MaterialTheme.typography.labelSmall) }
                Column(Modifier.verticalScroll(vertical)) {
                    scene.layers.forEach { layer -> TrackLabel(layer, state, viewModel) }
                    scene.audio.forEach { clip -> Text(clip.name, Modifier.height(40.dp).fillMaxWidth().padding(12.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(horizontal)) {
                FrameRuler(project, state, frameWidthPx, timelineWidth, viewModel::seek)
                Column(Modifier.verticalScroll(vertical)) {
                    scene.layers.forEach { layer ->
                        TrackCanvas(project, state, layer, frameWidthPx, timelineWidth, viewModel::seek)
                    }
                    scene.audio.forEach { clip ->
                        AudioTrackCanvas(project, state, clip, frameWidthPx, timelineWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackLabel(layer: Layer, state: EditorUiState, viewModel: StudioViewModel) {
    Row(Modifier.fillMaxWidth().height(40.dp).background(if (layer.id == state.selectedLayerId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton({ viewModel.selectLayer(layer.id) }, Modifier.size(30.dp)) {
            Icon(Icons.Default.Circle, "Select ${layer.name}", Modifier.size(10.dp), tint = if (layer.id == state.selectedLayerId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(layer.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        IconButton({ viewModel.updateLayer(layer.id) { it.copy(visible = !it.visible) } }, Modifier.size(28.dp)) {
            Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibility", Modifier.size(14.dp))
        }
        IconButton({ viewModel.updateLayer(layer.id) { it.copy(locked = !it.locked) } }, Modifier.size(28.dp)) {
            Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock", Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FrameRuler(project: StudioProject, state: EditorUiState, frameWidthPx: Float, width: androidx.compose.ui.unit.Dp, onSeek: (Int) -> Unit) {
    val textPaint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply { color = 0xffaeb5c2.toInt(); textSize = 18f }
    Canvas(Modifier.width(width).height(30.dp).pointerInput(project.durationFrames, frameWidthPx) {
        detectDragGestures(
            onDragStart = { offset -> onSeek((offset.x / frameWidthPx).toInt()) },
            onDrag = { change, _ -> onSeek((change.position.x / frameWidthPx).toInt()); change.consume() },
        )
    }) {
        drawRect(Color(0xff191c22))
        for (frame in 0 until project.durationFrames) {
            val x = frame * frameWidthPx
            val major = frame % project.fps == 0 || frame % 5 == 0 && frameWidthPx > 15f
            drawLine(Color(0xff626977), Offset(x, if (major) 8f else 20f), Offset(x, size.height), .7f)
            if (major) drawContext.canvas.nativeCanvas.drawText("$frame", x + 3f, 18f, textPaint)
        }
        val startX = project.activeScene.workArea.start * frameWidthPx
        val endX = project.activeScene.workArea.end * frameWidthPx
        drawRect(Color(0x22111318), topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(startX, size.height))
        drawRect(Color(0x22111318), topLeft = Offset(endX, 0f), size = androidx.compose.ui.geometry.Size((size.width - endX).coerceAtLeast(0f), size.height))
        val playhead = state.currentFrame * frameWidthPx
        drawLine(Color(0xffff6276), Offset(playhead, 0f), Offset(playhead, size.height), 2f)
    }
}

@Composable
private fun TrackCanvas(project: StudioProject, state: EditorUiState, layer: Layer, frameWidthPx: Float, width: androidx.compose.ui.unit.Dp, onSeek: (Int) -> Unit) {
    val scene = project.activeScene
    val objectIds = layer.cels.flatMap(DrawingCel::objects).map(VectorObject::id).toSet() + layer.id
    val keyframes = scene.tracks.filter { it.targetId in objectIds }.flatMap(AnimationTrack::keyframes)
    Canvas(Modifier.width(width).height(40.dp).pointerInput(frameWidthPx) {
        detectDragGestures(
            onDragStart = { offset -> onSeek((offset.x / frameWidthPx).toInt()) },
            onDrag = { change, _ -> onSeek((change.position.x / frameWidthPx).toInt()); change.consume() },
        )
    }) {
        drawRect(if (layer.id == state.selectedLayerId) Color(0xff25344d) else Color(0xff171a20))
        drawLine(Color(0xff303641), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 1f)
        layer.cels.forEach { cel ->
            val left = cel.frame * frameWidthPx
            val celWidth = (cel.exposure * frameWidthPx).coerceAtLeast(3f)
            drawRect(Color(0xff496d91), Offset(left + 1f, 10f), androidx.compose.ui.geometry.Size(celWidth - 2f, 20f))
        }
        keyframes.forEach { key ->
            val x = key.frame * frameWidthPx
            val path = Path().apply { moveTo(x, 12f); lineTo(x + 6f, 20f); lineTo(x, 28f); lineTo(x - 6f, 20f); close() }
            drawPath(path, Color(0xffffd166))
        }
        val x = state.currentFrame * frameWidthPx
        drawLine(Color(0xffff6276), Offset(x, 0f), Offset(x, size.height), 1.5f)
    }
}

@Composable
private fun AudioTrackCanvas(project: StudioProject, state: EditorUiState, clip: AudioClip, frameWidthPx: Float, width: androidx.compose.ui.unit.Dp) {
    val waveform = state.waveforms[clip.assetId].orEmpty()
    Canvas(Modifier.width(width).height(40.dp)) {
        drawRect(Color(0xff17231f))
        val start = clip.startFrame * frameWidthPx
        val durationFrames = clip.trimEndMs?.let { (it - clip.trimStartMs) * project.fps / 1000f } ?: project.durationFrames.toFloat()
        val clipWidth = durationFrames * frameWidthPx
        drawRect(Color(0xff285c4d), Offset(start, 5f), androidx.compose.ui.geometry.Size(clipWidth.coerceAtLeast(1f), size.height - 10f))
        waveform.forEachIndexed { index, peak ->
            val x = start + index.toFloat() / waveform.size.coerceAtLeast(1) * clipWidth
            drawLine(Color(0xff75d5c3), Offset(x, size.height / 2f - peak * 14f), Offset(x, size.height / 2f + peak * 14f), 1f)
        }
        val x = state.currentFrame * frameWidthPx
        drawLine(Color(0xffff6276), Offset(x, 0f), Offset(x, size.height), 1.5f)
    }
}
