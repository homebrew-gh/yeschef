package app.yeschef

import app.yeschef.data.recipeShareText
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.NamedDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.StepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeShareTest {
    private val chili = RecipeDetailDto(
        id = "r1", slug = "chili", name = "Chili",
        description = "Weeknight chili",
        prepTime = "15 minutes", totalTime = "1 hour",
        recipeServings = 4.0, recipeYield = "4 servings",
        orgURL = "https://example.com/chili",
        recipeIngredient = listOf(
            IngredientDto(title = "For the chili", display = ""),
            IngredientDto(display = "1 lb ground beef", food = NamedDto("f1", "ground beef")),
            IngredientDto(display = "2 cans beans", food = NamedDto("f2", "beans")),
        ),
        recipeInstructions = listOf(
            StepDto(text = "Brown the beef."),
            StepDto(title = "Finish", text = "Add the beans and simmer."),
            StepDto(text = " "),
        ),
    )

    @Test fun plainText() {
        assertEquals(
            """
            Chili

            Weeknight chili
            Makes: 4 servings  ·  Prep: 15 minutes  ·  Total: 1 hour
            Source: https://example.com/chili

            INGREDIENTS
            For the chili
            • 1 lb ground beef
            • 2 cans beans

            STEPS
            1. Brown the beef.

            Finish
            2. Add the beans and simmer.

            """.trimIndent(),
            recipeShareText(chili),
        )
    }

    @Test fun sharesAtTheServingsShown() {
        val text = recipeShareText(chili, scale = 2.0)
        assertTrue(text, "Makes: 8 servings" in text)
        assertTrue(text, "• 2 lb ground beef" in text)
        assertTrue(text, "• 4 cans beans" in text)
    }

    @Test fun skipsEmptySections() {
        assertEquals("Toast\n", recipeShareText(RecipeDetailDto(id = "t", slug = "toast", name = "Toast")))
    }
}
