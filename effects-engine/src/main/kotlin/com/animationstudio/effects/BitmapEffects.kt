package com.animationstudio.effects

import android.graphics.*
import com.animationstudio.core.model.EffectSettings
import com.animationstudio.core.model.EffectType
import kotlin.math.exp
import kotlin.math.roundToInt

/** CPU compositor used by export. Every returned bitmap is independent of the input. */
object BitmapEffects {
    fun apply(source: Bitmap, effects: List<EffectSettings>): Bitmap {
        var current = source.copy(Bitmap.Config.ARGB_8888, true)
        effects.filter(EffectSettings::enabled).forEach { effect ->
            current = when (effect.type) {
                EffectType.GAUSSIAN_BLUR -> gaussianBlur(current, effect.radius * effect.intensity)
                EffectType.DROP_SHADOW -> shadow(current, effect, glow = false)
                EffectType.GLOW -> shadow(current, effect, glow = true)
                EffectType.BRIGHTNESS, EffectType.CONTRAST, EffectType.SATURATION, EffectType.HUE, EffectType.OPACITY -> colorAdjust(current, effect)
                EffectType.MOTION_BLUR -> motionBlur(current, effect)
                EffectType.MASK -> current // Mask geometry is applied by the layer compositor, not this color pass.
            }
        }
        return current
    }

    fun gaussianBlur(source: Bitmap, radius: Float): Bitmap {
        val r = radius.roundToInt().coerceIn(0, 64)
        if (r == 0) return source.copy(Bitmap.Config.ARGB_8888, true)
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val kernel = FloatArray(2 * r + 1) { index ->
            val x = index - r
            exp(-(x * x).toFloat() / (2f * (r / 2f).coerceAtLeast(.5f).let { it * it }))
        }.let { values -> val sum = values.sum(); values.map { it / sum }.toFloatArray() }
        val temp = IntArray(input.size)
        val output = IntArray(input.size)
        convolve(input, temp, width, height, kernel, true)
        convolve(temp, output, width, height, kernel, false)
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun convolve(input: IntArray, output: IntArray, width: Int, height: Int, kernel: FloatArray, horizontal: Boolean) {
        val radius = kernel.size / 2
        for (y in 0 until height) for (x in 0 until width) {
            var a = 0f; var red = 0f; var green = 0f; var blue = 0f
            for (k in kernel.indices) {
                val sx = if (horizontal) (x + k - radius).coerceIn(0, width - 1) else x
                val sy = if (horizontal) y else (y + k - radius).coerceIn(0, height - 1)
                val color = input[sy * width + sx]
                val weight = kernel[k]
                a += Color.alpha(color) * weight; red += Color.red(color) * weight
                green += Color.green(color) * weight; blue += Color.blue(color) * weight
            }
            output[y * width + x] = Color.argb(a.roundToInt(), red.roundToInt(), green.roundToInt(), blue.roundToInt())
        }
    }

    private fun colorAdjust(source: Bitmap, effect: EffectSettings): Bitmap {
        val matrix = ColorMatrix()
        when (effect.type) {
            EffectType.BRIGHTNESS -> matrix.set(floatArrayOf(
                1f,0f,0f,0f,effect.intensity, 0f,1f,0f,0f,effect.intensity,
                0f,0f,1f,0f,effect.intensity, 0f,0f,0f,1f,0f))
            EffectType.CONTRAST -> {
                val contrast = effect.intensity
                val shift = 127.5f * (1f - contrast)
                matrix.set(floatArrayOf(contrast,0f,0f,0f,shift, 0f,contrast,0f,0f,shift,
                    0f,0f,contrast,0f,shift, 0f,0f,0f,1f,0f))
            }
            EffectType.SATURATION -> matrix.setSaturation(effect.intensity.coerceAtLeast(0f))
            EffectType.HUE -> {
                val radians = Math.toRadians(effect.intensity.toDouble())
                val cos = kotlin.math.cos(radians).toFloat(); val sin = kotlin.math.sin(radians).toFloat()
                matrix.set(floatArrayOf(
                    .213f + cos * .787f - sin * .213f, .715f - cos * .715f - sin * .715f, .072f - cos * .072f + sin * .928f, 0f, 0f,
                    .213f - cos * .213f + sin * .143f, .715f + cos * .285f + sin * .140f, .072f - cos * .072f - sin * .283f, 0f, 0f,
                    .213f - cos * .213f - sin * .787f, .715f - cos * .715f + sin * .715f, .072f + cos * .928f + sin * .072f, 0f, 0f,
                    0f,0f,0f,1f,0f))
            }
            EffectType.OPACITY -> matrix.setScale(1f, 1f, 1f, effect.intensity.coerceIn(0f, 1f))
            else -> Unit
        }
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return result
    }

    private fun shadow(source: Bitmap, effect: EffectSettings, glow: Boolean): Bitmap {
        val padding = (effect.radius * 3 + effect.offset.x + effect.offset.y).toInt().coerceAtLeast(4)
        val result = Bitmap.createBitmap(source.width + padding * 2, source.height + padding * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val alpha = source.extractAlpha()
        val tint = Paint().apply { colorFilter = PorterDuffColorFilter(effect.color, PorterDuff.Mode.SRC_IN) }
        val colored = Bitmap.createBitmap(alpha.width, alpha.height, Bitmap.Config.ARGB_8888)
        Canvas(colored).drawBitmap(alpha, 0f, 0f, tint)
        val blurred = gaussianBlur(colored, effect.radius)
        canvas.drawBitmap(blurred, padding + effect.offset.x, padding + effect.offset.y, null)
        if (!glow) canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        else canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        return result
    }

    private fun motionBlur(source: Bitmap, effect: EffectSettings): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val samples = effect.intensity.roundToInt().coerceIn(2, 16)
        repeat(samples) { index ->
            val t = index.toFloat() / (samples - 1) - .5f
            canvas.drawBitmap(source, effect.offset.x * t, effect.offset.y * t, Paint().apply { alpha = 255 / samples })
        }
        return result
    }
}
