package com.animationstudio.projectmanager

import android.content.Context
import android.net.Uri
import com.animationstudio.fileformat.AnimStudioCodec
import com.animationstudio.fileformat.ProjectFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

class AssetStorage(private val context: Context) {
    private fun projectDirectory(projectId: String) = File(context.filesDir, "projects/$projectId")

    fun file(projectId: String, entryName: String): File {
        require(entryName.startsWith("assets/") && !entryName.contains("..")) { "Unsafe asset entry" }
        return File(projectDirectory(projectId), entryName)
    }

    fun open(projectId: String, entryName: String): InputStream? = file(projectId, entryName).let {
        if (it.isFile) FileInputStream(it) else null
    }

    suspend fun import(projectId: String, source: Uri, displayName: String, id: String = UUID.randomUUID().toString()): Pair<String, String> = withContext(Dispatchers.IO) {
        val entry = AnimStudioCodec.assetEntry(id, displayName)
        val destination = file(projectId, entry)
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        context.contentResolver.openInputStream(source)?.use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_IMPORT_BYTES) throw ProjectFormatException("Imported asset exceeds 128 MiB safety limit")
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw ProjectFormatException("Cannot open selected asset")
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        id to entry
    }

    fun deleteProject(projectId: String) {
        projectDirectory(projectId).deleteRecursively()
    }

    companion object { private const val MAX_IMPORT_BYTES = 128L * 1024 * 1024 }
}
