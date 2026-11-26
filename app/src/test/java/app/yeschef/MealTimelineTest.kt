package app.yeschef

import app.yeschef.data.cook.MealRecipeIn
import app.yeschef.data.cook.MealStepIn
import app.yeschef.data.cook.buildMealTimeline
import app.yeschef.data.cook.parseRecipeTime
import app.yeschef.data.cook.planReminders
import app.yeschef.data.groupMeals
import app.yeschef.data.mainsAndSides
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
        assertEquals(0L, parseRecipeTime("0 minutes"))
        assertEquals(0L, parseRecipeTime("PT0M"))
    }

    @Test fun noCookRecipeGoesIntoUntimedPrep() {
        val sauce = MealRecipeIn("Yogurt-dill sauce", "5 minutes", "0 minutes", null, steps("Mix everything.", "Chill."))
        val t = buildMealTimeline(listOf(sauce))
        assertTrue(t.recipes[0].timesKnown)
        assertEquals(0L, t.recipes[0].totalSeconds)
        assertEquals(300L, t.recipes[0].prepSeconds)
        assertTrue(t.steps.isEmpty())
        assertEquals(listOf(0 to 0, 0 to 1), t.prepSteps.map { it.recipeIndex to it.stepIndex })
    }

    private fun steps(vararg text: String) = text.map { MealStepIn(it) }

    @Test fun recipesSeparatePrepAndFinishTimedCookingTogether() {
        val roast = MealRecipeIn("Roast chicken", "15 minutes", "1 hour 15 minutes", null,
            steps("Season the chicken.", "Roast at 400°F for 75 minutes.", "Rest, then carve."))
        val beans = MealRecipeIn("Green beans", "5 minutes", "10 minutes", null,
            steps("Trim the beans.", "Sauté for 10 minutes."))
        val t = buildMealTimeline(listOf(roast, beans))

        // Leading untimed steps move into untimed Prep & Setup.
        assertEquals(listOf(0 to 0, 1 to 0), t.prepSteps.map { it.recipeIndex to it.stepIndex })
        // Roast phase: 75 min roast + 5 min generous carve cushion = 80 min; beans: 10 min sauté.
        assertEquals(80 * 60L, t.recipes[0].startsBefore)
        assertEquals(10 * 60L, t.recipes[1].startsBefore)
        assertEquals(80 * 60L, t.leadSeconds)
        val order = t.steps.map { it.recipeIndex to it.stepIndex }
        assertEquals(listOf(0 to 1, 1 to 1, 0 to 2), order)
        val roastStep = t.steps.first { it.recipeIndex == 0 && it.stepIndex == 1 }
        val restStep = t.steps.first { it.recipeIndex == 0 && it.stepIndex == 2 }
        assertEquals(75 * 60L, roastStep.startsBefore - restStep.startsBefore)
    }

    @Test fun estimatesRecipesWithNoTimes() {
        val salad = MealRecipeIn("Salad", null, null, null, steps("Chop.", "Toss.", "Dress."))
        val rice = MealRecipeIn("Rice", null, null, null, steps("Rinse.", "Simmer 18 minutes."))
        val t = buildMealTimeline(listOf(salad, rice))
        assertFalse(t.recipes[0].timesKnown)
        assertEquals(0L, t.recipes[0].totalSeconds)
        assertEquals(15 * 60L, t.recipes[0].prepSeconds)
        assertEquals(18 * 60L, t.recipes[1].totalSeconds)
    }

    @Test fun neverSqueezesExplicitStepTimersAndSumsMultiDurationSteps() {
        val r = MealRecipeIn(
            "Lasagna",
            null,
            null,
            "20 minutes",
            steps("Boil 15 minutes.", "Bake covered 25 minutes, then uncovered for 20 minutes. Rest 15 minutes."),
        )
        val t = buildMealTimeline(listOf(r))
        assertTrue(t.recipes[0].timesKnown)
        // 15 min + (25 + 20 + 15 = 60 min) = 75 min — never shrunk down to the understated 20 min total.
        assertEquals(75 * 60L, t.recipes[0].totalSeconds)
        assertEquals(75 * 60L, t.steps[0].startsBefore)
        assertEquals(60 * 60L, t.steps[1].startsBefore)
    }

    @Test fun detectsOvenTemperaturesConflictsAndBoilWaterCues() {
        val chicken = MealRecipeIn("Roast chicken", null, "40 minutes", null, steps("Roast at 375°F for 40 minutes."))
        val potatoes = MealRecipeIn(
            "Smashed potatoes",
            null,
            "45 minutes",
            null,
            steps("Boil the baby potatoes in salted water for 20 minutes.", "Roast at 450°F for 25 minutes."),
        )
        val conflict = buildMealTimeline(listOf(chicken, potatoes))
        assertEquals(listOf("375°F", "450°F"), conflict.ovenRequirements.map { it.tempLabel })
        assertTrue(conflict.hasOvenConflict)
        assertEquals(listOf("Smashed potatoes"), conflict.boilWaterDishes)

        val sameOven = buildMealTimeline(
            listOf(
                MealRecipeIn("A", null, "20 min", null, steps("Bake at 400°F for 20 minutes.")),
                MealRecipeIn("B", null, "15 min", null, steps("Roast at 400°F for 15 minutes.")),
            ),
        )
        assertFalse(sameOven.hasOvenConflict)
    }

    @Test fun remindsAfterLongQuietStretches() {
        val pork = MealRecipeIn("Pulled pork", "15 minutes", "8 hours", null,
            steps("Rub the pork and put it in the slow cooker.", "Cook on low for 8 hours.", "Shred with two forks."))
        val rice = MealRecipeIn("Rice", null, null, "25 minutes", steps("Rinse the rice.", "Simmer for 20 minutes."))
        val t = buildMealTimeline(listOf(pork, rice))
        val ready = 100_000_000L
        val start = ready - t.leadSeconds * 1000
        val r = planReminders(t, listOf(pork, rice), ready, now = start - 1, clock = { "T" + (ready - it) / 60_000 })
        // Pork timed start (8 hr + 5 min shred = 485 min), then after 8 quiet hours the rice (25 min), then shredding.
        assertEquals(listOf("T485 · Start Pulled pork", "T25 · Start Rice"), r.take(2).map { it.title })
        assertEquals("Start Rice: Simmer for 20 minutes", r[1].lines.single())
        assertTrue(r.any { it.lines.any { l -> l.startsWith("Pulled pork, step 3") } })
        // Nothing for steps already past.
        assertEquals(r.size - 1, planReminders(t, listOf(pork, rice), ready, now = start + 1, clock = { "" }).size)
    }

    @Test fun remindsForDishAfterOwnQuietStretchEvenWhenSideStartsMidway() {
        val roast = MealRecipeIn("Roast chicken", "5 minutes", "25 minutes", null,
            steps("Season chicken.", "Roast for 25 minutes.", "Carve and serve."))
        val side = MealRecipeIn("Green beans", null, null, "10 minutes",
            steps("Sauté beans for 10 minutes."))
        val t = buildMealTimeline(listOf(roast, side))
        val ready = 100_000_000L
        val start = ready - t.leadSeconds * 1000
        val reminders = planReminders(t, listOf(roast, side), ready, now = start - 1, clock = { "T" + (ready - it) / 60_000 })
        // Roast step 3 follows a 25-minute roast even though the side starts 10 min before ready.
        assertTrue(reminders.any { it.lines.any { l -> l.startsWith("Roast chicken, step 3") } })
    }

    @Test fun classifiesPassiveVsActiveSteps() {
        val roast = MealRecipeIn("Roast chicken", "10 minutes", "30 minutes", null,
            steps("Season the chicken.", "Roast at 400°F for 25 minutes.", "Whisk constantly for 6 minutes to make gravy."))
        val t = buildMealTimeline(listOf(roast))
        assertEquals(1, t.prepSteps.size)
        assertTrue(t.steps[0].passive)
        assertFalse(t.steps[1].passive)
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

    @Test fun splitsMainFromSides() {
        val dinner = groupMeals(
            listOf(
                e(1, "2026-11-23", "dinner", "jasmine-rice"),
                e(2, "2026-11-23", "dinner", "lemon-chicken"),
                e(3, "2026-11-23", "dinner", "broccoli"),
            ),
        ).single()
        val sides = setOf("jasmine-rice", "broccoli")
        val (mains, withs) = dinner.mainsAndSides { it in sides }
        assertEquals(listOf("lemon-chicken"), mains.map { it.recipeSlug })
        assertEquals(listOf("jasmine-rice", "broccoli"), withs.map { it.recipeSlug })
        // Nothing filed as a main: the first entry leads.
        val (m2, s2) = dinner.mainsAndSides { true }
        assertEquals(listOf("jasmine-rice"), m2.map { it.recipeSlug })
        assertEquals(2, s2.size)
    }
}
