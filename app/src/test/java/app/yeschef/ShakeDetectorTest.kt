package app.yeschef

import app.yeschef.ui.recipes.ShakeDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    @Test fun singleBumpDoesNotTriggerShake() {
        val d = ShakeDetector()
        assertFalse(d.onSample(1.0f, 0))
        assertFalse(d.onSample(2.8f, 100))
        assertFalse(d.onSample(1.0f, 200))
    }

    @Test fun deliberateTwoPeakShakeTriggersOnceAndRespectsCooldown() {
        val d = ShakeDetector()
        assertFalse(d.onSample(2.4f, 1000))
        // Too close to the first peak (same motion): ignored.
        assertFalse(d.onSample(2.6f, 1050))
        // Second distinct peak in window: triggers!
        assertTrue(d.onSample(2.3f, 1200))
        // Immediately after trigger: blocked by cooldown.
        assertFalse(d.onSample(2.5f, 1400))
        assertFalse(d.onSample(2.5f, 1600))
        // After cooldown expires, a fresh two-peak shake works again.
        assertFalse(d.onSample(2.3f, 2500))
        assertTrue(d.onSample(2.4f, 2700))
    }

    @Test fun slowSpacedBumpsOutsideWindowDoNotCombine() {
        val d = ShakeDetector()
        assertFalse(d.onSample(2.5f, 1000))
        assertFalse(d.onSample(2.5f, 1800))
    }
}
