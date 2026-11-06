package com.animationstudio.drawing

import com.animationstudio.core.model.*
import com.animationstudio.vector.VectorGeometry.distance
import com.animationstudio.vector.VectorGeometry.distanceToSegment
import java.util.UUID
import kotlin.math.abs

/** Incremental stabilized stroke sampler. Input coordinates are canvas pixels. */
class StrokeBuilder(
    private val preset: BrushPreset,
    private val color: Int,
    private val id: String = UUID.randomUUID().toString(),
) {
    private var smoothed: Vec2? = null
    private var lastEmitted: Vec2? = null
    private val points = mutableListOf<PathPoint>()

    fun add(position: Vec2, pressure: Float = 1f, tilt: Float = 0f) {
        val previous = smoothed
        val smoothing = preset.smoothing.coerceIn(0f, .95f)
        val current = if (previous == null) position else previous * smoothing + position * (1f - smoothing)
        smoothed = current
        val safePressure = if (preset.pressureSensitive) pressure.coerceIn(.05f, 1f) else 1f
        val minimumDistance = (preset.size * preset.spacing).coerceAtLeast(.5f)
        if (lastEmitted == null || distance(current, lastEmitted!!) >= minimumDistance) {
            points += PathPoint(current, safePressure, tilt.coerceIn(-1f, 1f))
            lastEmitted = current
        }
    }

    fun preview(): VectorObject.PathObject = build(points)
    fun finish(): VectorObject.PathObject = build(simplify(points, .35f))

    private fun build(source: List<PathPoint>) = VectorObject.PathObject(
        id = id,
        name = preset.name,
        points = source,
        brush = preset.kind,
        stroke = StrokeStyle(
            color = withOpacity(color, preset.opacity),
            width = preset.size,
            opacity = preset.flow,
            cap = if (preset.kind == BrushKind.CALLIGRAPHY) StrokeCap.SQUARE else StrokeCap.ROUND,
        ),
    )

    private fun withOpacity(color: Int, opacity: Float): Int {
        val oldAlpha = (color ushr 24) and 0xff
        val alpha = (oldAlpha * opacity).toInt().coerceIn(0, 255)
        return (color and 0x00ffffff) or (alpha shl 24)
    }

    private fun simplify(input: List<PathPoint>, tolerance: Float): List<PathPoint> {
        if (input.size <= 2) return input
        val kept = BooleanArray(input.size) { false }
        kept[0] = true; kept[input.lastIndex] = true
        fun visit(first: Int, last: Int) {
            var maxDistance = 0f
            var index = -1
            for (i in first + 1 until last) {
                val distance = input[i].position.distanceToSegment(input[first].position, input[last].position)
                if (distance > maxDistance) { maxDistance = distance; index = i }
            }
            if (maxDistance > tolerance && index >= 0) {
                kept[index] = true
                visit(first, index); visit(index, last)
            }
        }
        visit(0, input.lastIndex)
        return input.filterIndexed { index, _ -> kept[index] }
    }
}

object ShapeFactory {
    fun create(tool: ToolType, start: Vec2, end: Vec2, color: Int, strokeWidth: Float = 4f, id: String = UUID.randomUUID().toString()): VectorObject.ShapeObject {
        val bounds = RectF(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
        val type = when (tool) {
            ToolType.RECTANGLE -> ShapeType.RECTANGLE
            ToolType.ELLIPSE -> ShapeType.ELLIPSE
            ToolType.POLYGON -> ShapeType.POLYGON
            ToolType.STAR -> ShapeType.STAR
            else -> error("$tool does not create a primitive shape")
        }
        return VectorObject.ShapeObject(
            id = id, name = type.name.lowercase().replaceFirstChar { it.uppercase() }, shape = type, bounds = bounds,
            stroke = StrokeStyle(color = 0xff20242dff.toInt(), width = strokeWidth),
            fill = FillStyle.Solid(color), vertices = if (type == ShapeType.POLYGON) 6 else 5,
        )
    }
}
