package app.yeschef.data

import app.yeschef.data.local.MealPlanEntity

/** One meal on one day: a dinner and its sides, say. Recipes in it can be cooked together. */
data class MealGroup(val date: String, val entryType: String, val entries: List<MealPlanEntity>) {
    /** Recipe slugs in cooking order, no repeats. */
    val slugs: List<String> get() = entries.mapNotNull { it.recipeSlug }.distinct()
    val cookable: Boolean get() = slugs.size >= 2
}

private val mealOrder = listOf("breakfast", "lunch", "dinner", "side", "snack", "drink", "dessert")
private val mains = listOf("dinner", "lunch", "breakfast")

/**
 * Groups a day's meal plan into meals. Entries of the same type on the same date are one
 * meal, so a main and its sides planned as "dinner" go together. Entries planned with
 * Mealie's "side" type join that day's dinner (or lunch, or breakfast) when there is one.
 */
fun groupMeals(entries: List<MealPlanEntity>): List<MealGroup> =
    entries.groupBy { it.date }.toSortedMap().flatMap { (date, day) ->
        val types = day.map { it.entryType.lowercase() }.toSet()
        val sideHome = mains.firstOrNull { it in types }
        day.groupBy { e ->
            val t = e.entryType.lowercase()
            if (t == "side" && sideHome != null) sideHome else t
        }.map { (type, group) ->
            // Mains before sides; otherwise keep the plan's order.
            MealGroup(date, type, group.sortedBy { if (it.entryType.equals("side", true) && type != "side") 1 else 0 })
        }.sortedBy { g -> mealOrder.indexOf(g.entryType).let { if (it < 0) 99 else it } }
    }

/**
 * Splits a meal into its main dish(es) and the sides that go with it. A side is an entry
 * planned with Mealie's "side" type or a recipe in the Sides chapter ([isSide], by slug).
 * If everything looks like a side, the first one is treated as the main.
 */
fun MealGroup.mainsAndSides(isSide: (String) -> Boolean): Pair<List<MealPlanEntity>, List<MealPlanEntity>> {
    val (sides, mains) = entries.partition { e ->
        (e.entryType.equals("side", true) && !entryType.equals("side", true)) || (e.recipeSlug?.let(isSide) ?: false)
    }
    return if (mains.isEmpty()) entries.take(1) to entries.drop(1) else mains to sides
}
