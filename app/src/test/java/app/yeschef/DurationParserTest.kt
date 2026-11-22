package app.yeschef

import app.yeschef.data.cook.detectDurations
import app.yeschef.data.cook.formatClock
import app.yeschef.data.cook.formatShort
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationParserTest {
    private fun secs(text: String) = detectDurations(text).map { it.seconds }

    @Test fun simple() = assertEquals(listOf(1500L), secs("Bake for 25 minutes until golden."))
    @Test fun compound() = assertEquals(listOf(5400L), secs("Simmer 1 hour 30 minutes, stirring."))
    @Test fun compoundWithAnd() = assertEquals(listOf(8100L), secs("Roast 2 hrs and 15 mins"))
    @Test fun rangeUsesLowerBound() {
        val d = detectDurations("Cook 10-12 minutes per side")
        assertEquals(listOf(600L), d.map { it.seconds })
        assertEquals("10-12 minutes", d.single().text)
    }
    @Test fun words() = assertEquals(listOf(1800L, 5400L), secs("Rest for half an hour, then bake 1½ hours"))
    @Test fun fractions() = assertEquals(listOf(90L * 60), secs("Braise 1 1/2 hours"))
    @Test fun seconds() = assertEquals(listOf(30L), secs("Microwave about 30 secs"))
    @Test fun ignoresTemperaturesAndQuantities() = assertEquals(emptyList<Long>(), secs("Preheat to 350 degrees. Add 2 cups flour."))
    @Test fun multipleSeparate() = assertEquals(listOf(300L, 1200L), secs("Sear 5 min. Transfer to oven for 20 minutes."))
    @Test fun noFalseWordMatch() = assertEquals(emptyList<Long>(), secs("Minutely dice the shallots"))

    @Test fun formatting() {
        assertEquals("1:05:00", formatClock(3900))
        assertEquals("4:07", formatClock(247))
        assertEquals("1 hr 15 min", formatShort(4500))
        assertEquals("30 sec", formatShort(30))
    }
}
