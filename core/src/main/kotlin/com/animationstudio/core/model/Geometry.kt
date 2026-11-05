package com.animationstudio.core.model

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)
    operator fun div(scale: Float) = if (scale == 0f) ZERO else Vec2(x / scale, y / scale)
    fun dot(other: Vec2) = x * other.x + y * other.y
    fun lengthSquared() = x * x + y * y
    fun rotate(radians: Float) = Vec2(x * cos(radians) - y * sin(radians), x * sin(radians) + y * cos(radians))

    companion object { val ZERO = Vec2() }
}

@Serializable
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(right >= left && bottom >= top) { "Rectangle bounds must be ordered" } }
    val width get() = right - left
    val height get() = bottom - top
    val center get() = Vec2((left + right) / 2f, (top + bottom) / 2f)
    fun contains(point: Vec2) = point.x in left..right && point.y in top..bottom
    fun expand(amount: Float) = RectF(left - amount, top - amount, right + amount, bottom + amount)

    companion object {
        fun fromPoints(points: Iterable<Vec2>): RectF? {
            val iterator = points.iterator()
            if (!iterator.hasNext()) return null
            val first = iterator.next()
            var left = first.x
            var right = first.x
            var top = first.y
            var bottom = first.y
            iterator.forEachRemaining {
                left = minOf(left, it.x); right = maxOf(right, it.x)
                top = minOf(top, it.y); bottom = maxOf(bottom, it.y)
            }
            return RectF(left, top, right, bottom)
        }

        fun around(center: Vec2, width: Float, height: Float) = RectF(
            center.x - width / 2f, center.y - height / 2f,
            center.x + width / 2f, center.y + height / 2f,
        )
    }
}

@Serializable
data class Transform(
    val position: Vec2 = Vec2.ZERO,
    val rotation: Float = 0f,
    val scale: Vec2 = Vec2(1f, 1f),
    val opacity: Float = 1f,
) {
    fun apply(point: Vec2): Vec2 = Vec2(point.x * scale.x, point.y * scale.y).rotate(rotation) + position
}
