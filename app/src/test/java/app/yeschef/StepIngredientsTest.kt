package app.yeschef

import app.yeschef.data.cook.IngredientLine
import app.yeschef.data.cook.StepLine
import app.yeschef.data.cook.ingredientsForSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class StepIngredientsTest {
    private fun line(text: String, ref: String? = null, food: String? = null) = IngredientLine(ref, food, text)
    private fun step(text: String, vararg refs: String) = StepLine(text, refs.toList())

    private val lasagna = listOf(
        line("1 lb ground beef"),
        line("1 yellow onion, diced"),
        line("24 oz marinara sauce"),
        line("16 oz ricotta"),
        line("2 cups shredded mozzarella"),
        line("12 lasagna noodles"),
    )

    @Test fun matchesByNameInStepText() {
        val result = ingredientsForSteps(
            lasagna,
            listOf(
                step("Brown the beef and onion in a large pan, about 8 minutes. Stir in the marinara and simmer."),
                step("Meanwhile, boil the noodles for 10-12 minutes until al dente, then drain."),
                step("Layer sauce, noodles, ricotta and mozzarella in a 9x13 dish."),
                step("Bake covered at 375°F for 25 minutes."),
            ),
        )
        assertEquals(listOf(listOf(0, 1, 2), listOf(5), listOf(2, 3, 4, 5), emptyList<Int>()), result)
    }

    @Test fun mealieLinksWinOverText() {
        val ings = listOf(line("1 lb ground beef", ref = "a"), line("1 onion", ref = "b"))
        val result = ingredientsForSteps(ings, listOf(step("Brown the beef and onion.", "b")))
        assertEquals(listOf(listOf(1)), result)
    }

    @Test fun unknownLinksFallBackToText() {
        val ings = listOf(line("1 lb ground beef", ref = "a"))
        assertEquals(listOf(listOf(0)), ingredientsForSteps(ings, listOf(step("Brown the beef.", "gone"))))
    }

    @Test fun pluralsAndParsedFoodNames() {
        val ings = listOf(
            line("3 ripe tomatoes"),
            line("2 cups berries"),
            line("2 large eggs, beaten", food = "egg"),
        )
        val result = ingredientsForSteps(ings, listOf(step("Whisk the egg, then fold in the berry mix and a tomato.")))
        assertEquals(listOf(listOf(0, 1, 2)), result)
    }

    @Test fun sharedHeadNounNeedsTheWordBefore() {
        val ings = listOf(line("1 red bell pepper, sliced"), line("1 tsp black pepper"))
        val result = ingredientsForSteps(
            ings,
            listOf(step("Saute the bell peppers."), step("Season with salt and black pepper."), step("Add pepper.")),
        )
        assertEquals(listOf(listOf(0), listOf(1), emptyList<Int>()), result)
    }

    @Test fun headingsAndAmountOnlyLinesNeverMatch() {
        val ings = listOf(IngredientLine(null, null, "For the sauce", isHeadingOnly = true), line("2 tbsp"))
        assertEquals(listOf(emptyList<Int>()), ingredientsForSteps(ings, listOf(step("Make the sauce in 2 tbsp of time."))))
    }
}
