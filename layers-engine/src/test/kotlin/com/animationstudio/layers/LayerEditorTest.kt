package com.animationstudio.layers

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LayerEditorTest {
    @Test fun removingGroupRemovesDescendantsAndTracks() {
        val scene = Scene("s", layers = listOf(
            Layer("root", "Root", LayerType.GROUP, cels = emptyList()),
            Layer("child", "Child", parentId = "root"),
            Layer("other", "Other"),
        ), tracks = listOf(AnimationTrack("t", "child", AnimatableProperty.OPACITY)))
        val edited = LayerEditor.remove(scene, "root")
        assertEquals(listOf("other"), edited.layers.map(Layer::id))
        assertTrue(edited.tracks.isEmpty())
    }

    @Test fun duplicateRegeneratesObjectAndTrackIds() {
        val obj = VectorObject.PathObject("old", points = listOf(PathPoint(Vec2.ZERO)))
        val scene = Scene("s", layers = listOf(Layer("l", "Layer", cels = listOf(DrawingCel(0, objects = listOf(obj))))),
            tracks = listOf(AnimationTrack("t", "old", AnimatableProperty.POSITION_X)))
        val edited = LayerEditor.duplicate(scene, "l", "copy")
        val copied = edited.layers[1].cels[0].objects.single()
        assertNotEquals("old", copied.id)
        assertNotEquals("t", edited.tracks[1].id)
        assertEquals(copied.id, edited.tracks[1].targetId)
    }

    @Test fun cyclesCannotBeCreatedThroughReparenting() {
        val layers = listOf(Layer("a", "A", LayerType.GROUP), Layer("b", "B", LayerType.GROUP, parentId = "a"))
        val scene = Scene("s", layers = layers)
        assertEquals(scene, LayerEditor.setParent(scene, "a", "b"))
    }
}
