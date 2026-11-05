package com.animationstudio.importer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.animationstudio.core.model.AssetKind
import com.animationstudio.core.model.AssetMetadata
import com.animationstudio.projectmanager.AssetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AssetImporter(private val context: Context, private val storage: AssetStorage) {
    suspend fun import(projectId: String, uri: Uri): AssetMetadata = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri) ?: mimeFromName(name)
        val kind = when {
            mime.startsWith("image/") -> AssetKind.IMAGE
            mime.startsWith("audio/") -> AssetKind.AUDIO
            mime.startsWith("video/") -> AssetKind.VIDEO
            mime.startsWith("font/") -> AssetKind.FONT
            else -> AssetKind.OTHER
        }
        require(kind == AssetKind.IMAGE || kind == AssetKind.AUDIO) { "Only image and audio assets are supported by this importer" }
        val id = UUID.randomUUID().toString()
        val (_, entry) = storage.import(projectId, uri, name, id)
        val file = storage.file(projectId, entry)
        var width: Int? = null
        var height: Int? = null
        var duration: Long? = null
        if (kind == AssetKind.IMAGE) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) error("Unsupported or damaged image")
            if (options.outWidth > 16384 || options.outHeight > 16384) error("Image exceeds 16384 pixel dimension limit")
            width = options.outWidth; height = options.outHeight
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally { retriever.release() }
        }
        AssetMetadata(id, kind, name, mime, entry, width, height, duration)
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported asset"
    }

    private fun mimeFromName(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
        "wav" -> "audio/wav"; "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
