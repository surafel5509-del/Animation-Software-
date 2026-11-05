package com.animationstudio.fileformat

import com.animationstudio.core.model.AssetMetadata
import com.animationstudio.core.model.StudioProject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class DecodedProject(val project: StudioProject, val assets: Map<String, ByteArray>)

/** Versioned ZIP container. scene.json always retains editable objects and animation tracks. */
class AnimStudioCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    fun write(
        project: StudioProject,
        output: OutputStream,
        assetStream: (AssetMetadata) -> InputStream? = { null },
    ) {
        ProjectValidator.validate(project)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(ArchiveManifest(format = FORMAT_NAME, version = project.formatVersion)).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(PROJECT_ENTRY))
            zip.write(json.encodeToString(project).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            project.assets.forEach { asset ->
                validateEntry(asset.entryName)
                val stream = assetStream(asset) ?: throw ProjectFormatException("Missing asset bytes: ${asset.displayName}")
                zip.putNextEntry(ZipEntry(asset.entryName))
                stream.use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun encode(project: StudioProject, assets: Map<String, ByteArray> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        write(project, output) { assets[it.id]?.inputStream() }
        return output.toByteArray()
    }

    fun read(input: InputStream): DecodedProject {
        var manifest: ArchiveManifest? = null
        var projectBytes: ByteArray? = null
        val assets = mutableMapOf<String, ByteArray>()
        val entries = mutableSetOf<String>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateEntry(entry.name)
                if (!entries.add(entry.name)) throw ProjectFormatException("Duplicate archive entry: ${entry.name}")
                if (entry.isDirectory) continue
                val limit = if (entry.name == PROJECT_ENTRY || entry.name == MANIFEST_ENTRY) MAX_PROJECT_BYTES else MAX_ASSET_BYTES
                val bytes = zip.readBounded(limit)
                totalBytes += bytes.size
                if (totalBytes > MAX_ARCHIVE_BYTES) throw ProjectFormatException("Project archive exceeds the safe size limit")
                when {
                    entry.name == MANIFEST_ENTRY -> manifest = json.decodeFromString<ArchiveManifest>(bytes.toString(Charsets.UTF_8))
                    entry.name == PROJECT_ENTRY -> projectBytes = bytes
                    entry.name.startsWith("assets/") -> assets[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        val validManifest = manifest ?: throw ProjectFormatException("Missing project manifest")
        if (validManifest.format != FORMAT_NAME) throw ProjectFormatException("Not an Animation Studio project")
        if (validManifest.version !in 1..StudioProject.CURRENT_FORMAT_VERSION)
            throw ProjectFormatException("Unsupported project version ${validManifest.version}")
        val project = try {
            json.decodeFromString<StudioProject>(projectBytes?.toString(Charsets.UTF_8)
                ?: throw ProjectFormatException("Missing editable scene data"))
        } catch (error: ProjectFormatException) { throw error }
        catch (error: Exception) { throw ProjectFormatException("Invalid project data", error) }
        if (project.formatVersion != validManifest.version) throw ProjectFormatException("Project version mismatch")
        ProjectValidator.validate(project)
        val byEntry = project.assets.associateBy(AssetMetadata::entryName)
        val resolved = assets.mapNotNull { (entry, bytes) -> byEntry[entry]?.id?.let { it to bytes } }.toMap()
        return DecodedProject(project, resolved)
    }

    private fun ZipInputStream.readBounded(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw ProjectFormatException("Project entry exceeds the safe size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun validateEntry(name: String) {
        if (name.isBlank() || name.startsWith('/') || name.contains('\\') || name.split('/').any { it == ".." })
            throw ProjectFormatException("Unsafe archive path")
    }

    @Serializable
    private data class ArchiveManifest(val format: String, val version: Int, val generator: String = "Animation Studio")

    companion object {
        const val FORMAT_NAME = "com.animationstudio.project"
        const val PROJECT_ENTRY = "scene.json"
        const val MANIFEST_ENTRY = "manifest.json"
        private const val MAX_PROJECT_BYTES = 32L * 1024 * 1024
        private const val MAX_ASSET_BYTES = 128L * 1024 * 1024
        private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        fun assetEntry(id: String, displayName: String): String {
            val safeId = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val safeName = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100)
            return "assets/$safeId/$safeName"
        }
    }
}
