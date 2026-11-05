package com.animationstudio.projectmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.animationstudio.core.model.StudioProject
import com.animationstudio.fileformat.AnimStudioCodec
import com.animationstudio.fileformat.DecodedProject
import com.animationstudio.fileformat.ProjectFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class OpenedProject(val project: StudioProject, val externalUri: Uri?, val recovered: Boolean = false)

/** Atomic local autosaves plus Storage Access Framework import/export. No storage permission is required. */
class ProjectRepository(
    private val context: Context,
    private val database: ProjectDatabase,
    private val codec: AnimStudioCodec = AnimStudioCodec(),
    val assets: AssetStorage = AssetStorage(context),
) {
    private val saveMutex = Mutex()
    val recent: Flow<List<ProjectMetadataEntity>> = database.metadata().observeRecent()

    private fun autosaveFile(projectId: String) = File(context.filesDir, "projects/$projectId/autosave.animstudio")

    suspend fun create(project: StudioProject): StudioProject = saveInternal(project)

    suspend fun autosave(project: StudioProject): StudioProject = saveInternal(project)

    suspend fun save(project: StudioProject, destination: Uri? = null): OpenedProject = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val updated = project.copy(modifiedAt = System.currentTimeMillis())
            writeAutosave(updated)
            val existing = database.metadata().get(updated.id)
            val target = destination ?: existing?.externalUri?.let(Uri::parse)
            if (target != null) {
                context.contentResolver.openOutputStream(target, "rwt")?.use { output ->
                    autosaveFile(updated.id).inputStream().use { it.copyTo(output) }
                } ?: throw ProjectFormatException("Cannot open save location")
            }
            record(updated, target?.toString())
            OpenedProject(updated, target)
        }
    }

    suspend fun openExternal(uri: Uri): OpenedProject = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) { /* Some providers only grant transient access. */ }
        val decoded = context.contentResolver.openInputStream(uri)?.use(codec::read)
            ?: throw ProjectFormatException("Cannot open selected project")
        materializeAssets(decoded)
        writeAutosave(decoded.project)
        record(decoded.project, uri.toString())
        OpenedProject(decoded.project, uri)
    }

    suspend fun recover(projectId: String, externalUri: Uri? = null): OpenedProject = withContext(Dispatchers.IO) {
        val file = autosaveFile(projectId)
        if (!file.isFile) throw ProjectFormatException("No recoverable autosave exists")
        val decoded = file.inputStream().use(codec::read)
        materializeAssets(decoded)
        record(decoded.project, externalUri?.toString() ?: database.metadata().get(projectId)?.externalUri)
        OpenedProject(decoded.project, externalUri, recovered = true)
    }

    suspend fun delete(projectId: String) = withContext(Dispatchers.IO) {
        database.metadata().delete(projectId)
        assets.deleteProject(projectId)
    }

    suspend fun duplicate(source: StudioProject, newId: String, name: String): StudioProject = withContext(Dispatchers.IO) {
        val duplicate = source.copy(id = newId, name = name, createdAt = System.currentTimeMillis(), modifiedAt = System.currentTimeMillis())
        source.assets.forEach { asset ->
            val input = assets.open(source.id, asset.entryName) ?: throw ProjectFormatException("Missing asset ${asset.displayName}")
            val destination = assets.file(newId, asset.entryName)
            destination.parentFile?.mkdirs()
            input.use { sourceStream -> destination.outputStream().use { sourceStream.copyTo(it) } }
        }
        saveInternal(duplicate)
    }

    suspend fun rename(project: StudioProject, name: String): StudioProject = saveInternal(project.copy(name = name.trim().ifBlank { project.name }))

    private suspend fun saveInternal(project: StudioProject): StudioProject = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val updated = project.copy(modifiedAt = System.currentTimeMillis())
            writeAutosave(updated)
            record(updated, database.metadata().get(updated.id)?.externalUri)
            updated
        }
    }

    private fun writeAutosave(project: StudioProject) {
        val file = autosaveFile(project.id)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "autosave.tmp")
        try {
            temporary.outputStream().buffered().use { output ->
                codec.write(project, output) { asset -> assets.open(project.id, asset.entryName) }
            }
            if (file.exists() && !file.delete()) throw ProjectFormatException("Cannot replace old autosave")
            if (!temporary.renameTo(file)) throw ProjectFormatException("Cannot commit autosave")
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun materializeAssets(decoded: DecodedProject) {
        decoded.project.assets.forEach { asset ->
            val bytes = decoded.assets[asset.id] ?: throw ProjectFormatException("Project is missing asset ${asset.displayName}")
            val destination = assets.file(decoded.project.id, asset.entryName)
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
        }
    }

    private suspend fun record(project: StudioProject, externalUri: String?) {
        database.metadata().upsert(ProjectMetadataEntity(
            project.id, project.name, project.modifiedAt, project.canvas.width, project.canvas.height,
            project.fps, project.durationFrames, autosaveFile(project.id).absolutePath, externalUri,
        ))
    }
}
