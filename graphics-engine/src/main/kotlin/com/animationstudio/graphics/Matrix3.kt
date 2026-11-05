package com.animationstudio.graphics

import com.animationstudio.core.model.Transform
import com.animationstudio.core.model.Vec2

/** Column-vector affine matrix used by rigging, hit testing, and software rendering. */
data class Matrix3(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f,
    val d: Float = 0f, val e: Float = 1f, val f: Float = 0f,
) {
    operator fun times(other: Matrix3) = Matrix3(
        a * other.a + b * other.d, a * other.b + b * other.e, a * other.c + b * other.f + c,
        d * other.a + e * other.d, d * other.b + e * other.e, d * other.c + e * other.f + f,
    )

    operator fun times(point: Vec2) = Vec2(a * point.x + b * point.y + c, d * point.x + e * point.y + f)

    fun inverse(): Matrix3 {
        val determinant = a * e - b * d
        require(kotlin.math.abs(determinant) > 0.000001f) { "Transform is not invertible" }
        val inv = 1f / determinant
        val ia = e * inv
        val ib = -b * inv
        val id = -d * inv
        val ie = a * inv
        return Matrix3(ia, ib, -(ia * c + ib * f), id, ie, -(id * c + ie * f))
    }

    companion object {
        fun translation(value: Vec2) = Matrix3(c = value.x, f = value.y)
        fun scale(value: Vec2) = Matrix3(a = value.x, e = value.y)
        fun rotation(radians: Float): Matrix3 {
            val cos = kotlin.math.cos(radians)
            val sin = kotlin.math.sin(radians)
            return Matrix3(cos, -sin, 0f, sin, cos, 0f)
        }
        fun from(transform: Transform) = translation(transform.position) * rotation(transform.rotation) * scale(transform.scale)
    }
}
