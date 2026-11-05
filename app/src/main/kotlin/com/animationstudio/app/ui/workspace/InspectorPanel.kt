package com.animationstudio.app.ui.workspace

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.animationstudio.app.ui.*
import com.animationstudio.core.model.*
import com.animationstudio.coreui.LabeledSlider
import com.animationstudio.coreui.PanelHeader
import com.animationstudio.rigging.SkeletonSolver
import com.animationstudio.vector.VectorGeometry
import kotlin.math.roundToInt

@Composable
fun InspectorPanel(viewModel: StudioViewModel, state: EditorUiState, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(310.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        PanelHeader(state.inspectorTab.name.lowercase().replaceFirstChar(Char::uppercase)) {
            IconButton(viewModel::toggleInspector, Modifier.size(28.dp)) { Icon(Icons.Default.ChevronRight, "Collapse inspector") }
        }
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(InspectorTab.entries) { tab ->
                FilterChip(selected = tab == state.inspectorTab, onClick = { viewModel.selectInspector(tab) },
                    label = { Text(tab.name.take(4), style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.inspectorTab) {
                InspectorTab.LAYERS -> LayersPanel(viewModel, state)
                InspectorTab.OBJECTS -> ObjectsPanel(viewModel, state)
                InspectorTab.PROPERTIES -> PropertiesPanel(viewModel, state)
                InspectorTab.COLOR -> ColorPanel(viewModel, state)
                InspectorTab.GRADIENTS -> GradientsPanel(viewModel, state)
                InspectorTab.BRUSHES -> BrushesPanel(viewModel, state)
                InspectorTab.RIGGING -> RiggingPanel(viewModel, state)
                InspectorTab.EFFECTS -> EffectsPanel(viewModel, state)
                InspectorTab.AUDIO -> AudioPanel(viewModel, state)
                InspectorTab.ASSETS -> AssetsPanel(viewModel, state, onImport)
                InspectorTab.SCENE -> ScenePanel(viewModel, state)
            }
        }
    }
}

@Composable
private fun LayersPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val scene = state.project?.activeScene ?: return
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ viewModel.addLayer() }) { Icon(Icons.Default.Add, "Add layer") }
            IconButton({ state.selectedLayerId?.let(viewModel::groupSelectedLayer) }) { Icon(Icons.Default.CreateNewFolder, "Group") }
            IconButton({ state.selectedLayerId?.let { viewModel.reorderLayer(it, 1) } }) { Icon(Icons.Default.ArrowUpward, "Move up") }
            IconButton({ state.selectedLayerId?.let { viewModel.reorderLayer(it, -1) } }) { Icon(Icons.Default.ArrowDownward, "Move down") }
            IconButton({ state.selectedLayerId?.let(viewModel::duplicateLayer) }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
            IconButton({ state.selectedLayerId?.let(viewModel::deleteLayer) }) { Icon(Icons.Default.Delete, "Delete") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(scene.layers.asReversed(), key = Layer::id) { layer ->
                val selected = layer.id == state.selectedLayerId
                Column(Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                    Row(Modifier.fillMaxWidth().clickable { viewModel.selectLayer(layer.id) }.padding(start = if (layer.parentId == null) 2.dp else 22.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ viewModel.updateLayer(layer.id) { it.copy(visible = !it.visible) } }, Modifier.size(34.dp)) {
                            Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibility", Modifier.size(18.dp))
                        }
                        Icon(layerIcon(layer.type), null, Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(layer.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text("${layer.type.name} • ${layer.cels.size} cels", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton({ viewModel.updateLayer(layer.id) { it.copy(locked = !it.locked) } }, Modifier.size(34.dp)) {
                            Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock", Modifier.size(17.dp))
                        }
                    }
                    if (selected) LabeledSlider("Opacity", layer.opacity, 0f..1f,
                        { value -> viewModel.updateLayer(layer.id) { it.copy(opacity = value) } }, "${(layer.opacity * 100).roundToInt()}%")
                }
            }
        }
    }
}

private fun layerIcon(type: LayerType) = when (type) {
    LayerType.VECTOR -> Icons.Default.Gesture; LayerType.RASTER -> Icons.Default.Brush; LayerType.GROUP -> Icons.Default.Folder
    LayerType.SKELETON, LayerType.BONE -> Icons.Default.AccountTree; LayerType.IMAGE -> Icons.Default.Image; LayerType.TEXT -> Icons.Default.TextFields
    LayerType.AUDIO -> Icons.Default.AudioFile; LayerType.CAMERA -> Icons.Default.CameraAlt; LayerType.MASK -> Icons.Default.Layers
    LayerType.EFFECT -> Icons.Default.AutoAwesome
}

@Composable
private fun ObjectsPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val layer = state.project?.activeScene?.layers?.firstOrNull { it.id == state.selectedLayerId }
    val objects = layer?.celAt(state.currentFrame)?.objects.orEmpty()
    LazyColumn {
        items(objects.asReversed(), key = VectorObject::id) { obj ->
            ListItem(headlineContent = { Text(obj.name) }, supportingContent = { Text(obj.javaClass.simpleName.removeSuffix("Object")) },
                leadingContent = { Icon(Icons.Default.Category, null) },
                modifier = Modifier.clickable { viewModel.selectObject(obj.id) },
                colors = ListItemDefaults.colors(containerColor = if (obj.id == state.selectedObjectId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
        }
    }
}

@Composable
private fun PropertiesPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val obj = viewModel.selectedObject()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (obj == null) {
            Text("Select an object to edit its transform and animation.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(obj.name, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
        NumberField("Position X", obj.transform.position.x) { value -> updateTransform(viewModel, obj) { it.copy(position = it.position.copy(x = value)) } }
        NumberField("Position Y", obj.transform.position.y) { value -> updateTransform(viewModel, obj) { it.copy(position = it.position.copy(y = value)) } }
        NumberField("Rotation °", Math.toDegrees(obj.transform.rotation.toDouble()).toFloat()) { value ->
            updateTransform(viewModel, obj) { it.copy(rotation = Math.toRadians(value.toDouble()).toFloat()) }
        }
        NumberField("Scale X", obj.transform.scale.x) { value -> updateTransform(viewModel, obj) { it.copy(scale = it.scale.copy(x = value.coerceAtLeast(.001f))) } }
        NumberField("Scale Y", obj.transform.scale.y) { value -> updateTransform(viewModel, obj) { it.copy(scale = it.scale.copy(y = value.coerceAtLeast(.001f))) } }
        LabeledSlider("Object opacity", obj.transform.opacity, 0f..1f, { value -> updateTransform(viewModel, obj) { it.copy(opacity = value) } })
        TextButton(viewModel::addAllTransformKeyframes, Modifier.padding(8.dp)) { Icon(Icons.Default.Diamond, null); Text("Key all at frame ${state.currentFrame}") }
        if (obj is VectorObject.ShapeObject) {
            NumberField("Vertices / star points", obj.vertices.toFloat()) { value ->
                viewModel.updateSelectedObject { current -> (current as VectorObject.ShapeObject).copy(vertices = value.roundToInt().coerceIn(3, 100)) }
            }
            NumberField("Corner radius", obj.cornerRadius) { value ->
                viewModel.updateSelectedObject { current -> (current as VectorObject.ShapeObject).copy(cornerRadius = value.coerceAtLeast(0f)) }
            }
        }
        if (obj is VectorObject.TextObject) {
            var text by remember(obj.id, obj.text) { mutableStateOf(obj.text) }
            OutlinedTextField(text, { text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
            TextButton({ viewModel.updateSelectedObject { current -> (current as VectorObject.TextObject).copy(text = text) } }) { Text("Apply text") }
        }
        Text("Keyframe channels", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
        listOf(AnimatableProperty.POSITION_X, AnimatableProperty.POSITION_Y, AnimatableProperty.ROTATION,
            AnimatableProperty.SCALE_X, AnimatableProperty.SCALE_Y, AnimatableProperty.OPACITY).forEach { property ->
            val key = state.project?.activeScene?.tracks?.firstOrNull { it.targetId == obj.id && it.property == property }
                ?.keyframes?.any { it.frame == state.currentFrame } == true
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(property.name.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(if (key) { { viewModel.removeKeyframe(property) } } else { { viewModel.addKeyframe(property) } }) {
                    Icon(Icons.Default.Diamond, if (key) "Remove keyframe" else "Add keyframe",
                        tint = if (key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun updateTransform(viewModel: StudioViewModel, obj: VectorObject, transform: (Transform) -> Transform) =
    viewModel.updateSelectedObject { VectorGeometry.withTransform(it, transform(obj.transform)) }

@Composable
private fun ColorPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val hsv = remember(state.currentColor) { FloatArray(3).apply { android.graphics.Color.colorToHSV(state.currentColor, this) } }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(72.dp).padding(12.dp).background(Color(state.currentColor)))
        LabeledSlider("Hue", hsv[0], 0f..360f, { viewModel.setColor(android.graphics.Color.HSVToColor(state.currentColor ushr 24, floatArrayOf(it, hsv[1], hsv[2]))) }, "${hsv[0].roundToInt()}°")
        LabeledSlider("Saturation", hsv[1], 0f..1f, { viewModel.setColor(android.graphics.Color.HSVToColor(state.currentColor ushr 24, floatArrayOf(hsv[0], it, hsv[2]))) })
        LabeledSlider("Value", hsv[2], 0f..1f, { viewModel.setColor(android.graphics.Color.HSVToColor(state.currentColor ushr 24, floatArrayOf(hsv[0], hsv[1], it))) })
        val palette = listOf(0xff111318, 0xffffffff, 0xffff5c6c, 0xffffb86b, 0xffffe66b, 0xff75d5c3, 0xff4f8cff, 0xffa778ff).map(Long::toInt)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
            palette.forEach { color -> Box(Modifier.size(42.dp).padding(3.dp).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline).clickable { viewModel.setColor(color) }) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(viewModel::applyColorToSelection, enabled = state.selectedObjectId != null) { Text("Apply to selection") }
            TextButton(viewModel::keySelectedColor, enabled = state.selectedObjectId != null) {
                Icon(Icons.Default.Diamond, null); Text("Key color")
            }
        }
        Text("Current color is used by drawing, shapes, text, fill, and gradient tools.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GradientsPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val gradients = listOf(
        listOf(0xff4f8cff, 0xff75d5c3), listOf(0xffff5c6c, 0xffffb86b),
        listOf(0xff111318, 0xffa778ff), listOf(0xffffe66b, 0xffff5c6c, 0xffa778ff),
    ).map { colors -> colors.map(Long::toInt) }
    Column {
        gradients.forEach { colors ->
            Row(Modifier.fillMaxWidth().padding(8.dp).height(54.dp).background(Brush.linearGradient(colors.map(::Color))).clickable { viewModel.setGradient(colors) }) {}
        }
        Text("Choose a gradient, then use the Gradient tool on an object.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BrushesPanel(viewModel: StudioViewModel, state: EditorUiState) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        BrushPreset.defaults.forEach { preset ->
            ListItem(headlineContent = { Text(preset.name) }, supportingContent = { Text("${preset.kind} • ${preset.size.toInt()} px") },
                modifier = Modifier.clickable { viewModel.setBrush(preset) },
                trailingContent = { if (preset.id == state.brush.id) Icon(Icons.Default.Check, "Selected") })
        }
        LabeledSlider("Size", state.brush.size, 1f..128f, { value -> viewModel.updateBrush { it.copy(size = value) } }, "${state.brush.size.roundToInt()} px")
        LabeledSlider("Opacity", state.brush.opacity, .01f..1f, { value -> viewModel.updateBrush { it.copy(opacity = value) } })
        LabeledSlider("Flow", state.brush.flow, .01f..1f, { value -> viewModel.updateBrush { it.copy(flow = value) } })
        LabeledSlider("Smoothing", state.brush.smoothing, 0f..0.95f, { value -> viewModel.updateBrush { it.copy(smoothing = value) } })
        LabeledSlider("Spacing", state.brush.spacing, .03f..1f, { value -> viewModel.updateBrush { it.copy(spacing = value) } })
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pressure sensitivity", Modifier.weight(1f)); Switch(state.brush.pressureSensitive, { checked -> viewModel.updateBrush { it.copy(pressureSensitive = checked) } })
        }
    }
}

@Composable
private fun RiggingPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val rig = state.project?.activeScene?.rigs?.firstOrNull()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Use the Bone tool and drag on the canvas. New bones inherit the selected parent.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        rig?.bones.orEmpty().forEach { bone ->
            ListItem(headlineContent = { Text(bone.name) }, supportingContent = { Text("Parent: ${bone.parentId ?: "root"}") },
                modifier = Modifier.clickable { viewModel.selectBone(bone.id) },
                trailingContent = { IconButton({ viewModel.deleteBone(bone.id) }) { Icon(Icons.Default.Delete, "Delete bone") } },
                colors = ListItemDefaults.colors(containerColor = if (bone.id == state.selectedBoneId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
        }
        val selected = rig?.bones?.firstOrNull { it.id == state.selectedBoneId }
        if (selected != null && rig != null) {
            LabeledSlider("Rotation", Math.toDegrees(selected.rotation.toDouble()).toFloat(), -180f..180f,
                { value -> viewModel.updateBone(selected.id) { it.copy(rotation = Math.toRadians(value.toDouble()).toFloat()) } }, "${Math.toDegrees(selected.rotation.toDouble()).roundToInt()}°")
            NumberField("Length", selected.length) { value -> viewModel.updateBone(selected.id) { it.copy(length = value.coerceAtLeast(1f)) } }
            TextButton({ viewModel.addKeyframe(AnimatableProperty.BONE_ROTATION) }) { Icon(Icons.Default.Diamond, null); Text("Key bone rotation") }
            val end = SkeletonSolver.forwardKinematics(rig)[selected.id]?.end
            if (end != null) TextButton({ viewModel.solveIk(selected.id, end + Vec2(80f, 0f)) }) {
                Icon(Icons.Default.Hub, null); Text("Solve IK toward +X")
            }
            Text("Parent", Modifier.padding(horizontal = 12.dp))
            rig.bones.filter { it.id != selected.id }.forEach { candidate ->
                TextButton({ viewModel.updateBone(selected.id) { it.copy(parentId = candidate.id) } }) { Text("Parent to ${candidate.name}") }
            }
            TextButton({ viewModel.updateBone(selected.id) { it.copy(parentId = null) } }) { Text("Make root") }
        }
    }
}

@Composable
private fun EffectsPanel(viewModel: StudioViewModel, state: EditorUiState) {
    val layer = state.project?.activeScene?.layers?.firstOrNull { it.id == state.selectedLayerId }
    var choosing by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box {
            TextButton({ choosing = true }, enabled = layer != null) { Icon(Icons.Default.Add, null); Text("Add effect") }
            DropdownMenu(choosing, { choosing = false }) {
                EffectType.entries.filter { it != EffectType.MASK }.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name.replace('_', ' ')) }, onClick = { viewModel.addEffect(type); choosing = false })
                }
            }
        }
        layer?.effects.orEmpty().forEach { effect ->
            Card(Modifier.fillMaxWidth().padding(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(effect.enabled, { checked -> viewModel.updateEffect(layer!!.id, effect.id) { it.copy(enabled = checked) } })
                    Text(effect.type.name.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton({ viewModel.removeEffect(layer!!.id, effect.id) }) { Icon(Icons.Default.Delete, "Remove effect") }
                }
                if (effect.type in setOf(EffectType.GAUSSIAN_BLUR, EffectType.GLOW, EffectType.DROP_SHADOW))
                    LabeledSlider("Radius", effect.radius, 0f..64f, { value -> viewModel.updateEffect(layer!!.id, effect.id) { it.copy(radius = value) } })
                val range = when (effect.type) { EffectType.BRIGHTNESS -> -255f..255f; EffectType.HUE -> -180f..180f; EffectType.MOTION_BLUR -> 2f..16f; else -> 0f..3f }
                LabeledSlider("Intensity", effect.intensity, range, { value -> viewModel.updateEffect(layer!!.id, effect.id) { it.copy(intensity = value) } })
                if (effect.type == EffectType.DROP_SHADOW || effect.type == EffectType.MOTION_BLUR) {
                    NumberField("Offset X", effect.offset.x) { value -> viewModel.updateEffect(layer!!.id, effect.id) { it.copy(offset = it.offset.copy(x = value)) } }
                    NumberField("Offset Y", effect.offset.y) { value -> viewModel.updateEffect(layer!!.id, effect.id) { it.copy(offset = it.offset.copy(y = value)) } }
                }
            }
        }
        Text("Effects are composited by the software export renderer. Preview shows source geometry for responsive editing.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AudioPanel(viewModel: StudioViewModel, state: EditorUiState) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        state.project?.activeScene?.audio.orEmpty().forEach { clip ->
            Card(Modifier.padding(8.dp)) {
                Text(clip.name, Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
                LabeledSlider("Volume", clip.volume, 0f..1f, { value -> viewModel.updateAudioClip(clip.id) { it.copy(volume = value) } })
                NumberField("Start frame", clip.startFrame.toFloat()) { value -> viewModel.updateAudioClip(clip.id) { it.copy(startFrame = value.roundToInt().coerceAtLeast(0)) } }
                NumberField("Trim start (ms)", clip.trimStartMs.toFloat()) { value -> viewModel.updateAudioClip(clip.id) { it.copy(trimStartMs = value.toLong().coerceAtLeast(0)) } }
                NumberField("Fade in (ms)", clip.fadeInMs.toFloat()) { value -> viewModel.updateAudioClip(clip.id) { it.copy(fadeInMs = value.toLong().coerceAtLeast(0)) } }
                NumberField("Fade out (ms)", clip.fadeOutMs.toFloat()) { value -> viewModel.updateAudioClip(clip.id) { it.copy(fadeOutMs = value.toLong().coerceAtLeast(0)) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mute", Modifier.padding(12.dp).weight(1f)); Switch(clip.muted, { checked -> viewModel.updateAudioClip(clip.id) { it.copy(muted = checked) } })
                    IconButton({ viewModel.removeAudioClip(clip.id) }) { Icon(Icons.Default.Delete, "Remove audio") }
                }
            }
        }
        Text("Audio clips synchronize to preview playback. Export MP4 is currently silent; final audio mixing is a documented remaining task.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AssetsPanel(viewModel: StudioViewModel, state: EditorUiState, onImport: () -> Unit) {
    Column {
        TextButton(onImport, Modifier.padding(8.dp)) { Icon(Icons.Default.UploadFile, null); Text("Import PNG / JPEG / WebP / WAV / MP3") }
        LazyColumn {
            items(state.project?.assets.orEmpty(), key = AssetMetadata::id) { asset ->
                ListItem(headlineContent = { Text(asset.displayName) },
                    supportingContent = { Text("${asset.kind} • ${asset.mimeType}${asset.width?.let { w -> " • ${w}×${asset.height}" } ?: ""}") },
                    leadingContent = { Icon(if (asset.kind == AssetKind.AUDIO) Icons.Default.AudioFile else Icons.Default.Image, null) })
            }
        }
    }
}

@Composable
private fun ScenePanel(viewModel: StudioViewModel, state: EditorUiState) {
    val project = state.project ?: return
    Column(Modifier.verticalScroll(rememberScrollState())) {
        var name by remember(project.id, project.name) { mutableStateOf(project.name) }
        OutlinedTextField(name, { name = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth().padding(8.dp))
        TextButton({ viewModel.renameProject(name) }) { Text("Apply name") }
        NumberField("Canvas width", project.canvas.width.toFloat()) { viewModel.setCanvas(it.roundToInt(), project.canvas.height) }
        NumberField("Canvas height", project.canvas.height.toFloat()) { viewModel.setCanvas(project.canvas.width, it.roundToInt()) }
        NumberField("Frames per second", project.fps.toFloat()) { viewModel.setFps(it.roundToInt()) }
        NumberField("Duration (frames)", project.durationFrames.toFloat()) { viewModel.setDuration(it.roundToInt()) }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Transparent background", Modifier.weight(1f)); Switch(project.canvas.transparent, { viewModel.setBackground(project.canvas.backgroundColor, it) })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text("Scenes", Modifier.weight(1f)); IconButton(viewModel::addScene) { Icon(Icons.Default.Add, "Add scene") }
        }
        project.scenes.forEach { scene ->
            TextButton({ viewModel.selectScene(scene.id) }, colors = ButtonDefaults.textButtonColors(
                containerColor = if (scene.id == project.activeSceneId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                Text(scene.name)
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Float, onValue: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else "%.3f".format(value)) }
    OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        keyboardActions = KeyboardActions(onDone = { text.toFloatOrNull()?.let(onValue) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp))
}
