package com.animationstudio.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/** Decodes real PCM peaks for timeline waveforms; runs off the UI thread. */
object WaveformDecoder {
    suspend fun decode(file: File, buckets: Int = 400): List<Float> = withContext(Dispatchers.IO) {
        require(buckets > 0)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext emptyList()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0); start()
            }
            val peaks = FloatArray(buckets)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val expectedFrames = (sampleRate.toDouble() * durationUs / 1_000_000.0).toLong().coerceAtLeast(1L)
            var sampleIndex = 0L
            var decoding = false
            while (!decoding) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val info = MediaCodec.BufferInfo()
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)!!
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    while (buffer.remaining() >= 2) {
                        val sample = buffer.short.toInt() / 32768f
                        val energy = abs(sample)
                        val frameIndex = sampleIndex / channels
                        val bucket = (frameIndex * buckets / expectedFrames).toInt().coerceIn(0, buckets - 1)
                        peaks[bucket] = maxOf(peaks[bucket], energy)
                        sampleIndex++
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoding = true
                }
            }
            val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
            peaks.map { sqrt(it / max).coerceIn(0f, 1f) }
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release(); extractor.release()
        }
    }
}
