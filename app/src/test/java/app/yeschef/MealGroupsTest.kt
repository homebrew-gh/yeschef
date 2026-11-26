package app.yeschef

import app.yeschef.data.groupMeals
import app.yeschef.data.local.MealPlanEntity
import app.yeschef.data.mainsAndSides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealGroupsTest {

    private fun entry(id: Int, date: String, type: String, slug: String, name: String) =
        MealPlanEntity(id, date, type, null, null, "r-$id", slug, name)

    @Test
    fun addingSideToSingleDishMealMakesItCookableTogether() {
        val date = "2026-11-26"
        val main = entry(1, date, "dinner", "classic-lasagna", "Classic lasagna")
        val before = groupMeals(listOf(main)).single()
        assertFalse(before.cookable)

        val addedSide = entry(-10, date, "dinner", "garlic-green-beans", "Garlic green beans")
        val after = groupMeals(listOf(main, addedSide)).single()
        val (mains, sides) = after.mainsAndSides { it == "garlic-green-beans" }
        val ordered = after.copy(entries = mains + sides)

        assertTrue(ordered.cookable)
        assertEquals(listOf("classic-lasagna", "garlic-green-beans"), ordered.slugs)
        assertEquals(listOf("classic-lasagna"), mains.map { it.recipeSlug })
        assertEquals(listOf("garlic-green-beans"), sides.map { it.recipeSlug })
    }

    @Test
    fun addingExtraDishToLunchStaysWithLunchNotDinner() {
        val date = "2026-11-26"
        val lunch = entry(1, date, "lunch", "grilled-cheese", "Grilled cheese")
        val dinner = entry(2, date, "dinner", "classic-lasagna", "Classic lasagna")
        val addedSoup = entry(-11, date, "lunch", "tomato-soup", "Tomato soup")

        val groups = groupMeals(listOf(lunch, dinner, addedSoup))
        val lunchGroup = groups.first { it.entryType == "lunch" }
        val dinnerGroup = groups.first { it.entryType == "dinner" }

        assertTrue(lunchGroup.cookable)
        assertEquals(listOf("grilled-cheese", "tomato-soup"), lunchGroup.slugs)
        assertFalse(dinnerGroup.cookable)
        assertEquals(listOf("classic-lasagna"), dinnerGroup.slugs)
    }
}
