package app.yeschef

import app.yeschef.data.cook.timerStepLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerLabelTest {
    private val step1 = "Brown the beef and onion in a large pan, about 8 minutes. Stir in the marinara and simmer for 15 minutes."
    private val step4 = "Bake covered at 375°F for 25 minutes, then uncovered for 20 minutes more. Rest for 15 minutes before slicing."

    @Test fun wordsBeforeTheTime() {
        assertEquals("Step 1 · Stir in the marinara and simmer", timerStepLabel(1, null, step1, "15 minutes"))
        assertEquals("Step 4 · Bake covered at 375°F", timerStepLabel(4, null, step4, "25 minutes"))
        assertEquals("Step 4 · Uncovered", timerStepLabel(4, null, step4, "20 minutes"))
        assertEquals("Step 4 · Rest", timerStepLabel(4, null, step4, "15 minutes"))
        assertEquals("Step 2 · Boil the noodles", timerStepLabel(2, null, "Meanwhile, boil the noodles for 10-12 minutes until al dente.", "10-12 minutes"))
    }

    @Test fun timeOnItsOwnUsesTheClauseBefore() {
        assertEquals("Step 1 · Brown the beef and onion in a large pan", timerStepLabel(1, null, step1, "8 minutes"))
    }

    @Test fun mealieTitleWins() {
        assertEquals("Step 3 · Make the sauce", timerStepLabel(3, "Make the sauce", step1, "15 minutes"))
    }

    @Test fun stepDurationWithoutTextUsesFirstClause() {
        assertEquals("Step 2 · Let the dough rise", timerStepLabel(2, null, "Let the dough rise, covered, somewhere warm.", ""))
    }

    @Test fun longLabelsAreShortened() {
        val label = timerStepLabel(5, null, "Gently fold the whipped egg whites into the chocolate mixture and chill for 2 hours.", "2 hours")
        assertEquals("Step 5 · Gently fold the whipped egg whites into…", label)
    }

    @Test fun nothingUsableFallsBackToStepNumber() {
        assertEquals("Step 6", timerStepLabel(6, null, "15 minutes.", "15 minutes"))
    }
}
