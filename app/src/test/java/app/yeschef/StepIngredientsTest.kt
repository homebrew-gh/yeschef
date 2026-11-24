package app.yeschef

import app.yeschef.data.cook.IngredientLine
import app.yeschef.data.cook.StepLine
import app.yeschef.data.cook.firstSteps
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

    // Instant Pot beef and broccoli: the broth only goes in at step 2.
    @Test fun sourceFoodIsNotTheThingMadeFromIt() {
        val ings = listOf(
            line("1 1/2 lb flank steak, sliced thin", food = "flank steak"),
            line("1/2 cup soy sauce", food = "soy sauce"),
            line("1/2 cup beef broth", food = "beef broth"),
            line("1 tablespoon avocado oil", food = "avocado oil"),
            line("4 cloves garlic minced", food = "garlic"),
            line("1 tablespoon ginger grated", food = "ginger"),
            line("1 tsp garlic powder"),
        )
        val result = ingredientsForSteps(
            ings,
            listOf(
                step("Set the Instant Pot to Saute, heat the avocado oil, and sear the beef in 2 batches, 1 min each."),
                step("Add garlic and ginger 30 seconds, then soy and broth; scrape the bottom well. Return the beef."),
            ),
        )
        assertEquals(listOf(listOf(3), listOf(1, 2, 4, 5)), result)
    }

    @Test fun genericHeadNeedsTheWordBefore() {
        val ings = listOf(line("1 tbsp taco seasoning"), line("1 box cake mix"), line("2 tbsp lime juice"))
        val result = ingredientsForSteps(
            ings,
            listOf(
                step("Cook until the juices run clear, mix well and adjust the seasoning."),
                step("Add the taco seasoning and the cake mix."),
                step("Squeeze in the lime."),
            ),
        )
        assertEquals(listOf(emptyList(), listOf(0, 1), listOf(2)), result)
    }

    @Test fun bracketedAmountsAndWrongFoodLinks() {
        val ings = listOf(
            line("1 (15 oz) can black beans, drained"),
            line("2 tbsp simple syrup", food = "maple syrup"),
            line("2 large eggs, beaten", food = "egg"),
            line("2 cups baby spinach leaves", food = "spinach"),
        )
        val result = ingredientsForSteps(
            ings,
            listOf(step("Warm the beans."), step("Drizzle with maple."), step("Whisk the egg, then wilt the spinach.")),
        )
        assertEquals(listOf(listOf(0), emptyList(), listOf(2, 3)), result)
    }

    @Test fun plainSaltNextToKosherSalt() {
        val ings = listOf(line("1 tsp salt"), line("1 tbsp kosher salt"))
        val result = ingredientsForSteps(ings, listOf(step("Season with salt."), step("Rub with the kosher salt.")))
        assertEquals(listOf(listOf(0), listOf(1)), result)
    }

    // Griddle steak fajitas: the steak's amount shows once, on the first step that uses it.
    @Test fun firstStepPerIngredient() {
        val perStep = listOf(listOf(1), listOf(0, 1), emptyList(), listOf(0, 2))
        assertEquals(mapOf(1 to 0, 0 to 1, 2 to 3), firstSteps(perStep))
    }
}
