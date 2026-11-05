package com.animationstudio.rendering

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.animationstudio.projectmanager.ProjectDatabase
import com.animationstudio.projectmanager.ProjectRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RenderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT_ID) ?: return Result.failure(workDataOf(KEY_ERROR to "Missing project ID"))
        val sceneId = inputData.getString(KEY_SCENE_ID) ?: return Result.failure(workDataOf(KEY_ERROR to "Missing scene ID"))
        val destination = inputData.getString(KEY_DESTINATION)?.let(Uri::parse) ?: return Result.failure(workDataOf(KEY_ERROR to "Missing destination"))
        val config = try { Json.decodeFromString<ExportConfig>(inputData.getString(KEY_CONFIG) ?: "") }
        catch (error: Exception) { return Result.failure(workDataOf(KEY_ERROR to "Invalid export configuration")) }
        return try {
            val repository = ProjectRepository(applicationContext, ProjectDatabase.create(applicationContext))
            val opened = repository.recover(projectId)
            val result = ExportManager(applicationContext).export(opened.project, sceneId, config, destination) { progress ->
                setProgress(workDataOf(
                    KEY_COMPLETED to progress.completedFrames, KEY_TOTAL to progress.totalFrames, KEY_PHASE to progress.phase,
                ))
            }
            Result.success(workDataOf(KEY_OUTPUT to result.outputUris.joinToString("\n"), KEY_RENDERED to result.renderedFrames))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (error.message ?: "Rendering failed")))
        }
    }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_SCENE_ID = "scene_id"
        const val KEY_DESTINATION = "destination"
        const val KEY_CONFIG = "config"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val KEY_OUTPUT = "output"
        const val KEY_RENDERED = "rendered"

        fun request(projectId: String, sceneId: String, destination: Uri, config: ExportConfig): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<RenderWorker>()
                .setInputData(workDataOf(KEY_PROJECT_ID to projectId, KEY_SCENE_ID to sceneId,
                    KEY_DESTINATION to destination.toString(), KEY_CONFIG to Json.encodeToString(config.validated())))
                .addTag("render:$projectId")
                .build()
    }
}
