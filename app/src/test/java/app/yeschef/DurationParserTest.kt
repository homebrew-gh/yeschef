package app.yeschef

import app.yeschef.data.cook.CookTimer
import app.yeschef.data.cook.detectDurations
import app.yeschef.data.cook.formatClock
import app.yeschef.data.cook.formatRangeShort
import app.yeschef.data.cook.formatShort
import app.yeschef.data.cook.formatTimerChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationParserTest {
    private fun secs(text: String) = detectDurations(text).map { it.seconds }

    @Test fun simple() = assertEquals(listOf(1500L), secs("Bake for 25 minutes until golden."))
    @Test fun compound() = assertEquals(listOf(5400L), secs("Simmer 1 hour 30 minutes, stirring."))
    @Test fun compoundWithAnd() = assertEquals(listOf(8100L), secs("Roast 2 hrs and 15 mins"))
    @Test fun rangeUsesLowerBoundAndPreservesUpperBound() {
        val d = detectDurations("Cook 10-12 minutes per side")
        assertEquals(listOf(600L), d.map { it.seconds })
        assertEquals(720L, d.single().maxSeconds)
        assertEquals("10-12 minutes", d.single().text)
        assertEquals("10–12 min", formatTimerChip(d.single()))
    }
    @Test fun words() = assertEquals(listOf(1800L, 5400L), secs("Rest for half an hour, then bake 1½ hours"))
    @Test fun fractions() = assertEquals(listOf(90L * 60), secs("Braise 1 1/2 hours"))
    @Test fun seconds() = assertEquals(listOf(30L), secs("Microwave about 30 secs"))
    @Test fun ignoresTemperaturesAndQuantities() = assertEquals(emptyList<Long>(), secs("Preheat to 350 degrees. Add 2 cups flour."))
    @Test fun multipleSeparate() = assertEquals(listOf(300L, 1200L), secs("Sear 5 min. Transfer to oven for 20 minutes."))
    @Test fun sequentialMoreNotFolded() = assertEquals(
        listOf(1500L, 900L),
        secs("Bake 25 minutes covered, then uncover and bake 15 minutes more."),
    )
    @Test fun noFalseWordMatch() = assertEquals(emptyList<Long>(), secs("Minutely dice the shallots"))

    @Test fun nestedFoilAtLeastFoldsIntoSingleTimerWithCheckpoint() {
        val found = detectDurations("Pour into the baking pan and bake for 40-48min, covering with foil for atleast 15min")
        assertEquals(1, found.size)
        val d = found.single()
        assertEquals(40L * 60, d.seconds)
        assertEquals(48L * 60, d.maxSeconds)
        assertNotNull(d.checkpoint)
        assertEquals(25L * 60, d.checkpoint!!.atSeconds)
        assertEquals("Cover with foil", d.checkpoint!!.label)
        assertEquals("40–48 min · alert @ 25 min", formatTimerChip(d))
    }

    @Test fun nestedLastMinutesFoldsAsRemainingCheckpoint() {
        val found = detectDurations("Bake for 45 minutes, uncovering for the last 10 minutes until golden.")
        assertEquals(1, found.size)
        val d = found.single()
        assertEquals(45L * 60, d.seconds)
        assertNull(d.maxSeconds)
        assertEquals(35L * 60, d.checkpoint?.atSeconds)
        assertEquals("Uncover", d.checkpoint?.label)
    }

    @Test fun nestedAfterMinutesFoldsAsElapsedCheckpoint() {
        val found = detectDurations("Bake for 50 minutes, tenting with foil after 30 minutes if browning too fast.")
        assertEquals(1, found.size)
        val d = found.single()
        assertEquals(50L * 60, d.seconds)
        assertEquals(30L * 60, d.checkpoint?.atSeconds)
        assertEquals("Cover with foil", d.checkpoint?.label)
    }

    @Test fun halfwayCheckpointDetected() {
        val found = detectDurations("Roast for 30 minutes, flipping halfway through.")
        assertEquals(1, found.size)
        val d = found.single()
        assertEquals(30L * 60, d.seconds)
        assertEquals(15L * 60, d.checkpoint?.atSeconds)
        assertEquals("Flip halfway", d.checkpoint?.label)
    }

    @Test fun cookTimerRangeAndCheckpointHelpers() {
        val now = 1_000_000L
        val t = CookTimer(
            id = 1,
            label = "Bake",
            durationMs = 40 * 60_000L,
            endAt = now + 40 * 60_000L,
            maxDurationMs = 48 * 60_000L,
            checkpointLabel = "Cover with foil",
            checkpointAt = now + 25 * 60_000L,
        )
        assertEquals(8 * 60_000L, t.remainingRangeMs)
        assertTrue(t.hasPendingCheckpoint)
        assertEquals(25 * 60_000L, t.checkpointRemainingMs(now))
    }

    @Test fun formatting() {
        assertEquals("1:05:00", formatClock(3900))
        assertEquals("4:07", formatClock(247))
        assertEquals("1 hr 15 min", formatShort(4500))
        assertEquals("30 sec", formatShort(30))
        assertEquals("40–48 min", formatRangeShort(2400, 2880))
        assertEquals("50 min–1 hr 5 min", formatRangeShort(3000, 3900))
    }
}
