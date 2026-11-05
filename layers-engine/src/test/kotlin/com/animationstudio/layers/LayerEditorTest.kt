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

    @Test fun cyclesCannotBeCreatedThroughReparenting() {
        val layers = listOf(Layer("a", "A", LayerType.GROUP), Layer("b", "B", LayerType.GROUP, parentId = "a"))
        val scene = Scene("s", layers = layers)
        assertEquals(scene, LayerEditor.setParent(scene, "a", "b"))
    }
}
