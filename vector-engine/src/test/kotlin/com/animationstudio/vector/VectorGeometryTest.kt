package com.animationstudio.vector

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class VectorGeometryTest {
    @Test fun cubicFlatteningIncludesEndpoints() {
        val points = listOf(
            PathPoint(Vec2.ZERO, controlOut = Vec2(25f, 100f)),
            PathPoint(Vec2(100f, 0f), controlIn = Vec2(75f, 100f)),
        )
        val flattened = VectorGeometry.flatten(points)
        assertEquals(Vec2.ZERO, flattened.first())
        assertEquals(Vec2(100f, 0f), flattened.last())
        assertTrue(flattened.size > 10)
        assertTrue(flattened.maxOf { it.y } > 49f)
    }

    @Test fun transformedBoundsAndStrokeHitTest() {
        val path = VectorObject.PathObject("p", points = listOf(PathPoint(Vec2.ZERO), PathPoint(Vec2(100f, 0f))))
        assertTrue(VectorGeometry.hitTest(path, Vec2(50f, 5f), 2f))
        assertFalse(VectorGeometry.hitTest(path, Vec2(50f, 30f), 2f))
        val transformed = VectorGeometry.withTransform(path, Transform(position = Vec2(10f, 20f)))
        assertEquals(RectF(6f, 16f, 114f, 24f), VectorGeometry.bounds(transformed))
    }
}
