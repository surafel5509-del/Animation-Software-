package com.animationstudio.timeline

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class TimelineEditorTest {
    @Test fun upsertReplacesOnlySameFrameAndProperty() {
        var scene = Scene("s")
        scene = TimelineEditor.upsertKeyframe(scene, "o", AnimatableProperty.POSITION_X, Keyframe(0, 1f), "t")
        scene = TimelineEditor.upsertKeyframe(scene, "o", AnimatableProperty.POSITION_X, Keyframe(10, 2f), "t")
        scene = TimelineEditor.upsertKeyframe(scene, "o", AnimatableProperty.POSITION_X, Keyframe(0, 3f), "t")
        assertEquals(1, scene.tracks.size)
        assertEquals(listOf(3f, 2f), scene.tracks.single().keyframes.map { it.value })
    }

    @Test fun insertionMovesKeyframesAndExtendsHeldCel() {
        val scene = Scene("s", layers = listOf(Layer("l", "Layer")),
            tracks = listOf(AnimationTrack("t", "o", AnimatableProperty.ROTATION, listOf(Keyframe(4, 1f)))),
            workArea = WorkArea(0, 10))
        val edited = TimelineEditor.insertFrames(scene, 0, 2)
        assertEquals(2, edited.layers[0].cels[0].frame)
        assertEquals(6, edited.tracks[0].keyframes[0].frame)
        assertEquals(12, edited.workArea.end)
    }

    @Test fun timingLoopsInsideWorkArea() {
        assertEquals(3, FrameTiming.advance(6, WorkArea(3, 7), 20))
        assertEquals(6, FrameTiming.advance(6, WorkArea(3, 7, loop = false), 20))
        assertEquals(.5, FrameTiming.timeSeconds(12, 24), .0)
    }
}
