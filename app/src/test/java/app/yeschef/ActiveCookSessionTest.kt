package app.yeschef

import app.yeschef.data.cook.ActiveCookSession
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveCookSessionTest {
    @Test fun jsonRoundTripPreservesCookProgress() {
        val session = ActiveCookSession(
            slug = "classic-lasagna",
            name = "Classic lasagna",
            checkedIngredients = setOf(0, 2, 4),
            completedSteps = setOf(0, 1),
            servings = 12.0,
            factor = 1.5,
            stepIndex = 2,
            totalSteps = 4,
        )
        val encoded = Json.encodeToString(ActiveCookSession.serializer(), session)
        val decoded = Json.decodeFromString(ActiveCookSession.serializer(), encoded)
        assertEquals(session, decoded)
    }

    @Test fun progressTextReflectsStepsDoneOrCurrentStep() {
        val fresh = ActiveCookSession(slug = "lasagna", name = "Classic lasagna", stepIndex = 0, totalSteps = 4)
        assertEquals("Step 1 of 4", fresh.progressText())

        val scrolled = fresh.copy(stepIndex = 2)
        assertEquals("Step 3 of 4", scrolled.progressText())

        val checked = scrolled.copy(completedSteps = setOf(0, 1))
        assertEquals("2 of 4 steps done", checked.progressText())

        val empty = ActiveCookSession(slug = "toast", name = "Toast", totalSteps = 0)
        assertNull(empty.progressText())
    }
}
