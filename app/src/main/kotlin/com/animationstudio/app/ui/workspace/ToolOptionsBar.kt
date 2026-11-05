package com.animationstudio.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.*
import com.animationstudio.core.model.*

@Composable
fun ToolOptionsBar(viewModel: StudioViewModel, state: EditorUiState) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(state.currentTool.title(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            when (state.currentTool) {
                ToolType.BRUSH, ToolType.PENCIL, ToolType.PEN, ToolType.BEZIER -> {
                    Text("  Size", style = MaterialTheme.typography.labelSmall)
                    Slider(state.brush.size, { viewModel.updateBrush { brush -> brush.copy(size = it) } },
                        valueRange = 1f..128f, modifier = Modifier.width(130.dp))
                    Text("${state.brush.size.toInt()} px", style = MaterialTheme.typography.labelSmall)
                    if (state.pathBuilding) {
                        TextButton(viewModel::finishPath) { Icon(Icons.Default.Check, null); Text("Finish path") }
                        TextButton(viewModel::closePath) { Icon(Icons.Default.Link, null); Text("Close + fill") }
                    }
                }
                ToolType.SELECTION, ToolType.MOVE, ToolType.TRANSFORM, ToolType.ROTATE, ToolType.SCALE -> {
                    TextButton(viewModel::addAllTransformKeyframes, enabled = state.selectedObjectId != null) {
                        Icon(Icons.Default.Diamond, null); Text("Key transforms")
                    }
                    TextButton(viewModel::deleteSelectedObject, enabled = state.selectedObjectId != null) {
                        Icon(Icons.Default.Delete, null); Text("Delete")
                    }
                }
                ToolType.BONE -> Text("  Drag from a joint to create a child bone", style = MaterialTheme.typography.labelSmall)
                ToolType.CAMERA -> TextButton(viewModel::addAllTransformKeyframes) {
                    Icon(Icons.Default.Diamond, null); Text("Key camera transform")
                }
                ToolType.HAND, ToolType.ZOOM -> Text("  Pinch to zoom • drag to pan • twist to rotate", style = MaterialTheme.typography.labelSmall)
                ToolType.TEXT -> Text("  Tap the canvas to place editable text", style = MaterialTheme.typography.labelSmall)
                else -> Text("  Tap or drag on the canvas", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (state.busyMessage != null) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(" ${state.busyMessage}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
