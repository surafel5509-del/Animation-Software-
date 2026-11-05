package com.animationstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.animationstudio.app.ui.StudioViewModel
import com.animationstudio.app.ui.screens.*
import com.animationstudio.app.ui.workspace.WorkspaceScreen
import com.animationstudio.coreui.AnimationStudioTheme
import com.animationstudio.rendering.ExportConfig
import com.animationstudio.rendering.ExportFormat

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            var pendingExport by remember { mutableStateOf<ExportConfig?>(null) }
            val openProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(viewModel::openExternal)
            }
            val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                uri?.let { viewModel.saveCurrent(it) }
            }
            val importAsset = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(viewModel::importAsset)
            }
            val exportTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                val config = pendingExport
                if (uri != null && config != null) viewModel.startExport(config, uri)
                pendingExport = null
            }
            val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                val config = pendingExport
                if (uri != null && config != null) viewModel.startExport(config, uri)
                pendingExport = null
            }
            val focus = remember { FocusRequester() }
            LaunchedEffect(state.project) { if (state.project != null) focus.requestFocus() }
            BackHandler(enabled = state.project != null) {
                when {
                    state.fullScreenCanvas -> viewModel.toggleFullScreen()
                    state.showExport -> viewModel.showExport(false)
                    else -> viewModel.goHome()
                }
            }
            AnimationStudioTheme {
                Scaffold(Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when {
                        event.isCtrlPressed && event.key == Key.Z && event.isShiftPressed -> { viewModel.redo(); true }
                        event.isCtrlPressed && event.key == Key.Z -> { viewModel.undo(); true }
                        event.isCtrlPressed && event.key == Key.S -> { viewModel.saveCurrent(); true }
                        event.key == Key.Spacebar && state.project != null -> { viewModel.togglePlayback(); true }
                        event.key == Key.Delete && state.selectedObjectId != null -> { viewModel.deleteSelectedObject(); true }
                        else -> false
                    }
                }) { padding ->
                    Surface(Modifier.fillMaxSize().padding(padding)) {
                        if (state.project == null) {
                            WelcomeScreen(viewModel, state) { openProject.launch(arrayOf("*/*")) }
                        } else {
                            WorkspaceScreen(viewModel, state,
                                onNew = { viewModel.showProjectSetup() },
                                onOpen = { openProject.launch(arrayOf("*/*")) },
                                onSaveAs = { saveAs.launch("${state.project?.name ?: "animation"}.animstudio") },
                                onImport = { importAsset.launch(arrayOf("image/*", "audio/*")) })
                        }
                    }
                }
                if (state.showProjectSetup) ProjectSetupDialog(viewModel)
                if (state.showSettings) SettingsDialog(viewModel, state)
                if (state.showExport && state.project != null) ExportDialog(state.project!!, { viewModel.showExport(false) }) { config ->
                    pendingExport = config
                    when (config.format) {
                        ExportFormat.PNG_SEQUENCE, ExportFormat.JPEG_SEQUENCE -> exportTree.launch(null)
                        else -> {
                            val extension = when (config.format) {
                                ExportFormat.MP4 -> "mp4"; ExportFormat.GIF -> "gif"; ExportFormat.JPEG -> "jpg"; else -> "png"
                            }
                            exportFile.launch("${state.project?.name ?: "animation"}.$extension")
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        viewModel.pausePlayback()
        super.onPause()
    }
}
