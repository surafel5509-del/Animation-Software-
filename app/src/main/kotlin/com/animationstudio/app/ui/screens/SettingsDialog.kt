package com.animationstudio.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.EditorUiState
import com.animationstudio.app.ui.StudioViewModel
import com.animationstudio.coreui.LabeledSlider

@Composable
fun SettingsDialog(viewModel: StudioViewModel, state: EditorUiState) {
    AlertDialog({ viewModel.showSettings(false) }, confirmButton = { TextButton({ viewModel.showSettings(false) }) { Text("Done") } },
        title = { Text("Workspace settings") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingSwitch("Grid and snapping", state.preferences.gridEnabled) { viewModel.updatePreferences { p -> p.copy(gridEnabled = it) } }
                SettingSwitch("Transparent checkerboard", state.preferences.checkerboardEnabled) { viewModel.updatePreferences { p -> p.copy(checkerboardEnabled = it) } }
                SettingSwitch("Onion skin", state.preferences.onionSkinEnabled) { viewModel.updatePreferences { p -> p.copy(onionSkinEnabled = it) } }
                LabeledSlider("Previous frames", state.preferences.onionBefore.toFloat(), 0f..12f,
                    { value -> viewModel.updatePreferences { it.copy(onionBefore = value.toInt()) } }, "${state.preferences.onionBefore}")
                LabeledSlider("Next frames", state.preferences.onionAfter.toFloat(), 0f..12f,
                    { value -> viewModel.updatePreferences { it.copy(onionAfter = value.toInt()) } }, "${state.preferences.onionAfter}")
                LabeledSlider("Onion opacity", state.preferences.onionOpacity, 0.02f..1f,
                    { value -> viewModel.updatePreferences { it.copy(onionOpacity = value) } })
                LabeledSlider("Autosave interval", state.preferences.autosaveIntervalSeconds.toFloat(), 10f..300f,
                    { value -> viewModel.updatePreferences { it.copy(autosaveIntervalSeconds = value.toInt()) } }, "${state.preferences.autosaveIntervalSeconds} s")
                Text("Core editing, project recovery, and rendering work offline. Projects are never uploaded by the app.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        })
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}
