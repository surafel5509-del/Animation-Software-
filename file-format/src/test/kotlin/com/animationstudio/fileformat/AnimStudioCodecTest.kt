package com.animationstudio.fileformat

import com.animationstudio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AnimStudioCodecTest {
    private val codec = AnimStudioCodec()

    @Test fun editableProjectRoundTripIncludesVectorsKeysRigsAndAssets() {
        val original = ProjectFactory.create("Round trip", width = 1280, height = 720, now = 1L)
        val path = VectorObject.PathObject("p", points = listOf(PathPoint(Vec2(1f, 2f)), PathPoint(Vec2(30f, 40f))))
        val scene = original.activeScene.copy(
            layers = listOf(original.activeScene.layers[0].copy(cels = listOf(DrawingCel(0, 2, listOf(path))))),
            tracks = listOf(AnimationTrack("t", "p", AnimatableProperty.POSITION_X, listOf(Keyframe(0, 0f), Keyframe(24, 100f)))),
            rigs = listOf(CharacterRig("r", bones = listOf(Bone("b", "Root", start = Vec2.ZERO)))),
        )
        val asset = AssetMetadata("a", AssetKind.IMAGE, "pixel.png", "image/png", "assets/a/pixel.png", 1, 1)
        val project = original.updateScene(scene).copy(assets = listOf(asset))
        val decoded = codec.read(codec.encode(project, mapOf("a" to byteArrayOf(1, 2, 3, 4))).inputStream())
        assertEquals(project, decoded.project)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), decoded.assets["a"])
        assertEquals(path, decoded.project.activeScene.layers[0].cels[0].objects[0])
    }

    @Test(expected = ProjectFormatException::class)
    fun rejectsForeignArchive() {
        codec.read("not a zip".byteInputStream())
    }

    @Test fun deterministicTimingSurvivesSerialization() {
        val project = ProjectFactory.create("Timing", fps = 30, durationFrames = 91, now = 5L)
        val decoded = codec.read(codec.encode(project).inputStream()).project
        assertEquals(30, decoded.fps)
        assertEquals(91, decoded.durationFrames)
        assertEquals(5L, decoded.createdAt)
    }
}
