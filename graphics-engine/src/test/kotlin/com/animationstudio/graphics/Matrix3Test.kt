package com.animationstudio.graphics

import com.animationstudio.core.model.Transform
import com.animationstudio.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class Matrix3Test {
    @Test fun inverseRoundTrip() {
        val matrix = Matrix3.from(Transform(Vec2(20f, 30f), .7f, Vec2(2f, 3f)))
        val point = Vec2(12f, -8f)
        val restored = matrix.inverse() * (matrix * point)
        assertEquals(point.x, restored.x, .001f)
        assertEquals(point.y, restored.y, .001f)
    }
}
