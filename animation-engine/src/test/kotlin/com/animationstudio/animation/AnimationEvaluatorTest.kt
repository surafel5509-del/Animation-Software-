package com.animationstudio.animation

import com.animationstudio.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationEvaluatorTest {
    private fun track(type: InterpolationType) = AnimationTrack(
        "track", "object", AnimatableProperty.POSITION_X,
        listOf(Keyframe(0, 0f, type), Keyframe(10, 100f)),
    )

    @Test fun linearAndBoundarySampling() {
        val track = track(InterpolationType.LINEAR)
        assertEquals(0f, AnimationEvaluator.sample(track, -2), 0f)
        assertEquals(50f, AnimationEvaluator.sample(track, 5), 0.001f)
        assertEquals(100f, AnimationEvaluator.sample(track, 50), 0f)
    }

    @Test fun holdDoesNotInterpolate() {
        assertEquals(0f, AnimationEvaluator.sample(track(InterpolationType.HOLD), 9), 0f)
    }

    @Test fun bezierHasCorrectEndpointsAndProgress() {
        assertEquals(0f, Interpolation.cubicBezier(0f, .42f, 0f, .58f, 1f), 0f)
        assertEquals(1f, Interpolation.cubicBezier(1f, .42f, 0f, .58f, 1f), 0f)
        assertEquals(.5f, Interpolation.cubicBezier(.5f, .42f, 0f, .58f, 1f), .001f)
    }

    @Test fun colorChannelsInterpolateWithoutLosingAlpha() {
        val track = AnimationTrack("c", "o", AnimatableProperty.COLOR, listOf(
            Keyframe(0, colorValue = 0xff000000.toInt()),
            Keyframe(10, colorValue = 0xffffffff.toInt()),
        ))
        assertEquals(0xff808080.toInt(), AnimationEvaluator.sampleColor(track, 5, 0))
    }
}
