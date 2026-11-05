package com.animationstudio.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animationstudio.core.model.StudioProject
import com.animationstudio.rendering.ExportConfig
import com.animationstudio.rendering.ExportFormat

@Composable
fun ExportDialog(project: StudioProject, onDismiss: () -> Unit, onExport: (ExportConfig) -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.MP4) }
    var width by remember { mutableStateOf(project.canvas.width.toString()) }
    var height by remember { mutableStateOf(project.canvas.height.toString()) }
    var fps by remember { mutableStateOf(project.fps.toString()) }
    var start by remember { mutableStateOf(project.activeScene.workArea.start.toString()) }
    var end by remember { mutableStateOf(minOf(project.activeScene.workArea.end, project.durationFrames).toString()) }
    var quality by remember { mutableStateOf(92f) }
    var bitrate by remember { mutableStateOf(8_000_000f) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismiss, confirmButton = {
        Button({
            try {
                val config = ExportConfig(format, width.toInt(), height.toInt(), fps.toInt(), start.toInt(), end.toInt(),
                    quality.toInt(), bitrate.toInt()).validated()
                if (config.endFrame > project.durationFrames) error = "End frame exceeds project duration."
                else onExport(config)
            } catch (e: Exception) { error = e.message ?: "Enter valid export values." }
        }) { Text(if (format == ExportFormat.PNG_SEQUENCE || format == ExportFormat.JPEG_SEQUENCE) "Choose output folder" else "Choose output file") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } }, title = { Text("Render animation") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ExportFormat.entries.forEach { item ->
                    FilterChip(format == item, { format = item }, label = { Text(item.name.replace('_', ' ')) }, modifier = Modifier.padding(end = 4.dp))
                }
            }
            Row { ExportNumber("Width", width, Modifier.weight(1f)) { width = it }; ExportNumber("Height", height, Modifier.weight(1f)) { height = it } }
            Row { ExportNumber("FPS", fps, Modifier.weight(1f)) { fps = it }; ExportNumber("Start", start, Modifier.weight(1f)) { start = it } }
            ExportNumber("End frame (exclusive)", end) { end = it }
            if (format == ExportFormat.MP4) {
                Text("Video bitrate: ${(bitrate / 1_000_000).toInt()} Mbps")
                Slider(bitrate, { bitrate = it }, valueRange = 1_000_000f..40_000_000f)
                Text("H.264 MP4 export is silent in this version. Codec availability varies by device.", style = MaterialTheme.typography.bodySmall)
            } else if (format != ExportFormat.PNG && format != ExportFormat.PNG_SEQUENCE) {
                Text("Quality: ${quality.toInt()}%"); Slider(quality, { quality = it }, valueRange = 1f..100f)
            }
            val frames = (end.toIntOrNull() ?: 0) - (start.toIntOrNull() ?: 0)
            Text("${frames.coerceAtLeast(0)} frame(s) • real rendering, no simulated output", style = MaterialTheme.typography.labelMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    })
}

@Composable
private fun ExportNumber(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.padding(3.dp))
}
