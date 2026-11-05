package com.animationstudio.app.ui

import com.animationstudio.core.model.*
import com.animationstudio.projectmanager.ProjectMetadataEntity
import com.animationstudio.settings.WorkspacePreferences

enum class InspectorTab { LAYERS, OBJECTS, PROPERTIES, COLOR, GRADIENTS, BRUSHES, RIGGING, EFFECTS, AUDIO, ASSETS, SCENE }

data class RenderUiState(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val phase: String = "",
    val error: String? = null,
    val success: String? = null,
)

data class EditorUiState(
    val project: StudioProject? = null,
    val recentProjects: List<ProjectMetadataEntity> = emptyList(),
    val currentLocation: String? = null,
    val currentFrame: Int = 0,
    val selectedLayerId: String? = null,
    val selectedObjectId: String? = null,
    val selectedBoneId: String? = null,
    val selectedPointIndex: Int? = null,
    val currentTool: ToolType = ToolType.BRUSH,
    val brush: BrushPreset = BrushPreset.defaults.first { it.kind == BrushKind.INK },
    val currentColor: Int = 0xff4f8cff.toInt(),
    val gradientColors: List<Int> = listOf(0xff4f8cff.toInt(), 0xff75d5c3.toInt()),
    val draft: VectorObject? = null,
    val pathBuilding: Boolean = false,
    val isPlaying: Boolean = false,
    val isDirty: Boolean = false,
    val busyMessage: String? = null,
    val notice: String? = null,
    val error: String? = null,
    val showProjectSetup: Boolean = false,
    val showExport: Boolean = false,
    val showSettings: Boolean = false,
    val showInspector: Boolean = true,
    val showTimeline: Boolean = true,
    val fullScreenCanvas: Boolean = false,
    val inspectorTab: InspectorTab = InspectorTab.LAYERS,
    val textEntryPosition: Vec2? = null,
    val viewportZoom: Float = 0f,
    val viewportPan: Vec2 = Vec2.ZERO,
    val viewportRotation: Float = 0f,
    val timelineZoom: Float = 1f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val preferences: WorkspacePreferences = WorkspacePreferences(),
    val render: RenderUiState = RenderUiState(),
    val waveforms: Map<String, List<Float>> = emptyMap(),
)
