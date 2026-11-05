package com.animationstudio.audio

import com.animationstudio.core.model.AudioClip
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioGainTest {
    @Test fun fadesAndMuteProduceExpectedGain() {
        val controller = AudioPlaybackController()
        val clip = AudioClip("c", "a", "Voice", volume = .8f, fadeInMs = 1000, fadeOutMs = 1000)
        assertEquals(.4f, controller.gainAt(clip, 500, 10_000), .001f)
        assertEquals(.8f, controller.gainAt(clip, 1000, 10_000), .001f)
        assertEquals(.4f, controller.gainAt(clip, 9500, 10_000), .001f)
        assertEquals(0f, controller.gainAt(clip.copy(muted = true), 1000, 10_000), 0f)
        controller.release()
    }
}
