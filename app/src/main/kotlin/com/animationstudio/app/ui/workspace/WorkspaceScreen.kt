package com.animationstudio.app.ui.workspace

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.*

@Composable
fun WorkspaceScreen(viewModel: StudioViewModel, state: EditorUiState, onNew: () -> Unit, onOpen: () -> Unit, onSaveAs: () -> Unit, onImport: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        var compactInspector by remember { mutableStateOf(false) }
        var timelineHeight by remember { mutableStateOf(230.dp) }
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize()) {
            if (!state.fullScreenCanvas) {
                TopToolbar(viewModel, state, onNew, onOpen, onSaveAs, onImport)
                ToolOptionsBar(viewModel, state)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!state.fullScreenCanvas) LeftToolbox(state.currentTool, viewModel::setTool)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    StudioCanvas(viewModel, state)
                    Row(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                        if (state.render.running) {
                            Card(Modifier.padding(end = 8.dp)) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(progress = { if (state.render.total > 0) state.render.completed.toFloat() / state.render.total else 0f },
                                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Text(" ${state.render.phase} ${state.render.completed}/${state.render.total}", style = MaterialTheme.typography.labelSmall)
                                    IconButton(viewModel::cancelExport, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Cancel render") }
                                }
                            }
                        }
                        if (compact && !state.fullScreenCanvas) SmallFloatingActionButton({ compactInspector = true }) { Icon(Icons.Default.Tune, "Open inspector") }
                        SmallFloatingActionButton(viewModel::toggleFullScreen, Modifier.padding(start = 6.dp)) {
                            Icon(if (state.fullScreenCanvas) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle full screen")
                        }
                    }
                }
                if (!compact && state.showInspector && !state.fullScreenCanvas) InspectorPanel(viewModel, state, onImport)
            }
            if (state.showTimeline && !state.fullScreenCanvas) {
                Box(Modifier.fillMaxWidth().height(6.dp).pointerInput(maxHeight) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = with(density) { (-dragAmount).toDp() }
                        timelineHeight = (timelineHeight + delta).coerceIn(140.dp, maxHeight * .55f)
                    }
                })
                TimelinePanel(viewModel, state, Modifier.height(if (compact) minOf(timelineHeight, 210.dp) else timelineHeight))
            }
        }
        if (compact && compactInspector && !state.fullScreenCanvas) {
            AlertDialog({ compactInspector = false }, confirmButton = { TextButton({ compactInspector = false }) { Text("Close") } },
                text = { Box(Modifier.height((maxHeight * .75f)).fillMaxWidth()) { InspectorPanel(viewModel, state, onImport) } })
        }
        state.notice?.let { notice ->
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp), dismissAction = { IconButton(viewModel::dismissMessages) { Icon(Icons.Default.Close, "Dismiss") } }) { Text(notice) }
        }
        state.error?.let { error ->
            AlertDialog(viewModel::dismissMessages, confirmButton = { TextButton(viewModel::dismissMessages) { Text("OK") } },
                title = { Text("Animation Studio") }, text = { Text(error) })
        }
        state.textEntryPosition?.let {
            var text by remember { mutableStateOf("") }
            AlertDialog({ viewModel.showTextEntry(null) }, confirmButton = { TextButton({ viewModel.createText(text) }) { Text("Place text") } },
                dismissButton = { TextButton({ viewModel.showTextEntry(null) }) { Text("Cancel") } }, title = { Text("Editable text") },
                text = { OutlinedTextField(text, { text = it }, label = { Text("Text") }) })
        }
    }
}
