package com.animationstudio.rendering

import android.graphics.Bitmap
import android.graphics.Color
import java.io.OutputStream
import kotlin.math.roundToInt

/** GIF89a encoder with a 3:3:2 palette, transparency, looping, and real LZW compression. */
class AnimatedGifEncoder(private val output: OutputStream, private val width: Int, private val height: Int, private val loop: Boolean = true) {
    private var started = false

    fun addFrame(bitmap: Bitmap, fps: Int) {
        require(bitmap.width == width && bitmap.height == height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val transparent = pixels.any { Color.alpha(it) < 128 }
        val palette = IntArray(256)
        val indices = ByteArray(pixels.size)
        pixels.forEachIndexed { index, color ->
            if (transparent && Color.alpha(color) < 128) {
                indices[index] = 0
            } else {
                val base = (Color.red(color) and 0xe0) or ((Color.green(color) and 0xe0) shr 3) or (Color.blue(color) ushr 6)
                val paletteIndex = if (transparent) base % 255 + 1 else base
                palette[paletteIndex] = color or 0xff000000.toInt()
                indices[index] = paletteIndex.toByte()
            }
        }
        if (!started) writeHeader(palette)
        output.write(0x21); output.write(0xf9); output.write(4)
        output.write(if (transparent) 0x09 else 0x08) // disposal: do not dispose; optional transparency
        writeShort((100f / fps).roundToInt().coerceAtLeast(1))
        output.write(if (transparent) 0 else 0); output.write(0)
        output.write(0x2c); writeShort(0); writeShort(0); writeShort(width); writeShort(height); output.write(0)
        output.write(8)
        val compressed = lzw(indices)
        var offset = 0
        while (offset < compressed.size) {
            val size = minOf(255, compressed.size - offset)
            output.write(size); output.write(compressed, offset, size); offset += size
        }
        output.write(0)
    }

    fun finish() {
        if (started) output.write(0x3b)
    }

    private fun writeHeader(palette: IntArray) {
        output.write("GIF89a".toByteArray())
        writeShort(width); writeShort(height)
        output.write(0xf7); output.write(0); output.write(0) // global 256-color table
        palette.forEach { output.write(Color.red(it)); output.write(Color.green(it)); output.write(Color.blue(it)) }
        if (loop) {
            output.write(0x21); output.write(0xff); output.write(11)
            output.write("NETSCAPE2.0".toByteArray())
            output.write(3); output.write(1); writeShort(0); output.write(0)
        }
        started = true
    }

    private fun lzw(indices: ByteArray): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        val bits = BitPacker(bytes)
        var dictionary = HashMap<Int, Int>(4096)
        var nextCode = 258
        var codeSize = 9
        fun reset() {
            dictionary = HashMap(4096)
            nextCode = 258
            codeSize = 9
        }
        bits.write(256, codeSize)
        if (indices.isNotEmpty()) {
            var prefix = indices[0].toInt() and 0xff
            for (i in 1 until indices.size) {
                val next = indices[i].toInt() and 0xff
                val key = (prefix shl 8) or next
                val existing = dictionary[key]
                if (existing != null) {
                    prefix = existing
                } else {
                    bits.write(prefix, codeSize)
                    if (nextCode < 4096) {
                        dictionary[key] = nextCode++
                        if (nextCode == (1 shl codeSize) && codeSize < 12) codeSize++
                    } else {
                        bits.write(256, codeSize)
                        reset()
                    }
                    prefix = next
                }
            }
            bits.write(prefix, codeSize)
        }
        bits.write(257, codeSize)
        bits.flush()
        return bytes.toByteArray()
    }

    private fun writeShort(value: Int) {
        output.write(value and 0xff); output.write((value ushr 8) and 0xff)
    }

    private class BitPacker(private val output: java.io.ByteArrayOutputStream) {
        private var accumulator = 0
        private var bitCount = 0
        fun write(code: Int, size: Int) {
            accumulator = accumulator or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                output.write(accumulator and 0xff)
                accumulator = accumulator ushr 8
                bitCount -= 8
            }
        }
        fun flush() { if (bitCount > 0) output.write(accumulator and 0xff) }
    }
}
