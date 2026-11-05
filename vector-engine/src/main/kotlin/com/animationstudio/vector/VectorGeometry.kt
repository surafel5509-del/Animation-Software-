package com.animationstudio.vector

import com.animationstudio.core.model.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object VectorGeometry {
    fun bounds(obj: VectorObject): RectF? = when (obj) {
        is VectorObject.PathObject -> RectF.fromPoints(obj.points.map { obj.transform.apply(it.position) })
            ?.expand((obj.stroke?.width ?: 0f) / 2f)
        is VectorObject.ShapeObject -> transformedBounds(obj.bounds, obj.transform)
        is VectorObject.TextObject -> transformedBounds(
            RectF(obj.position.x, obj.position.y - obj.size, obj.position.x + obj.text.length * obj.size * .6f, obj.position.y),
            obj.transform,
        )
        is VectorObject.ImageObject -> transformedBounds(obj.bounds, obj.transform)
    }

    private fun transformedBounds(rect: RectF, transform: Transform): RectF = RectF.fromPoints(listOf(
        transform.apply(Vec2(rect.left, rect.top)), transform.apply(Vec2(rect.right, rect.top)),
        transform.apply(Vec2(rect.right, rect.bottom)), transform.apply(Vec2(rect.left, rect.bottom)),
    ))!!

    fun hitTest(obj: VectorObject, point: Vec2, tolerance: Float = 12f): Boolean {
        if (!obj.visible || obj.locked) return false
        if (obj is VectorObject.PathObject && obj.stroke != null) {
            val points = obj.points.map { obj.transform.apply(it.position) }
            val distance = points.zipWithNext().minOfOrNull { (a, b) -> point.distanceToSegment(a, b) } ?: Float.MAX_VALUE
            if (distance <= tolerance + obj.stroke.width / 2f) return true
        }
        return bounds(obj)?.expand(tolerance)?.contains(point) == true
    }

    fun Vec2.distanceToSegment(a: Vec2, b: Vec2): Float {
        val ab = b - a
        val length = ab.lengthSquared()
        if (length == 0f) return kotlin.math.sqrt((this - a).lengthSquared())
        val t = (((this - a).dot(ab)) / length).coerceIn(0f, 1f)
        return kotlin.math.sqrt((this - (a + ab * t)).lengthSquared())
    }

    /** Converts cubic Bézier controls to line segments for hit testing and motion paths. */
    fun flatten(path: List<PathPoint>, closed: Boolean = false, segmentsPerCurve: Int = 16): List<Vec2> {
        if (path.size < 2) return path.map(PathPoint::position)
        val result = mutableListOf(path.first().position)
        val count = if (closed) path.size else path.size - 1
        for (index in 0 until count) {
            val start = path[index]
            val end = path[(index + 1) % path.size]
            val out = start.controlOut
            val incoming = end.controlIn
            if (out == null && incoming == null) {
                result += end.position
            } else {
                val c1 = out ?: start.position
                val c2 = incoming ?: end.position
                for (step in 1..segmentsPerCurve) {
                    val t = step.toFloat() / segmentsPerCurve
                    val u = 1f - t
                    result += start.position * (u * u * u) + c1 * (3f * u * u * t) +
                        c2 * (3f * u * t * t) + end.position * (t * t * t)
                }
            }
        }
        return result
    }

    fun regularPolygon(bounds: RectF, vertices: Int, starInnerRadius: Float? = null): List<Vec2> {
        val count = vertices.coerceAtLeast(3) * if (starInnerRadius == null) 1 else 2
        val center = bounds.center
        val rx = bounds.width / 2f
        val ry = bounds.height / 2f
        return (0 until count).map { index ->
            val radius = if (starInnerRadius != null && index % 2 == 1) starInnerRadius.coerceIn(.05f, 1f) else 1f
            val angle = -Math.PI / 2 + index * Math.PI * 2 / count
            Vec2(center.x + (rx * radius * cos(angle)).toFloat(), center.y + (ry * radius * sin(angle)).toFloat())
        }
    }

    fun withTransform(obj: VectorObject, transform: Transform): VectorObject = when (obj) {
        is VectorObject.PathObject -> obj.copy(transform = transform)
        is VectorObject.ShapeObject -> obj.copy(transform = transform)
        is VectorObject.TextObject -> obj.copy(transform = transform)
        is VectorObject.ImageObject -> obj.copy(transform = transform)
    }

    fun withFill(obj: VectorObject, fill: FillStyle): VectorObject = when (obj) {
        is VectorObject.PathObject -> obj.copy(fill = fill)
        is VectorObject.ShapeObject -> obj.copy(fill = fill)
        is VectorObject.TextObject -> obj.copy(color = (fill as? FillStyle.Solid)?.color ?: obj.color)
        is VectorObject.ImageObject -> obj
    }

    fun colorOf(obj: VectorObject): Int? = when (obj) {
        is VectorObject.PathObject -> obj.stroke?.color ?: (obj.fill as? FillStyle.Solid)?.color
        is VectorObject.ShapeObject -> (obj.fill as? FillStyle.Solid)?.color ?: obj.stroke?.color
        is VectorObject.TextObject -> obj.color
        is VectorObject.ImageObject -> null
    }

    fun distance(a: Vec2, b: Vec2) = kotlin.math.sqrt((a - b).lengthSquared())
    fun nearlyZero(value: Float) = abs(value) < 0.0001f
}
