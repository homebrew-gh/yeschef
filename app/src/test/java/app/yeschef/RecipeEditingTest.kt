package app.yeschef

import app.yeschef.data.DraftIngredient
import app.yeschef.data.DraftStep
import app.yeschef.data.DraftTime
import app.yeschef.data.RecipeDraft
import app.yeschef.data.buildRecipePatch
import app.yeschef.data.changesFrom
import app.yeschef.data.formatMealieTime
import app.yeschef.data.parsedIngredient
import app.yeschef.data.selectIngredients
import app.yeschef.data.string
import app.yeschef.data.withChange
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.NamedDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.StepDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditingTest {
    private val recipe = RecipeDetailDto(
        id = "r1", slug = "chili", name = "Chili",
        description = "Weeknight chili",
        prepTime = "15 minutes", performTime = "1 hour", totalTime = "1 hour 15 minutes",
        recipeServings = 4.0, recipeYield = "4 servings",
        recipeIngredient = listOf(
            IngredientDto(title = "For the chili", display = "", referenceId = "h"),
            IngredientDto(display = "1 lb ground beef", food = NamedDto("f1", "ground beef"), referenceId = "a"),
            IngredientDto(display = "2 cans beans", food = NamedDto("f2", "beans"), referenceId = "b"),
        ),
        recipeInstructions = listOf(StepDto(id = "s1", text = "Brown the beef."), StepDto(id = "s2", text = "Simmer.")),
    )

    @Test fun mealieTimeText() {
        assertEquals("0 minutes", formatMealieTime(0))
        assertEquals("", formatMealieTime(null))
        assertEquals("45 minutes", formatMealieTime(45))
        assertEquals("1 hour", formatMealieTime(60))
        assertEquals("1 hour 30 minutes", formatMealieTime(90))
        assertEquals("2 hours 1 minute", formatMealieTime(121))
    }

    @Test fun draftReadsTimes() {
        val d = RecipeDraft.of(recipe)
        assertEquals(DraftTime("", "15"), d.prep)
        assertEquals(DraftTime("1", ""), d.cook)
        assertEquals(75L, d.total.totalMinutes)
        assertTrue(d.totalFollows)
        assertEquals(0L, DraftTime.of("0 minutes").totalMinutes)
        assertNull(DraftTime.of("").totalMinutes)
    }

    @Test fun headingOnlyLineBecomesTitleOfNextLine() {
        val d = RecipeDraft.of(recipe)
        assertEquals(listOf("1 lb ground beef", "2 cans beans"), d.ingredients.map { it.text })
        assertEquals("For the chili", d.ingredients[0].title)
    }

    @Test fun nothingChangedIsNull() {
        assertNull(RecipeDraft.of(recipe).changesFrom(recipe))
    }

    @Test fun totalFollowsPrepAndCookUntilEdited() {
        var d = RecipeDraft.of(recipe).withCook(DraftTime("", "0"))
        assertEquals(15L, d.total.totalMinutes)
        val change = d.changesFrom(recipe)!!
        assertEquals("0 minutes", change.performTime)
        assertEquals("15 minutes", change.totalTime)
        assertNull(change.prepTime)
        d = d.withTotal(DraftTime("8", "")).withPrep(DraftTime("", "20"))
        assertEquals(480L, d.total.totalMinutes)
    }

    @Test fun clearingATimeMakesItUnknown() {
        val change = RecipeDraft.of(recipe).withPrep(DraftTime()).changesFrom(recipe)!!
        assertEquals("", change.prepTime)
    }

    @Test fun servingsRewritePlainYield() {
        val change = RecipeDraft.of(recipe).copy(servings = "6").changesFrom(recipe)!!
        assertEquals(6.0, change.servings!!, 0.0)
        assertEquals("6 servings", change.recipeYield)
        val loaf = recipe.copy(recipeYield = "1 loaf")
        assertNull(RecipeDraft.of(loaf).copy(servings = "6").changesFrom(loaf)!!.recipeYield)
    }

    @Test fun servingsFromYieldWhenUnset() {
        val old = recipe.copy(recipeServings = 0.0, recipeYield = "6 servings")
        assertEquals("6", RecipeDraft.of(old).servings)
        assertNull(RecipeDraft.of(old).changesFrom(old))
    }

    @Test fun onlyNewAndChangedLinesAreParsed() {
        val d = RecipeDraft.of(recipe)
        val edited = d.copy(ingredients = listOf(d.ingredients[1], d.ingredients[0].copy(text = "2 lb ground beef"), DraftIngredient("1 onion, diced")))
        val change = edited.changesFrom(recipe)!!
        assertEquals(listOf(1, 2), change.linesToParse())
    }

    @Test fun removingALineMovesItsHeading() {
        val d = RecipeDraft.of(recipe).removeIngredient(0)
        assertEquals("For the chili", d.ingredients[0].title)
    }

    @Test fun pastingSeveralLinesSplitsThem() {
        val d = RecipeDraft.of(recipe).setIngredientText(1, "2 cans beans\n1 onion\n\n2 tbsp chili powder")
        assertEquals(listOf("1 lb ground beef", "2 cans beans", "1 onion", "2 tbsp chili powder"), d.ingredients.map { it.text })
    }

    @Test fun localCopyKeepsUnchangedLines() {
        val d = RecipeDraft.of(recipe)
        val change = d.copy(ingredients = d.ingredients + DraftIngredient("1 onion", referenceId = "c")).changesFrom(recipe)!!
        val updated = recipe.withChange(change)
        assertEquals("f1", updated.recipeIngredient[0].food?.id)
        assertEquals("For the chili", updated.recipeIngredient[0].title)
        assertEquals("1 onion", updated.recipeIngredient[2].display)
        assertEquals("c", updated.recipeIngredient[2].referenceId)
    }

    @Test fun patchKeepsRawLinesAndStepLinks() {
        val raw = JsonObject(
            mapOf(
                "recipeIngredient" to JsonArray(
                    listOf(
                        obj("referenceId" to "h", "title" to "For the chili", "note" to ""),
                        obj("referenceId" to "a", "display" to "1 lb ground beef", "food" to obj("id" to "f1", "name" to "ground beef")),
                        obj("referenceId" to "b", "display" to "2 cans beans"),
                    ),
                ),
                "recipeInstructions" to JsonArray(
                    listOf(
                        JsonObject(mapOf("id" to JsonPrimitive("s1"), "text" to JsonPrimitive("Brown the beef."), "ingredientReferences" to JsonArray(listOf(obj("referenceId" to "a"))))),
                        obj("id" to "s2", "text" to "Simmer."),
                    ),
                ),
            ),
        )
        val d = RecipeDraft.of(recipe)
        val draft = d.copy(
            name = "Beef chili",
            ingredients = listOf(d.ingredients[0], d.ingredients[1].copy(text = "3 cans beans"), DraftIngredient("1 onion", referenceId = "c")),
            steps = listOf(d.steps[0].copy(text = "Brown the beef well."), DraftStep("Serve.", id = "s3")),
        )
        val change = draft.changesFrom(recipe)!!
        val parsedBeans = parsedIngredient(obj("quantity" to "3", "display" to "x"), "3 cans beans", obj("id" to "f2", "name" to "beans"), null)
        val body = buildRecipePatch(raw, change, mapOf(1 to parsedBeans))

        assertEquals("Beef chili", body.string("name"))
        val lines = body["recipeIngredient"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("a", "b", "c"), lines.map { it.string("referenceId") })
        assertEquals("f1", lines[0]["food"]!!.jsonObject.string("id"))
        assertEquals("For the chili", lines[0].string("title"))
        assertNull(lines[0].string("originalText"))
        assertEquals("f2", lines[1]["food"]!!.jsonObject.string("id"))
        assertEquals("3 cans beans", lines[1].string("originalText"))
        assertNull(lines[1]["display"])
        assertEquals("1 onion", lines[2].string("note")) // not parsed: kept as text
        assertEquals(JsonNull, lines[1]["title"])

        val steps = body["recipeInstructions"]!!.jsonArray.map { it.jsonObject }
        assertEquals("Brown the beef well.", steps[0].string("text"))
        assertEquals(1, steps[0]["ingredientReferences"]!!.jsonArray.size)
        assertEquals("s3", steps[1].string("id"))
        assertNull(body["prepTime"])
    }

    @Test fun selectByReferenceThenIndex() {
        val raw = listOf(obj("referenceId" to "a"), obj("note" to "salt"), obj("referenceId" to "b"))
        assertEquals(2, selectIngredients(raw, setOf("b"), setOf(1)).size)
        assertEquals("b", selectIngredients(raw, setOf("b"), emptySet()).single().string("referenceId"))
    }

    private fun obj(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(
        pairs.associate { (k, v) -> k to (v as? JsonObject ?: JsonPrimitive(v.toString())) },
    )
}
