package com.animationstudio.drawing

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class StrokeBuilderTest {
    @Test fun pressureAndBrushKindRemainEditable() {
        val builder = StrokeBuilder(BrushPreset("test", "Test", BrushKind.SOFT, size = 10f), 0xff123456.toInt())
        builder.add(Vec2.ZERO, .2f)
        builder.add(Vec2(10f, 0f), .5f)
        builder.add(Vec2(30f, 0f), 1f)
        val path = builder.finish()
        assertEquals(BrushKind.SOFT, path.brush)
        assertTrue(path.points.size >= 2)
        assertTrue(path.points.any { it.pressure < 1f })
        assertEquals(0xff123456.toInt(), path.stroke?.color)
    }

    @Test fun shapeFactoryNormalizesDragDirection() {
        val shape = ShapeFactory.create(ToolType.STAR, Vec2(100f, 100f), Vec2(0f, 20f), 0xff0000ff.toInt())
        assertEquals(RectF(0f, 20f, 100f, 100f), shape.bounds)
        assertEquals(ShapeType.STAR, shape.shape)
    }
}
