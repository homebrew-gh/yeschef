package app.yeschef

import app.yeschef.data.FoodMatch
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMatchTest {
    private fun obj(json: String): JsonObject = MealieJson.parseToJsonElement(json).jsonObject

    @Test fun splitCombinedIngredientSplitsBareListsAndKeepsCompounds() {
        assertEquals(
            listOf("salt, to taste", "pepper, to taste"),
            FoodMatch.splitCombinedIngredient("salt and pepper, to taste"),
        )
        assertEquals(
            listOf("half and half"),
            FoodMatch.splitCombinedIngredient("half and half"),
        )
        assertEquals(
            listOf("1 cup flour and water"),
            FoodMatch.splitCombinedIngredient("1 cup flour and water"),
        )
    }

    @Test fun normalizeKeyHandlesPluralsHyphensAndNonPlurals() {
        assertEquals("all purpose flour", FoodMatch.normalizeKey("All-Purpose Flour"))
        assertEquals("yellow onion", FoodMatch.normalizeKey("Yellow Onions"))
        assertEquals("cherry tomato", FoodMatch.normalizeKey("Cherry Tomatoes"))
        assertEquals("hummus", FoodMatch.normalizeKey("Hummus"))
        assertEquals("asparagus", FoodMatch.normalizeKey("Asparagus"))
    }

    @Test fun isGoodFoodMatchRejectsFuzzyMismatchesAndHonorsAliases() {
        val butter = obj("""{"id":"b","name":"butter"}""")
        val maple = obj("""{"id":"m","name":"maple syrup"}""")
        val chicken = obj("""{"id":"c","name":"chicken"}""")
        val olive = obj("""{"id":"o","name":"olive"}""")
        val oliveOil = obj("""{"id":"oo","name":"olive oil"}""")
        val greenOnion = obj("""{"id":"g","name":"green onion","aliases":[{"name":"scallions"}]}""")

        assertFalse(FoodMatch.isGoodFoodMatch("1 cup peanut butter", butter))
        assertEquals("peanut butter", FoodMatch.ownFoodText("1 cup peanut butter", butter))
        assertFalse(FoodMatch.isGoodFoodMatch("1 oz simple syrup", maple))
        assertEquals("simple syrup", FoodMatch.ownFoodText("1 oz simple syrup", maple))
        assertFalse(FoodMatch.isGoodFoodMatch("1 cup chicken broth", chicken))
        assertFalse(FoodMatch.isGoodFoodMatch("1 tbsp olive oil", olive))

        assertTrue(FoodMatch.isGoodFoodMatch("2 tbsp unsalted butter", butter))
        assertNull(FoodMatch.ownFoodText("2 tbsp unsalted butter", butter))
        assertTrue(FoodMatch.isGoodFoodMatch("2 tbsp extra-virgin olive oil", oliveOil))
        assertTrue(FoodMatch.isGoodFoodMatch("4 scallions, sliced", greenOnion))
    }

    @Test fun extractCoreFoodStripsPrepAndSizeWords() {
        assertEquals(
            "yellow onions",
            FoodMatch.extractCoreFood("3 large finely chopped yellow onions, divided", note = "divided"),
        )
        assertEquals(
            "chicken thighs",
            FoodMatch.extractCoreFood("1 lb boneless skinless chicken thighs"),
        )
    }

    @Test fun findMatchingFoodChecksAliasesPluralsAndHyphens() {
        val catalog = listOf(
            obj("""{"id":"1","name":"green onion","aliases":[{"name":"scallions"}]}"""),
            obj("""{"id":"2","name":"all-purpose flour"}"""),
            obj("""{"id":"3","name":"cherry tomato"}"""),
        )
        assertEquals("1", FoodMatch.findMatchingFood("scallion", catalog)?.string("id"))
        assertEquals("2", FoodMatch.findMatchingFood("all purpose flour", catalog)?.string("id"))
        assertEquals("3", FoodMatch.findMatchingFood("cherry tomatoes", catalog)?.string("id"))
        assertNull(FoodMatch.findMatchingFood("peanut butter", catalog))
    }
}
