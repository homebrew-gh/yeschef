package app.yeschef

import app.yeschef.data.AutoOrganize
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.remote.OrganizerDto
import app.yeschef.data.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoOrganizeTest {
    private fun obj(json: String): JsonObject = MealieJson.parseToJsonElement(json).jsonObject
    private fun org(name: String) = OrganizerDto(id = "id-" + name.lowercase(), name = name, slug = name.lowercase())

    private val categories = listOf("Mains", "Sides", "Soups & Salads", "Drinks", "Desserts").map(::org)
    private val tags = listOf("Chicken", "Beef", "Seafood", "Vegetarian", "Quick", "Mexican").map(::org)

    @Test fun linesToParse() {
        assertEquals("2 cups flour", AutoOrganize.lineText(obj("""{"quantity":0,"note":"2 cups flour","food":null}""")))
        assertEquals("1 onion, diced", AutoOrganize.lineText(obj("""{"originalText":"1 onion, diced","note":"diced"}""")))
        assertNull(AutoOrganize.lineText(obj("""{"note":"diced","food":{"id":"f1","name":"onion"}}""")))
        assertNull(AutoOrganize.lineText(obj("""{"note":"  ","food":null}""")))
    }

    @Test fun mergeKeepsPlace() {
        val merged = AutoOrganize.mergeLine(
            obj("""{"referenceId":"r1","title":"Sauce","note":"1 cup milk"}"""),
            obj("""{"quantity":1,"note":"","food":{"id":"f","name":"milk"},"referenceId":"other"}"""),
            "1 cup milk",
        )
        assertEquals("r1", merged.string("referenceId"))
        assertEquals("Sauce", merged.string("title"))
        assertEquals("1 cup milk", merged.string("originalText"))
    }

    @Test fun chapterFromThePage() {
        val p = AutoOrganize.pick("White Chicken Chili", listOf("chicken breast"), listOf("Main Course", "chili"), categories, tags, hasCategory = false)
        assertEquals(listOf("Mains"), p.categories.map { it.name })
        assertEquals(listOf("Sides"), AutoOrganize.pick("Roast carrots", emptyList(), listOf("Side Dish"), categories, tags, false).categories.map { it.name })
        assertEquals(listOf("Drinks"), AutoOrganize.pick("Paloma", emptyList(), listOf("Cocktails"), categories, tags, false).categories.map { it.name })
        // Keeps a chapter the recipe already has.
        assertTrue(AutoOrganize.pick("x", emptyList(), listOf("Dinner"), categories, tags, hasCategory = true).categories.isEmpty())
        // Only chapters that exist in Mealie.
        assertTrue(AutoOrganize.pick("Pancakes", emptyList(), listOf("Breakfast"), categories, tags, false).categories.isEmpty())
    }

    @Test fun chapterFromTheNameOnlyWhenClear() {
        assertEquals(listOf("Soups & Salads"), AutoOrganize.pick("Tomato Soup", emptyList(), emptyList(), categories, tags, false).categories.map { it.name })
        assertTrue(AutoOrganize.pick("Sweet Potato Fries", emptyList(), emptyList(), categories, tags, false).categories.isEmpty())
        assertTrue(AutoOrganize.pick("Garlic Bread", emptyList(), emptyList(), categories, tags, false).categories.isEmpty())
    }

    @Test fun proteinTagsFromFoods() {
        val p = AutoOrganize.pick("Weeknight stir fry", listOf("chicken thighs", "soy sauce"), emptyList(), categories, tags, false)
        assertEquals(listOf("Chicken"), p.tags.map { it.name })
        assertEquals(listOf("Seafood"), AutoOrganize.pick("Garlic shrimp", listOf("shrimp"), emptyList(), categories, tags, false).tags.map { it.name })
        // Broth and fish sauce don't make it a chicken or fish dish.
        assertTrue(AutoOrganize.pick("Lentil soup", listOf("chicken broth", "fish sauce", "lentils"), emptyList(), categories, tags, false).tags.isEmpty())
        // No meat is not the same as vegetarian, unless the page says so.
        assertTrue(AutoOrganize.pick("Bean salad", listOf("beans"), emptyList(), categories, tags, false).tags.isEmpty())
        assertEquals(listOf("Vegetarian"), AutoOrganize.pick("Bean salad", listOf("beans"), listOf("vegetarian"), categories, tags, false).tags.map { it.name })
    }

    @Test fun otherTagsOnlyWhenThePageNamesThem() {
        val p = AutoOrganize.pick("Tacos", listOf("ground beef"), listOf("Mexican", "quick", "taco tuesday"), categories, tags, false)
        assertEquals(listOf("Beef", "Quick", "Mexican"), p.tags.map { it.name })
    }

    @Test fun siteWords() {
        val schema = MealieJson.parseToJsonElement(
            """{"@type":"Recipe","recipeCategory":["Dinner"],"recipeCuisine":"Mexican","keywords":"tacos, quick, easy"}""",
        )
        assertEquals(listOf("Dinner", "Mexican", "tacos", "quick", "easy"), AutoOrganize.siteWords(schema))
        assertEquals(emptyList<String>(), AutoOrganize.siteWords(MealieJson.parseToJsonElement("\"recipe_scrapers was unable to scrape this URL\"")))
    }

    @Test fun shoppingItems() {
        val typed = obj("""{"id":"i1","shoppingListId":"l","checked":false,"quantity":1,"note":"2 lb chicken thighs","display":"2 lb chicken thighs","foodId":null,"labelId":null,"food":null,"label":null}""")
        assertTrue(AutoOrganize.needsAisle(typed))
        assertFalse(AutoOrganize.needsAisle(obj("""{"checked":true,"note":"milk"}""")))
        assertFalse(AutoOrganize.needsAisle(obj("""{"checked":false,"note":"milk","foodId":"f"}""")))
        assertFalse(AutoOrganize.needsAisle(obj("""{"checked":false,"note":"milk","labelId":"dairy"}""")))

        val linked = AutoOrganize.linkedItem(
            typed,
            obj("""{"quantity":2,"unit":{"id":"u-lb","name":"pound"},"food":{"id":"f-ch","name":"chicken thighs"},"note":""}"""),
            obj("""{"id":"f-ch","name":"chicken thighs","labelId":"meat"}"""),
        )
        assertEquals("f-ch", linked.string("foodId"))
        assertEquals("u-lb", linked.string("unitId"))
        assertEquals("meat", linked.string("labelId"))
        assertEquals("", linked.string("note"))
        assertEquals("i1", linked.string("id"))
        assertNull(linked["display"])
    }
}
