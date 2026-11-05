package com.animationstudio.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.EditorUiState
import com.animationstudio.app.ui.StudioViewModel

@Composable
fun WelcomeScreen(viewModel: StudioViewModel, state: EditorUiState, onOpen: () -> Unit) {
    var tutorials by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 650.dp
        Row(Modifier.fillMaxSize().padding(if (compact) 20.dp else 48.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(if (compact) 1f else .85f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.MovieCreation, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("ANIMATION\nSTUDIO", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Professional 2D creation, editable by design.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                Button({ viewModel.showProjectSetup() }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New project")
                }
                OutlinedButton(onOpen, Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open .animstudio project")
                }
                TextButton({ tutorials = true }) { Icon(Icons.Default.School, null); Text("  Quick start & tutorials") }
                TextButton({ viewModel.showSettings() }) { Icon(Icons.Default.Settings, null); Text("  Workspace settings") }
                Text("Offline • Local projects • No account", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 16.dp))
            }
            if (!compact) VerticalDivider()
            Column(Modifier.weight(1.15f)) {
                Text("RECENT PROJECTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (state.recentProjects.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) { Text("Your recent editable projects and recoverable autosaves will appear here.", Modifier.padding(24.dp)) }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recentProjects, key = { it.id }) { recent ->
                        Card(Modifier.fillMaxWidth().clickable { viewModel.recoverRecent(recent.id) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(recent.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${recent.width} × ${recent.height} • ${recent.fps} fps • ${recent.durationFrames} frames",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton({ viewModel.duplicateRecent(recent.id) }) { Icon(Icons.Default.ContentCopy, "Duplicate project") }
                                IconButton({ viewModel.deleteRecent(recent.id) }) { Icon(Icons.Default.Delete, "Delete project") }
                            }
                        }
                    }
                }
            }
        }
        state.busyMessage?.let { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
    }
    if (tutorials) AlertDialog({ tutorials = false }, confirmButton = { TextButton({ tutorials = false }) { Text("Start creating") } },
        title = { Text("Quick start") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    "1. Create a project with your desired canvas, frame rate, and duration.",
                    "2. Choose Brush or Pencil and draw with touch or a stylus. Pressure is retained per point.",
                    "3. Add a blank cel for frame-by-frame work, or key an object's transforms for interpolated motion.",
                    "4. Use two fingers to pan, zoom, and rotate the workspace. Onion skin is configurable in Settings.",
                    "5. Save often. Autosaves remain fully editable and can be recovered from Recent Projects.",
                    "6. Export PNG/JPEG stills or sequences, animated GIF, or silent H.264 MP4 from Render.",
                ).forEach { Text(it, Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        })
}
