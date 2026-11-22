package app.yeschef

import app.yeschef.data.cook.MealRecipeIn
import app.yeschef.data.cook.MealStepIn
import app.yeschef.data.cook.buildMealTimeline
import app.yeschef.data.cook.parseRecipeTime
import app.yeschef.data.cook.planReminders
import app.yeschef.data.groupMeals
import app.yeschef.data.local.MealPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealTimelineTest {
    @Test fun parsesMealieTimeText() {
        assertEquals(5400L, parseRecipeTime("1 hour 30 minutes"))
        assertEquals(2700L, parseRecipeTime("45 min"))
        assertEquals(5400L, parseRecipeTime("PT1H30M"))
        assertEquals(2400L, parseRecipeTime("40"))
        assertEquals(4500L, parseRecipeTime("1:15"))
        assertNull(parseRecipeTime(""))
        assertNull(parseRecipeTime("a while"))
    }

    private fun steps(vararg text: String) = text.map { MealStepIn(it) }

    @Test fun recipesFinishTogether() {
        val roast = MealRecipeIn("Roast chicken", "15 minutes", "1 hour 15 minutes", null,
            steps("Season the chicken.", "Roast for 75 minutes.", "Rest, then carve."))
        val beans = MealRecipeIn("Green beans", "5 minutes", "10 minutes", null,
            steps("Trim the beans.", "Sauté for 10 minutes."))
        val t = buildMealTimeline(listOf(roast, beans))

        assertEquals(5400L, t.recipes[0].startsBefore)
        assertEquals(900L, t.recipes[1].startsBefore)
        assertEquals(5400L, t.leadSeconds)
        // Chicken starts first; the beans slot in near the end.
        assertEquals(0 to 0, t.steps.first().let { it.recipeIndex to it.stepIndex })
        val order = t.steps.map { it.recipeIndex to it.stepIndex }
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 0 to 2), order)
        // The 75 minute roast keeps its time: it starts 90 min out and the rest begins 15 min out.
        val roastStep = t.steps.first { it.recipeIndex == 0 && it.stepIndex == 1 }
        val restStep = t.steps.first { it.recipeIndex == 0 && it.stepIndex == 2 }
        assertEquals(75 * 60L, roastStep.startsBefore - restStep.startsBefore)
    }

    @Test fun estimatesRecipesWithNoTimes() {
        val salad = MealRecipeIn("Salad", null, null, null, steps("Chop.", "Toss.", "Dress."))
        val rice = MealRecipeIn("Rice", null, null, null, steps("Rinse.", "Simmer 18 minutes."))
        val t = buildMealTimeline(listOf(salad, rice))
        assertFalse(t.recipes[0].timesKnown)
        assertEquals(15 * 60L, t.recipes[0].totalSeconds)
        assertEquals(23 * 60L, t.recipes[1].totalSeconds)
    }

    @Test fun squeezesStepsThatOverrunTheStatedTime() {
        val r = MealRecipeIn("Quick", null, null, "20 minutes", steps("Boil 15 minutes.", "Bake 15 minutes."))
        val t = buildMealTimeline(listOf(r))
        assertTrue(t.recipes[0].timesKnown)
        assertEquals(1200L, t.steps[0].startsBefore)
        assertEquals(600L, t.steps[1].startsBefore)
    }

    @Test fun remindsAfterLongQuietStretches() {
        val pork = MealRecipeIn("Pulled pork", "15 minutes", "8 hours", null,
            steps("Rub the pork and put it in the slow cooker.", "Cook on low for 8 hours.", "Shred with two forks."))
        val rice = MealRecipeIn("Rice", null, null, "25 minutes", steps("Rinse the rice.", "Simmer for 20 minutes."))
        val t = buildMealTimeline(listOf(pork, rice))
        val ready = 100_000_000L
        val start = ready - t.leadSeconds * 1000
        val r = planReminders(t, listOf(pork, rice), ready, now = start - 1, clock = { "T" + (ready - it) / 60_000 })
        // Pork start, then after 8 quiet hours the rice, then shredding.
        assertEquals(listOf("T495 · Start Pulled pork", "T25 · Start Rice"), r.take(2).map { it.title })
        assertEquals("Start Rice: Rinse the rice", r[1].lines.single())
        assertTrue(r.any { it.lines.any { l -> l.startsWith("Pulled pork, step 3") } })
        // Nothing for steps already past.
        assertEquals(r.size - 1, planReminders(t, listOf(pork, rice), ready, now = start + 1, clock = { "" }).size)
    }

    private fun e(id: Int, date: String, type: String, slug: String?) =
        MealPlanEntity(id, date, type, null, null, slug?.let { "id-$it" }, slug, slug)

    @Test fun groupsMainsAndSides() {
        val groups = groupMeals(
            listOf(
                e(1, "2026-11-23", "dinner", "steak"),
                e(2, "2026-11-23", "side", "potatoes"),
                e(3, "2026-11-23", "dinner", "salad"),
                e(4, "2026-11-23", "breakfast", "eggs"),
                e(5, "2026-11-24", "side", "slaw"),
            ),
        )
        assertEquals(listOf("breakfast", "dinner", "side"), groups.map { it.entryType })
        val dinner = groups[1]
        assertEquals(listOf("steak", "salad", "potatoes"), dinner.slugs)
        assertTrue(dinner.cookable)
        assertFalse(groups[0].cookable)
        assertEquals("2026-11-24", groups[2].date)
    }
}
