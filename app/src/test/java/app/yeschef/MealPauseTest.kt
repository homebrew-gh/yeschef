package app.yeschef

import app.yeschef.data.cook.MealReminder
import app.yeschef.data.cook.MealSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealPauseTest {
    private val min = 60_000L
    private val session = MealSession(
        "Chicken dinner", listOf("chicken"), readyAt = 60 * min,
        reminders = listOf(MealReminder(1, 10 * min, "Rice", emptyList()), MealReminder(2, 40 * min, "Sprouts", emptyList())),
    )

    @Test fun pausingPushesTheReadyTimeBackWhilePaused() {
        val paused = session.paused(20 * min)
        assertEquals(60 * min, paused.readyAtNow(20 * min))
        assertEquals(75 * min, paused.readyAtNow(35 * min))
        // Pausing again doesn't restart the pause.
        assertEquals(20 * min, paused.paused(30 * min).pausedAt)
    }

    @Test fun resumingShiftsReadyTimeAndReminders() {
        val resumed = session.paused(20 * min).resumed(35 * min)
        assertNull(resumed.pausedAt)
        assertEquals(75 * min, resumed.readyAt)
        assertEquals(listOf(25 * min, 55 * min), resumed.reminders.map { it.at })
        assertEquals(session, session.resumed(50 * min))
    }

    @Test fun shiftingNudgesReadyTimeAndReminders() {
        val later = session.shifted(5 * min)
        assertEquals(65 * min, later.readyAt)
        assertEquals(listOf(15 * min, 45 * min), later.reminders.map { it.at })
        val earlier = session.shifted(-5 * min)
        assertEquals(55 * min, earlier.readyAt)
        assertEquals(listOf(5 * min, 35 * min), earlier.reminders.map { it.at })
    }
}
