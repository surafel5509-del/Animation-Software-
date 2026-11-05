package com.animationstudio.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.StudioViewModel

@Composable
fun ProjectSetupDialog(viewModel: StudioViewModel) {
    var name by remember { mutableStateOf("Untitled animation") }
    var width by remember { mutableStateOf("1920") }
    var height by remember { mutableStateOf("1080") }
    var fps by remember { mutableStateOf("24") }
    var duration by remember { mutableStateOf("240") }
    var transparent by remember { mutableStateOf(false) }
    var background by remember { mutableStateOf(0xffffffff.toInt()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog({ viewModel.showProjectSetup(false) }, confirmButton = {
        Button({
            val w = width.toIntOrNull(); val h = height.toIntOrNull(); val rate = fps.toIntOrNull(); val frames = duration.toIntOrNull()
            if (name.isBlank() || w == null || h == null || rate == null || frames == null || w !in 1..16384 || h !in 1..16384 || rate !in 1..120 || frames !in 1..1_000_000)
                error = "Enter a name, dimensions 1–16384, FPS 1–120, and a positive duration."
            else viewModel.createProject(name, w, h, rate, frames, background, transparent)
        }) { Text("Create editable project") }
    }, dismissButton = { TextButton({ viewModel.showProjectSetup(false) }) { Text("Cancel") } },
        title = { Text("New animation project") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    listOf("HD" to (1920 to 1080), "4K" to (3840 to 2160), "Square" to (1080 to 1080), "Vertical" to (1080 to 1920)).forEach { (label, size) ->
                        FilterChip(selected = width == size.first.toString() && height == size.second.toString(),
                            onClick = { width = size.first.toString(); height = size.second.toString() },
                            label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                NumberInput("Canvas width", width) { width = it }
                NumberInput("Canvas height", height) { height = it }
                NumberInput("Frames per second", fps) { fps = it }
                NumberInput("Duration (frames)", duration) { duration = it }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Transparent background", Modifier.weight(1f)); Switch(transparent, { transparent = it })
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0xffffffff, 0xff111318, 0xff75d5c3, 0xff4f8cff).map(Long::toInt).forEach { color ->
                        Box(Modifier.size(44.dp).padding(4.dp).background(Color(color)).clickable { background = color; transparent = false })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        })
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
}
