package app.yeschef

import app.yeschef.data.cook.CookNote
import app.yeschef.data.cook.CookNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookNotesTest {
    private val clock: (Long) -> String = { "6:${(it / 60_000).toString().padStart(2, '0')} PM" }

    @Test fun messageListsNotesInOrderWithTheirSteps() {
        val notes = listOf(
            CookNote(at = 42 * 60_000L, text = "Needed 2 more minutes a side ", step = 3, stepLabel = "Sear the steak"),
            CookNote(at = 10 * 60_000L, text = "Used shallots instead of onion"),
            CookNote(at = 50 * 60_000L, text = "Too salty", step = 5),
            CookNote(at = 55 * 60_000L, text = "  "),
        )
        assertEquals(
            "- 6:10 PM: Used shallots instead of onion\n" +
                "- 6:42 PM, step 3 (Sear the steak): Needed 2 more minutes a side\n" +
                "- 6:50 PM, step 5: Too salty",
            CookNotes.message(notes, clock),
        )
    }

    @Test fun stepLabelPrefersTheTitleElseTheFirstSentence() {
        assertEquals("Make the sauce", CookNotes.stepLabel("Make the sauce", "Melt butter."))
        assertEquals("Sear the steak", CookNotes.stepLabel(null, "Sear the steak. Rest it for 5 minutes."))
        assertEquals(
            "Whisk the eggs, sugar and vanilla in a…",
            CookNotes.stepLabel("  ", "Whisk the eggs, sugar and vanilla in a large bowl until pale"),
        )
        assertNull(CookNotes.stepLabel(null, "350°"))
    }

    @Test fun stepInViewIsTheFirstStepMostlyOnScreen() {
        // Step 0 scrolled mostly off the top, step 1 fully in view.
        assertEquals(1, CookNotes.stepInView(listOf(Triple(0, -300, 400), Triple(1, 100, 300)), 0))
        // Step 0 still half in view.
        assertEquals(0, CookNotes.stepInView(listOf(Triple(0, -150, 400), Triple(1, 250, 300)), 0))
        // Only the last step's tail is left on screen.
        assertEquals(4, CookNotes.stepInView(listOf(Triple(4, -500, 600)), 0))
        assertNull(CookNotes.stepInView(emptyList(), 0))
    }

    @Test fun messagePrependsScaleAndMealContextLines() {
        val notes = listOf(CookNote(at = 42 * 60_000L, text = "Needed 5 more minutes", step = 2, stepLabel = "Bake"))
        val context = listOfNotNull(
            CookNotes.scaleContext(1.5, 4.0),
            CookNotes.mealContext(listOf("Garlic Mashed Potatoes", "Roasted Asparagus")),
        )
        assertEquals(
            "Cooked at 6 servings (×1½)\n" +
                "Cooked with: Garlic Mashed Potatoes, Roasted Asparagus\n" +
                "- 6:42 PM, step 2 (Bake): Needed 5 more minutes",
            CookNotes.message(notes, clock, context),
        )
        assertNull(CookNotes.scaleContext(1.0, 4.0))
        assertEquals("Cooked at ×2", CookNotes.scaleContext(2.0, null))
        assertNull(CookNotes.mealContext(emptyList()))
    }
}
