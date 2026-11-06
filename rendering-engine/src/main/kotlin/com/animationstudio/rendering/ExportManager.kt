package com.animationstudio.rendering

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import com.animationstudio.core.model.StudioProject
import com.animationstudio.fileformat.ProjectFormatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.coroutineContext

enum class ExportFormat { PNG, JPEG, PNG_SEQUENCE, JPEG_SEQUENCE, GIF, MP4 }

@Serializable
data class ExportConfig(
    val format: ExportFormat = ExportFormat.MP4,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 24,
    val startFrame: Int = 0,
    val endFrame: Int = 240,
    val quality: Int = 92,
    val bitrate: Int = 8_000_000,
) {
    fun validated(): ExportConfig {
        require(width in 16..8192 && height in 16..8192) { "Export dimensions must be 16–8192 pixels" }
        require(fps in 1..60) { "Export FPS must be 1–60" }
        require(startFrame >= 0 && endFrame > startFrame) { "Invalid export frame range" }
        require(quality in 1..100 && bitrate in 100_000..100_000_000)
        return if (format == ExportFormat.MP4) copy(width = width / 2 * 2, height = height / 2 * 2) else this
    }
}

data class ExportProgress(val completedFrames: Int, val totalFrames: Int, val phase: String) {
    val fraction get() = if (totalFrames == 0) 0f else completedFrames.toFloat() / totalFrames
}

data class ExportResult(val outputUris: List<Uri>, val renderedFrames: Int)

class ExportManager(private val context: Context) {
    suspend fun export(
        project: StudioProject,
        sceneId: String,
        rawConfig: ExportConfig,
        destination: Uri,
        onProgress: suspend (ExportProgress) -> Unit = {},
    ): ExportResult = withContext(Dispatchers.IO) {
        val config = rawConfig.validated()
        val scene = project.scenes.firstOrNull { it.id == sceneId } ?: throw ProjectFormatException("Scene not found")
        val renderer = SoftwareFrameRenderer(context, project)
        val total = config.endFrame - config.startFrame
        onProgress(ExportProgress(0, total, "Preparing renderer"))
        try {
            when (config.format) {
                ExportFormat.PNG, ExportFormat.JPEG -> {
                    val bitmap = renderer.render(scene, config.startFrame, config.width, config.height)
                    writeBitmap(destination, bitmap, config)
                    bitmap.recycle()
                    onProgress(ExportProgress(1, 1, "Complete"))
                    ExportResult(listOf(destination), 1)
                }
                ExportFormat.PNG_SEQUENCE, ExportFormat.JPEG_SEQUENCE -> {
                    val outputs = mutableListOf<Uri>()
                    for ((index, frame) in (config.startFrame until config.endFrame).withIndex()) {
                        coroutineContext.ensureActive()
                        val mime = if (config.format == ExportFormat.PNG_SEQUENCE) "image/png" else "image/jpeg"
                        val extension = if (config.format == ExportFormat.PNG_SEQUENCE) "png" else "jpg"
                        val uri = DocumentsContract.createDocument(context.contentResolver, destination, mime,
                            "${safeName(project.name)}_${frame.toString().padStart(6, '0')}.$extension")
                            ?: throw ProjectFormatException("Cannot create image in selected directory")
                        val bitmap = renderer.render(scene, frame, config.width, config.height)
                        writeBitmap(uri, bitmap, config)
                        bitmap.recycle()
                        outputs += uri
                        onProgress(ExportProgress(index + 1, total, "Rendering image sequence"))
                    }
                    ExportResult(outputs, total)
                }
                ExportFormat.GIF -> exportGif(renderer, project, scene.id, config, destination, onProgress)
                ExportFormat.MP4 -> exportMp4(renderer, project, scene.id, config, destination, onProgress)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            throw ProjectFormatException("Export failed: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private suspend fun exportGif(
        renderer: SoftwareFrameRenderer, project: StudioProject, sceneId: String,
        config: ExportConfig, destination: Uri, onProgress: suspend (ExportProgress) -> Unit,
    ): ExportResult {
        val scene = project.scenes.first { it.id == sceneId }
        val stream = context.contentResolver.openOutputStream(destination, "rwt")
            ?: throw ProjectFormatException("Cannot open GIF destination")
        stream.use { output ->
            val encoder = AnimatedGifEncoder(output, config.width, config.height)
            for ((index, frame) in (config.startFrame until config.endFrame).withIndex()) {
                coroutineContext.ensureActive()
                val bitmap = renderer.render(scene, frame, config.width, config.height)
                encoder.addFrame(bitmap, config.fps)
                bitmap.recycle()
                onProgress(ExportProgress(index + 1, config.endFrame - config.startFrame, "Encoding GIF"))
            }
            encoder.finish()
        }
        return ExportResult(listOf(destination), config.endFrame - config.startFrame)
    }

    private suspend fun exportMp4(
        renderer: SoftwareFrameRenderer, project: StudioProject, sceneId: String,
        config: ExportConfig, destination: Uri, onProgress: suspend (ExportProgress) -> Unit,
    ): ExportResult {
        val scene = project.scenes.first { it.id == sceneId }
        val descriptor = context.contentResolver.openFileDescriptor(destination, "rwt")
            ?: throw ProjectFormatException("Cannot open MP4 destination")
        var encoder: H264Encoder? = null
        try {
            descriptor.use {
                val h264 = H264Encoder(it.fileDescriptor, config.width, config.height, config.fps, config.bitrate)
                encoder = h264
                for ((index, frame) in (config.startFrame until config.endFrame).withIndex()) {
                    coroutineContext.ensureActive()
                    val bitmap = renderer.render(scene, frame, config.width, config.height)
                    h264.encode(bitmap, index)
                    bitmap.recycle()
                    onProgress(ExportProgress(index + 1, config.endFrame - config.startFrame, "Encoding H.264 video (silent)"))
                }
                h264.finish()
            }
        } catch (error: Exception) {
            encoder?.abort()
            throw error
        }
        return ExportResult(listOf(destination), config.endFrame - config.startFrame)
    }

    private fun writeBitmap(uri: Uri, bitmap: Bitmap, config: ExportConfig) {
        val jpeg = config.format == ExportFormat.JPEG || config.format == ExportFormat.JPEG_SEQUENCE
        if (jpeg && bitmap.hasAlpha()) {
            // JPEG has no alpha channel; Android composites transparent pixels to black unless a background is drawn.
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
            android.graphics.Canvas(flattened).apply { drawColor(configBackground(bitmap)); drawBitmap(bitmap, 0f, 0f, null) }
            writeCompressed(uri, flattened, Bitmap.CompressFormat.JPEG, config.quality)
            flattened.recycle()
        } else writeCompressed(uri, bitmap, if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, config.quality)
    }

    private fun configBackground(bitmap: Bitmap): Int {
        val pixel = if (bitmap.width > 0 && bitmap.height > 0) bitmap.getPixel(0, 0) else 0
        return pixel or 0xff000000.toInt()
    }

    private fun writeCompressed(uri: Uri, bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
            if (!bitmap.compress(format, quality, output)) throw ProjectFormatException("Bitmap encoder failed")
        } ?: throw ProjectFormatException("Cannot open image destination")
    }

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifBlank { "animation" }
}
