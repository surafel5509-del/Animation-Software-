package com.animationstudio.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.*
import com.animationstudio.core.model.LayerType
import com.animationstudio.core.model.ToolType

@Composable
fun TopToolbar(
    viewModel: StudioViewModel,
    state: EditorUiState,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(54.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.MovieCreation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp))
        Text(state.project?.name ?: "Animation Studio", style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            StudioMenu("File") {
                MenuItem("New project", Icons.Default.Add) { onNew() }
                MenuItem("Open project", Icons.Default.FolderOpen) { onOpen() }
                MenuItem("Save", Icons.Default.Save) { viewModel.saveCurrent() }
                MenuItem("Save as", Icons.Default.SaveAs) { onSaveAs() }
                MenuItem("Import image/audio", Icons.Default.UploadFile) { onImport() }
                MenuItem("Export / render", Icons.Default.Movie) { viewModel.showExport() }
            }
            StudioMenu("Edit") {
                MenuItem("Undo", Icons.Default.Undo, state.canUndo) { viewModel.undo() }
                MenuItem("Redo", Icons.Default.Redo, state.canRedo) { viewModel.redo() }
                MenuItem("Delete selected object", Icons.Default.Delete, state.selectedObjectId != null) { viewModel.deleteSelectedObject() }
            }
            StudioMenu("View") {
                MenuItem(if (state.preferences.gridEnabled) "Hide grid" else "Show grid", Icons.Default.GridOn) {
                    viewModel.updatePreferences { it.copy(gridEnabled = !it.gridEnabled) }
                }
                MenuItem(if (state.preferences.onionSkinEnabled) "Disable onion skin" else "Enable onion skin", Icons.Default.Layers) {
                    viewModel.updatePreferences { it.copy(onionSkinEnabled = !it.onionSkinEnabled) }
                }
                MenuItem(if (state.fullScreenCanvas) "Exit full-screen canvas" else "Full-screen canvas", Icons.Default.Fullscreen) { viewModel.toggleFullScreen() }
                MenuItem("Toggle inspector", Icons.Default.ViewSidebar) { viewModel.toggleInspector() }
                MenuItem("Toggle timeline", Icons.Default.ViewStream) { viewModel.toggleTimeline() }
            }
            StudioMenu("Animation") {
                MenuItem(if (state.isPlaying) "Pause" else "Play", Icons.Default.PlayArrow) { viewModel.togglePlayback() }
                MenuItem("Key all transforms", Icons.Default.Diamond, state.selectedObjectId != null) { viewModel.addAllTransformKeyframes() }
                MenuItem("Insert frame", Icons.Default.Add) { viewModel.insertFrame() }
                MenuItem("Delete frame", Icons.Default.Remove) { viewModel.deleteFrame() }
                MenuItem("New blank cel", Icons.Default.NoteAdd) { viewModel.createBlankCel() }
            }
            StudioMenu("Layer") {
                MenuItem("Add vector layer", Icons.Default.Add) { viewModel.addLayer(LayerType.VECTOR) }
                MenuItem("Add raster layer", Icons.Default.AddPhotoAlternate) { viewModel.addLayer(LayerType.RASTER) }
                MenuItem("Group selected", Icons.Default.CreateNewFolder, state.selectedLayerId != null) { viewModel.groupSelectedLayer() }
                MenuItem("Duplicate selected", Icons.Default.ContentCopy, state.selectedLayerId != null) { viewModel.duplicateLayer(state.selectedLayerId!!) }
            }
            StudioMenu("Character") {
                MenuItem("Bone tool", Icons.Default.AccountTree) { viewModel.setTool(ToolType.BONE) }
                MenuItem("Rigging panel", Icons.Default.AccessibilityNew) { viewModel.selectInspector(InspectorTab.RIGGING) }
            }
            StudioMenu("Effects") {
                MenuItem("Effects panel", Icons.Default.AutoAwesome) { viewModel.selectInspector(InspectorTab.EFFECTS) }
            }
            StudioMenu("Audio") {
                MenuItem("Import audio", Icons.Default.AudioFile) { onImport() }
                MenuItem("Audio panel", Icons.Default.GraphicEq) { viewModel.selectInspector(InspectorTab.AUDIO) }
            }
            StudioMenu("Render") {
                MenuItem("Configure export", Icons.Default.Movie) { viewModel.showExport() }
                if (state.render.running) MenuItem("Cancel active render", Icons.Default.Cancel) { viewModel.cancelExport() }
            }
            StudioMenu("Settings") {
                MenuItem("Workspace settings", Icons.Default.Settings) { viewModel.showSettings() }
                MenuItem("Project home", Icons.Default.Home) { viewModel.goHome() }
            }
        }
        IconButton(viewModel::undo, enabled = state.canUndo) { Icon(Icons.Default.Undo, "Undo") }
        IconButton(viewModel::redo, enabled = state.canRedo) { Icon(Icons.Default.Redo, "Redo") }
        IconButton({ viewModel.saveCurrent() }) { Icon(Icons.Default.Save, "Save") }
    }
}

@Composable
private fun StudioMenu(title: String, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { content { expanded = false } }
    }
}

@Composable
private fun MenuItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(title) }, leadingIcon = { Icon(icon, null) }, enabled = enabled, onClick = onClick)
}
