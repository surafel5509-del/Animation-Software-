package com.animationstudio.rendering

import android.graphics.Bitmap
import android.media.*
import java.io.FileDescriptor
import kotlin.math.roundToInt

/** MediaCodec/MediaMuxer H.264 encoder using flexible YUV420 input and device-reported plane strides. */
class H264Encoder(
    output: FileDescriptor,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    bitrate: Int,
) {
    private val codec: MediaCodec
    private val muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var started = false
    private var finished = false

    init {
        require(width % 2 == 0 && height % 2 == 0) { "H.264 dimensions must be even" }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    fun encode(bitmap: Bitmap, frameIndex: Int) {
        check(!finished)
        require(bitmap.width == width && bitmap.height == height)
        val inputIndex = awaitInputBuffer()
        val image = codec.getInputImage(inputIndex) ?: error("Encoder does not expose YUV input; try another device/codec")
        bitmapToYuv420(bitmap, image)
        val presentation = frameIndex * 1_000_000L / fps
        codec.queueInputBuffer(inputIndex, 0, image.width * image.height * 3 / 2, presentation, 0)
        drain(endOfStream = false)
    }

    fun finish() {
        if (finished) return
        val input = awaitInputBuffer()
        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(endOfStream = true)
        try { codec.stop() } finally { codec.release() }
        if (started) muxer.stop()
        muxer.release()
        finished = true
    }

    fun abort() {
        if (finished) return
        try { codec.stop() } catch (_: Exception) {}
        codec.release()
        if (started) try { muxer.stop() } catch (_: Exception) {}
        muxer.release()
        finished = true
    }

    private fun awaitInputBuffer(): Int {
        while (true) {
            val index = codec.dequeueInputBuffer(20_000)
            if (index >= 0) return index
            drain(false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, if (endOfStream) 20_000 else 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started) { "Encoder changed format twice" }
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start(); started = true
                }
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex) ?: error("Missing encoder output")
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        check(started) { "Encoded data arrived before format" }
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (eos) return
                }
            }
        }
    }

    companion object {
        fun bitmapToYuv420(bitmap: Bitmap, image: Image) {
            require(image.format == ImageFormat.YUV_420_888)
            val width = bitmap.width; val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val color = pixels[y * width + x]
                    val r = (color ushr 16) and 0xff; val g = (color ushr 8) and 0xff; val b = color and 0xff
                    val luminance = 16f + (65.481f * r + 128.553f * g + 24.966f * b) / 255f
                    val yIndex = y * yPlane.rowStride + x * yPlane.pixelStride
                    yBuffer.put(yIndex, luminance.roundToInt().coerceIn(0, 255).toByte())
                    if (x % 2 == 0 && y % 2 == 0) {
                        val cb = 128f + (-37.797f * r - 74.203f * g + 112f * b) / 255f
                        val cr = 128f + (112f * r - 93.786f * g - 18.214f * b) / 255f
                        val uvIndex = (y / 2) * uPlane.rowStride + (x / 2) * uPlane.pixelStride
                        val vvIndex = (y / 2) * vPlane.rowStride + (x / 2) * vPlane.pixelStride
                        uBuffer.put(uvIndex, cb.roundToInt().coerceIn(0, 255).toByte())
                        vBuffer.put(vvIndex, cr.roundToInt().coerceIn(0, 255).toByte())
                    }
                }
            }
        }
    }
}
