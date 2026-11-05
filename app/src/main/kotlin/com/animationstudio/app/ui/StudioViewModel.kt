package com.animationstudio.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.*
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.animationstudio.animation.AnimationEvaluator
import com.animationstudio.app.AnimationStudioApplication
import com.animationstudio.audio.WaveformDecoder
import com.animationstudio.core.model.*
import com.animationstudio.importer.AssetImporter
import com.animationstudio.layers.LayerEditor
import com.animationstudio.projectmanager.OpenedProject
import com.animationstudio.rendering.*
import com.animationstudio.settings.WorkspacePreferences
import com.animationstudio.timeline.FrameTiming
import com.animationstudio.timeline.TimelineEditor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AnimationStudioApplication).container
    private val repository = container.repository
    private val importer: AssetImporter = container.importer
    private val workManager = WorkManager.getInstance(application)
    internal val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()
    private val undoStack = ArrayDeque<StudioProject>()
    private val redoStack = ArrayDeque<StudioProject>()
    private var playbackJob: Job? = null
    private var renderObserver: Observer<WorkInfo?>? = null
    private var renderId: UUID? = null

    init {
        viewModelScope.launch {
            repository.recent.collect { recent -> mutableState.update { it.copy(recentProjects = recent) } }
        }
        viewModelScope.launch {
            container.settings.preferences.collect { preferences -> mutableState.update { it.copy(preferences = preferences) } }
        }
        viewModelScope.launch {
            while (isActive) {
                val seconds = state.value.preferences.autosaveIntervalSeconds
                delay(seconds * 1000L)
                if (state.value.isDirty && state.value.project != null && state.value.busyMessage == null) {
                    runCatching { repository.autosave(state.value.project!!) }
                        .onSuccess { saved -> markSavedIfCurrent(saved) }
                        .onFailure { error -> mutableState.update { it.copy(error = "Autosave failed: ${error.message}") } }
                }
            }
        }
    }

    internal inline fun editProject(recordHistory: Boolean = true, crossinline transform: (StudioProject) -> StudioProject) {
        val old = mutableState.value.project ?: return
        val updated = transform(old).copy(modifiedAt = System.currentTimeMillis())
        if (updated == old) return
        if (recordHistory) {
            undoStack.addLast(old)
            while (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
        }
        mutableState.update {
            it.copy(project = updated, isDirty = true, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
    }

    internal fun replaceWithoutHistory(project: StudioProject) {
        mutableState.update { it.copy(project = project, isDirty = true) }
    }

    fun undo() {
        val current = mutableState.value.project ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        mutableState.update { it.copy(project = previous, draft = null, selectedObjectId = null, selectedPointIndex = null,
            isDirty = true, canUndo = undoStack.isNotEmpty(), canRedo = true) }
    }

    fun redo() {
        val current = mutableState.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        mutableState.update { it.copy(project = next, draft = null, selectedObjectId = null, selectedPointIndex = null,
            isDirty = true, canUndo = true, canRedo = redoStack.isNotEmpty()) }
    }

    fun showProjectSetup(show: Boolean = true) = mutableState.update { it.copy(showProjectSetup = show) }
    fun showExport(show: Boolean = true) = mutableState.update { it.copy(showExport = show, render = if (show) it.render else RenderUiState()) }
    fun showSettings(show: Boolean = true) = mutableState.update { it.copy(showSettings = show) }
    fun dismissMessages() = mutableState.update { it.copy(error = null, notice = null) }
    fun selectInspector(tab: InspectorTab) = mutableState.update { it.copy(inspectorTab = tab, showInspector = true) }
    fun toggleInspector() = mutableState.update { it.copy(showInspector = !it.showInspector) }
    fun toggleTimeline() = mutableState.update { it.copy(showTimeline = !it.showTimeline) }
    fun toggleFullScreen() = mutableState.update { it.copy(fullScreenCanvas = !it.fullScreenCanvas) }

    fun createProject(name: String, width: Int, height: Int, fps: Int, duration: Int, color: Int, transparent: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(busyMessage = "Creating project…", showProjectSetup = false, error = null) }
            runCatching {
                val project = ProjectFactory.create(name, width, height, fps, duration, color, transparent)
                repository.create(project)
            }.onSuccess { project ->
                undoStack.clear(); redoStack.clear()
                mutableState.update { EditorUiState(project = project, recentProjects = it.recentProjects,
                    preferences = it.preferences, selectedLayerId = project.activeScene.layers.firstOrNull()?.id,
                    currentColor = it.currentColor, brush = it.brush, notice = "Project created") }
            }.onFailure(::showError)
        }
    }

    fun openExternal(uri: Uri) = viewModelScope.launch {
        mutableState.update { it.copy(busyMessage = "Opening editable project…") }
        runCatching { repository.openExternal(uri) }.onSuccess(::installOpenedProject).onFailure(::showError)
    }

    fun recoverRecent(id: String) = viewModelScope.launch {
        mutableState.update { it.copy(busyMessage = "Recovering autosave…") }
        runCatching { repository.recover(id) }.onSuccess(::installOpenedProject).onFailure(::showError)
    }

    private fun installOpenedProject(opened: OpenedProject) {
        undoStack.clear(); redoStack.clear()
        mutableState.update {
            it.copy(project = opened.project, currentLocation = opened.externalUri?.toString(),
                currentFrame = 0, selectedLayerId = opened.project.activeScene.layers.firstOrNull()?.id,
                selectedObjectId = null, draft = null, busyMessage = null, isDirty = false,
                canUndo = false, canRedo = false, notice = if (opened.recovered) "Autosave recovered" else "Project opened")
        }
    }

    fun saveCurrent(destination: Uri? = null) = viewModelScope.launch {
        val project = state.value.project ?: return@launch
        mutableState.update { it.copy(busyMessage = "Saving editable project…") }
        runCatching { repository.save(project, destination) }.onSuccess { saved ->
            mutableState.update { it.copy(project = saved.project, currentLocation = saved.externalUri?.toString(),
                busyMessage = null, isDirty = false, notice = "Project saved") }
        }.onFailure(::showError)
    }

    private fun markSavedIfCurrent(saved: StudioProject) {
        mutableState.update { current ->
            if (current.project != null && current.project.modifiedAt <= saved.modifiedAt)
                current.copy(project = saved, isDirty = false, busyMessage = null) else current.copy(busyMessage = null)
        }
    }

    fun deleteRecent(id: String) = viewModelScope.launch {
        runCatching { repository.delete(id) }.onSuccess {
            if (state.value.project?.id == id) goHome()
            mutableState.update { it.copy(notice = "Project deleted") }
        }.onFailure(::showError)
    }

    fun duplicateRecent(id: String) = viewModelScope.launch {
        mutableState.update { it.copy(busyMessage = "Duplicating project…") }
        runCatching {
            val source = repository.recover(id).project
            repository.duplicate(source, UUID.randomUUID().toString(), "${source.name} copy")
        }.onSuccess { mutableState.update { it.copy(busyMessage = null, notice = "Project duplicated") } }.onFailure(::showError)
    }

    fun renameProject(name: String) = editProject { it.copy(name = name.trim().ifBlank { it.name }) }

    fun goHome() {
        pausePlayback()
        container.audio.release()
        mutableState.update { EditorUiState(recentProjects = it.recentProjects, preferences = it.preferences,
            currentColor = it.currentColor, brush = it.brush) }
    }

    fun setTool(tool: ToolType) {
        if (state.value.pathBuilding && tool !in setOf(ToolType.PEN, ToolType.BEZIER)) finishPath()
        mutableState.update { it.copy(currentTool = tool, selectedPointIndex = null) }
    }

    fun setBrush(brush: BrushPreset) = mutableState.update { it.copy(brush = brush) }
    fun updateBrush(transform: (BrushPreset) -> BrushPreset) = mutableState.update { it.copy(brush = transform(it.brush)) }
    fun setColor(color: Int) = mutableState.update { it.copy(currentColor = color) }
    fun setGradient(colors: List<Int>) = mutableState.update { it.copy(gradientColors = colors.takeIf { it.size >= 2 } ?: it.gradientColors) }

    fun selectLayer(id: String) = mutableState.update { it.copy(selectedLayerId = id, selectedObjectId = null, selectedPointIndex = null) }
    fun selectObject(id: String?) = mutableState.update { it.copy(selectedObjectId = id, selectedPointIndex = null) }

    fun addLayer(type: LayerType = LayerType.VECTOR) {
        val id = UUID.randomUUID().toString()
        editProject { project -> project.updateActiveScene { scene ->
            LayerEditor.add(scene, Layer(id, "${type.name.lowercase().replaceFirstChar(Char::uppercase)} ${scene.layers.size + 1}", type = type,
                cels = if (type == LayerType.AUDIO) emptyList() else listOf(DrawingCel(0))))
        } }
        selectLayer(id)
    }

    fun updateLayer(id: String, transform: (Layer) -> Layer) = editProject { it.updateActiveScene { scene -> LayerEditor.update(scene, id, transform) } }

    fun deleteLayer(id: String) {
        editProject { it.updateActiveScene { scene -> LayerEditor.remove(scene, id) } }
        mutableState.update { it.copy(selectedLayerId = it.project?.activeScene?.layers?.firstOrNull()?.id, selectedObjectId = null) }
    }

    fun duplicateLayer(id: String) = editProject { it.updateActiveScene { scene -> LayerEditor.duplicate(scene, id) } }
    fun reorderLayer(id: String, delta: Int) = editProject { it.updateActiveScene { scene -> LayerEditor.reorder(scene, id, delta) } }

    fun groupSelectedLayer() {
        val selected = state.value.selectedLayerId ?: return
        val groupId = UUID.randomUUID().toString()
        editProject { it.updateActiveScene { scene ->
            val group = Layer(groupId, "Group ${scene.layers.count { layer -> layer.type == LayerType.GROUP } + 1}", LayerType.GROUP, cels = emptyList())
            LayerEditor.setParent(LayerEditor.add(scene, group), selected, groupId)
        } }
        selectLayer(groupId)
    }

    fun deleteSelectedObject() {
        val s = state.value
        val layer = s.selectedLayerId ?: return
        val obj = s.selectedObjectId ?: return
        editProject { it.updateActiveScene { scene -> LayerEditor.deleteObject(scene, layer, s.currentFrame, obj) } }
        selectObject(null)
    }

    fun updateSelectedObject(transform: (VectorObject) -> VectorObject) {
        val s = state.value
        val layer = s.selectedLayerId ?: return
        val obj = s.selectedObjectId ?: return
        editProject { it.updateActiveScene { scene -> LayerEditor.updateObject(scene, layer, s.currentFrame, obj, transform) } }
    }

    fun seek(frame: Int) {
        val project = state.value.project ?: return
        val clamped = frame.coerceIn(0, project.durationFrames - 1)
        mutableState.update { it.copy(currentFrame = clamped) }
        syncAudio(clamped, state.value.isPlaying)
    }

    fun togglePlayback() {
        if (state.value.isPlaying) pausePlayback() else {
            val project = state.value.project ?: return
            val work = project.activeScene.workArea
            if (state.value.currentFrame >= minOf(work.end, project.durationFrames) - 1) seek(work.start)
            mutableState.update { it.copy(isPlaying = true) }
            syncAudio(state.value.currentFrame, true)
            playbackJob = viewModelScope.launch {
                var lastTime = System.nanoTime()
                while (isActive && state.value.isPlaying) {
                    val currentProject = state.value.project ?: break
                    delay((1000L / currentProject.fps).coerceAtLeast(8L))
                    val now = System.nanoTime()
                    val elapsedFrames = ((now - lastTime) / 1_000_000L * currentProject.fps / 1000L).toInt().coerceAtLeast(1)
                    lastTime += elapsedFrames * 1_000_000_000L / currentProject.fps
                    repeat(elapsedFrames.coerceAtMost(12)) {
                        val s = state.value
                        val p = s.project ?: return@launch
                        val workArea = p.activeScene.workArea
                        val next = FrameTiming.advance(s.currentFrame, workArea, p.durationFrames)
                        if (next == s.currentFrame && !workArea.loop) {
                            pausePlayback(); return@launch
                        }
                        mutableState.update { it.copy(currentFrame = next) }
                    }
                    syncAudio(state.value.currentFrame, true)
                }
            }
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel(); playbackJob = null
        mutableState.update { it.copy(isPlaying = false) }
        state.value.project?.let { syncAudio(state.value.currentFrame, false) }
    }

    private fun syncAudio(frame: Int, playing: Boolean) {
        val project = state.value.project ?: return
        container.audio.update(project.activeScene, project.assets, { repository.assets.file(project.id, it.entryName) }, frame, project.fps, playing)
    }

    fun insertFrame() = editProject { project ->
        val updated = project.updateActiveScene { TimelineEditor.insertFrames(it, state.value.currentFrame) }
        updated.copy(durationFrames = updated.durationFrames + 1)
    }

    fun deleteFrame() = editProject { project ->
        if (project.durationFrames <= 1) project else {
            val updated = project.updateActiveScene { TimelineEditor.deleteFrames(it, state.value.currentFrame) }
            updated.copy(durationFrames = updated.durationFrames - 1)
        }
    }

    fun duplicateCel() {
        val layer = state.value.selectedLayerId ?: return
        editProject { it.updateActiveScene { scene -> LayerEditor.update(scene, layer) { TimelineEditor.duplicateCel(it, state.value.currentFrame) } } }
    }

    fun setExposure(exposure: Int) {
        val layer = state.value.selectedLayerId ?: return
        editProject { it.updateActiveScene { scene -> LayerEditor.update(scene, layer) { TimelineEditor.setExposure(it, state.value.currentFrame, exposure) } } }
    }

    fun setWorkArea(start: Int, end: Int, loop: Boolean = state.value.project?.activeScene?.workArea?.loop ?: true) {
        if (start < 0 || end <= start) return
        editProject { it.updateActiveScene { scene -> scene.copy(workArea = WorkArea(start, end, loop)) } }
    }

    fun addKeyframe(property: AnimatableProperty) {
        val s = state.value
        val project = s.project ?: return
        val target = s.selectedObjectId ?: s.selectedBoneId ?: project.activeScene.activeCameraId ?: return
        val scene = project.activeScene
        val base = selectedObject()?.transform
            ?: scene.cameras.firstOrNull { it.id == target }?.transform
            ?: Transform()
        val evaluated = AnimationEvaluator.transform(scene, target, base, s.currentFrame)
        val value = when (property) {
            AnimatableProperty.POSITION_X -> evaluated.position.x; AnimatableProperty.POSITION_Y -> evaluated.position.y
            AnimatableProperty.ROTATION -> evaluated.rotation; AnimatableProperty.SCALE_X -> evaluated.scale.x
            AnimatableProperty.SCALE_Y -> evaluated.scale.y; AnimatableProperty.OPACITY -> evaluated.opacity
            AnimatableProperty.CAMERA_ZOOM -> scene.cameras.firstOrNull { it.id == target }?.zoom ?: 1f
            AnimatableProperty.BONE_ROTATION -> scene.rigs.flatMap(CharacterRig::bones).firstOrNull { it.id == target }?.rotation ?: 0f
            AnimatableProperty.BONE_SCALE -> scene.rigs.flatMap(CharacterRig::bones).firstOrNull { it.id == target }?.scale ?: 1f
            else -> 0f
        }
        editProject { it.updateActiveScene { TimelineEditor.upsertKeyframe(it, target, property, Keyframe(s.currentFrame, value)) } }
    }

    fun addAllTransformKeyframes() {
        listOf(AnimatableProperty.POSITION_X, AnimatableProperty.POSITION_Y, AnimatableProperty.ROTATION,
            AnimatableProperty.SCALE_X, AnimatableProperty.SCALE_Y, AnimatableProperty.OPACITY).forEach(::addKeyframe)
    }

    fun removeKeyframe(property: AnimatableProperty) {
        val s = state.value; val target = s.selectedObjectId ?: return
        editProject { it.updateActiveScene { scene -> TimelineEditor.removeKeyframe(scene, target, property, s.currentFrame) } }
    }

    fun applyColorToSelection() {
        val color = state.value.currentColor
        updateSelectedObject { obj -> when (obj) {
            is VectorObject.PathObject -> if (obj.fill is FillStyle.Solid) obj.copy(fill = FillStyle.Solid(color))
                else obj.copy(stroke = (obj.stroke ?: StrokeStyle()).copy(color = color))
            is VectorObject.ShapeObject -> if (obj.fill != null) obj.copy(fill = FillStyle.Solid(color))
                else obj.copy(stroke = (obj.stroke ?: StrokeStyle()).copy(color = color))
            is VectorObject.TextObject -> obj.copy(color = color)
            is VectorObject.ImageObject -> obj
        } }
    }

    fun keySelectedColor() {
        val s = state.value
        val obj = selectedObject() ?: return
        val color = com.animationstudio.vector.VectorGeometry.colorOf(obj) ?: return
        editProject { it.updateActiveScene { scene -> TimelineEditor.upsertKeyframe(scene, obj.id, AnimatableProperty.COLOR,
            Keyframe(s.currentFrame, colorValue = color)) } }
    }

    fun setFps(fps: Int) = editProject { it.copy(fps = fps.coerceIn(1, 120)) }
    fun setDuration(frames: Int) = editProject { project ->
        val duration = frames.coerceAtLeast(1)
        project.copy(durationFrames = duration).updateActiveScene { scene ->
            scene.copy(workArea = scene.workArea.copy(end = duration.coerceAtLeast(scene.workArea.start + 1)))
        }
    }
    fun setCanvas(width: Int, height: Int) = editProject { it.copy(canvas = it.canvas.copy(width = width.coerceIn(1, 16384), height = height.coerceIn(1, 16384))) }
    fun setBackground(color: Int, transparent: Boolean = false) = editProject { it.copy(canvas = it.canvas.copy(backgroundColor = color, transparent = transparent)) }
    fun setTimelineZoom(value: Float) = mutableState.update { it.copy(timelineZoom = value.coerceIn(.4f, 5f)) }

    fun setViewport(zoom: Float, pan: Vec2, rotation: Float) = mutableState.update {
        it.copy(viewportZoom = zoom.coerceIn(.02f, 64f), viewportPan = pan, viewportRotation = rotation)
    }
    fun transformViewport(zoomFactor: Float, panDelta: Vec2, rotationDelta: Float) = mutableState.update {
        it.copy(viewportZoom = (it.viewportZoom * zoomFactor).coerceIn(.02f, 64f),
            viewportPan = it.viewportPan + panDelta, viewportRotation = it.viewportRotation + rotationDelta)
    }

    fun updatePreferences(transform: (WorkspacePreferences) -> WorkspacePreferences) = viewModelScope.launch { container.settings.update(transform) }

    fun selectedObject(): VectorObject? {
        val s = state.value
        return s.project?.activeScene?.layers?.firstOrNull { it.id == s.selectedLayerId }
            ?.celAt(s.currentFrame)?.objects?.firstOrNull { it.id == s.selectedObjectId }
    }

    fun showTextEntry(position: Vec2?) = mutableState.update { it.copy(textEntryPosition = position) }

    fun createText(text: String) {
        val position = state.value.textEntryPosition ?: return
        if (text.isNotBlank()) addObjectToCurrentCel(VectorObject.TextObject(UUID.randomUUID().toString(), text = text,
            position = position, color = state.value.currentColor))
        showTextEntry(null)
    }

    internal fun addObjectToCurrentCel(obj: VectorObject) {
        val s = state.value
        var layerId = s.selectedLayerId
        val project = s.project ?: return
        if (layerId == null || project.activeScene.layers.none { it.id == layerId && it.type != LayerType.GROUP && !it.locked }) {
            layerId = project.activeScene.layers.firstOrNull { it.type != LayerType.GROUP && !it.locked }?.id
        }
        if (layerId == null) {
            layerId = UUID.randomUUID().toString()
            val newLayerId = layerId
            editProject { it.updateActiveScene { scene -> LayerEditor.add(scene, Layer(newLayerId, "Drawing 1")) } }
        }
        val finalLayer = layerId
        editProject { it.updateActiveScene { scene -> LayerEditor.addObject(scene, finalLayer, s.currentFrame, obj) } }
        mutableState.update { it.copy(selectedLayerId = finalLayer, selectedObjectId = obj.id) }
    }

    fun addEffect(type: EffectType) {
        val layer = state.value.selectedLayerId ?: return
        val effect = EffectSettings(UUID.randomUUID().toString(), type,
            radius = if (type in setOf(EffectType.GLOW, EffectType.DROP_SHADOW, EffectType.GAUSSIAN_BLUR)) 12f else 0f,
            intensity = when (type) { EffectType.BRIGHTNESS -> 0f; EffectType.HUE -> 0f; else -> 1f },
            offset = if (type == EffectType.DROP_SHADOW) Vec2(12f, 16f) else Vec2.ZERO)
        updateLayer(layer) { it.copy(effects = it.effects + effect) }
    }

    fun updateEffect(layerId: String, effectId: String, transform: (EffectSettings) -> EffectSettings) = updateLayer(layerId) {
        it.copy(effects = it.effects.map { effect -> if (effect.id == effectId) transform(effect) else effect })
    }

    fun removeEffect(layerId: String, effectId: String) = updateLayer(layerId) { it.copy(effects = it.effects.filterNot { effect -> effect.id == effectId }) }

    fun importAsset(uri: Uri) = viewModelScope.launch {
        val project = state.value.project ?: return@launch
        mutableState.update { it.copy(busyMessage = "Importing asset…") }
        runCatching { importer.import(project.id, uri) }.onSuccess { asset ->
            when (asset.kind) {
                AssetKind.IMAGE -> {
                    editProject { it.copy(assets = it.assets + asset) }
                    val maxWidth = project.canvas.width * .6f
                    val maxHeight = project.canvas.height * .6f
                    val sourceWidth = asset.width?.toFloat() ?: maxWidth
                    val sourceHeight = asset.height?.toFloat() ?: maxHeight
                    val scale = minOf(maxWidth / sourceWidth, maxHeight / sourceHeight, 1f)
                    val width = sourceWidth * scale; val height = sourceHeight * scale
                    addObjectToCurrentCel(VectorObject.ImageObject(UUID.randomUUID().toString(), asset.displayName, asset.id,
                        RectF.around(Vec2(project.canvas.width / 2f, project.canvas.height / 2f), width, height)))
                }
                AssetKind.AUDIO -> {
                    val clip = AudioClip(UUID.randomUUID().toString(), asset.id, asset.displayName, trimEndMs = asset.durationMs)
                    editProject { it.copy(assets = it.assets + asset).updateActiveScene { scene -> scene.copy(audio = scene.audio + clip) } }
                    loadWaveform(asset)
                }
                else -> Unit
            }
            mutableState.update { it.copy(busyMessage = null, notice = "${asset.displayName} imported") }
        }.onFailure(::showError)
    }

    private fun loadWaveform(asset: AssetMetadata) {
        val projectId = state.value.project?.id ?: return
        viewModelScope.launch {
            runCatching { WaveformDecoder.decode(repository.assets.file(projectId, asset.entryName)) }.onSuccess { peaks ->
                mutableState.update { it.copy(waveforms = it.waveforms + (asset.id to peaks)) }
            }
        }
    }

    fun updateAudioClip(id: String, transform: (AudioClip) -> AudioClip) = editProject {
        it.updateActiveScene { scene -> scene.copy(audio = scene.audio.map { clip -> if (clip.id == id) transform(clip) else clip }) }
    }

    fun removeAudioClip(id: String) = editProject { it.updateActiveScene { scene -> scene.copy(audio = scene.audio.filterNot { clip -> clip.id == id }) } }

    fun addScene() {
        val id = UUID.randomUUID().toString(); val camera = UUID.randomUUID().toString(); val layer = UUID.randomUUID().toString()
        editProject { project ->
            val scene = Scene(id, "Scene ${project.scenes.size + 1}", layers = listOf(Layer(layer, "Drawing 1")),
                cameras = listOf(CameraSettings(camera)), activeCameraId = camera, workArea = WorkArea(0, project.durationFrames))
            project.copy(scenes = project.scenes + scene)
        }
    }

    fun selectScene(id: String) {
        editProject(recordHistory = false) { it.copy(activeSceneId = id) }
        mutableState.update { it.copy(currentFrame = 0, selectedLayerId = it.project?.activeScene?.layers?.firstOrNull()?.id, selectedObjectId = null) }
    }

    fun startExport(config: ExportConfig, destination: Uri) = viewModelScope.launch {
        val project = state.value.project ?: return@launch
        mutableState.update { it.copy(busyMessage = "Saving before render…", render = RenderUiState(running = true, phase = "Saving project")) }
        runCatching {
            try { getApplication<Application>().contentResolver.takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: SecurityException) {}
            val saved = repository.autosave(project)
            val request = RenderWorker.request(saved.id, saved.activeSceneId, destination, config)
            workManager.enqueue(request)
            saved to request.id
        }.onSuccess { (saved, id) ->
            renderId = id
            observeRender(id)
            mutableState.update { it.copy(project = saved, isDirty = false, busyMessage = null, showExport = false,
                notice = "Render queued", render = RenderUiState(running = true, phase = "Queued")) }
        }.onFailure(::showError)
    }

    private fun observeRender(id: UUID) {
        renderObserver?.let { observer -> renderId?.let { workManager.getWorkInfoByIdLiveData(it).removeObserver(observer) } }
        val observer = Observer<WorkInfo?> { info ->
            if (info == null) return@Observer
            val progress = info.progress
            mutableState.update { it.copy(render = RenderUiState(
                running = !info.state.isFinished,
                completed = progress.getInt(RenderWorker.KEY_COMPLETED, 0),
                total = progress.getInt(RenderWorker.KEY_TOTAL, 0),
                phase = progress.getString(RenderWorker.KEY_PHASE) ?: info.state.name,
                error = info.outputData.getString(RenderWorker.KEY_ERROR),
                success = if (info.state == WorkInfo.State.SUCCEEDED)
                    "Rendered ${info.outputData.getInt(RenderWorker.KEY_RENDERED, 0)} frame(s)" else null,
            )) }
            if (info.state.isFinished) mutableState.update { it.copy(notice = if (info.state == WorkInfo.State.SUCCEEDED) "Export complete" else null,
                error = if (info.state == WorkInfo.State.FAILED) info.outputData.getString(RenderWorker.KEY_ERROR) else it.error) }
        }
        renderObserver = observer
        workManager.getWorkInfoByIdLiveData(id).observeForever(observer)
    }

    fun cancelExport() {
        renderId?.let(workManager::cancelWorkById)
        mutableState.update { it.copy(render = it.render.copy(running = false, phase = "Cancelled")) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(busyMessage = null, render = it.render.copy(running = false),
            error = error.message ?: "Unexpected ${error.javaClass.simpleName}") }
    }

    override fun onCleared() {
        playbackJob?.cancel(); container.audio.release()
        renderObserver?.let { observer -> renderId?.let { workManager.getWorkInfoByIdLiveData(it).removeObserver(observer) } }
        super.onCleared()
    }
}
