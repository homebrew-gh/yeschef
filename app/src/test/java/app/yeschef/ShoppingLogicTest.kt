package app.yeschef

import app.yeschef.data.ShoppingRow
import app.yeschef.data.UNLABELED
import app.yeschef.data.assignRecipeColors
import app.yeschef.data.parseRecipeIds
import app.yeschef.data.groupForShopping
import app.yeschef.data.itemText
import app.yeschef.data.mergeLabelOrders
import app.yeschef.data.withChecked
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingLogicTest {
    private fun row(id: String, label: String?, checked: Boolean = false, position: Int = 0) =
        ShoppingRow(id, id, checked, position, label?.let { "id-$it" }, label, null, pending = false)

    @Test
    fun sectionsFollowStoreOrderThenAlphabeticalThenUnlabeled() {
        val rows = listOf(
            row("milk", "Dairy"), row("apples", "Produce"), row("bread", "Bakery"),
            row("foil", null), row("soap", "Household"), row("eggs", "Dairy", checked = true),
        )
        val order = mapOf("id-Produce" to 0, "id-Dairy" to 1)
        val (sections, checked) = groupForShopping(rows, order)
        assertEquals(listOf("Produce", "Dairy", "Bakery", "Household", UNLABELED), sections.map { it.labelName })
        assertEquals(listOf("eggs"), checked.map { it.id })
    }

    @Test
    fun itemsWithinSectionSortByPosition() {
        val rows = listOf(row("b", "X", position = 2), row("a", "X", position = 1))
        val (sections, _) = groupForShopping(rows, emptyMap())
        assertEquals(listOf("a", "b"), sections.single().rows.map { it.id })
    }

    @Test
    fun itemTextPrefersDisplay() {
        assertEquals("2 cups flour", itemText("2 cups flour", "x", "y", 2.0))
        assertEquals("3 onion diced", itemText("", "diced", "onion", 3.0))
        assertEquals("milk", itemText(null, "milk", null, 1.0))
    }

    @Test
    fun withCheckedKeepsOtherFields() {
        val raw = Json.parseToJsonElement("""{"id":"1","checked":false,"recipeReferences":[{"recipeId":"r"}],"extras":{}}""").jsonObject
        val updated = raw.withChecked(true)
        assertTrue(updated["checked"]!!.jsonPrimitive.boolean)
        assertEquals(raw["recipeReferences"], updated["recipeReferences"])
        assertFalse(raw["checked"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun recipeColorsAreDistinctAndStable() {
        val names = mapOf("r2" to "Tacos", "r1" to "Lasagna", "r3" to "Banana bread")
        val colors = assignRecipeColors(names, paletteSize = 8)
        assertEquals(mapOf("r3" to 0, "r1" to 1, "r2" to 2), colors)
        assertEquals(colors, assignRecipeColors(names.toList().reversed().toMap(), 8))
    }

    @Test
    fun parsesRecipeIdList() {
        assertEquals(listOf("a", "b"), parseRecipeIds("a, b,"))
        assertEquals(emptyList<String>(), parseRecipeIds(""))
    }

    @Test
    fun mergedLabelOrderKeepsFirstListOrderThenAddsTheRest() {
        val weekly = mapOf("produce" to 0, "dairy" to 1, "meat" to 2)
        val household = mapOf("cleaning" to 0, "produce" to 1, "paper" to 2)
        val merged = mergeLabelOrders(listOf(weekly, household))
        assertEquals(listOf("produce", "dairy", "meat", "cleaning", "paper"), merged.entries.sortedBy { it.value }.map { it.key })
        assertEquals(mapOf("a" to 3), mergeLabelOrders(listOf(emptyMap(), mapOf("a" to 3))))
    }

    @Test
    fun mergedListsGroupBySharedStoreSection() {
        val rows = listOf(
            row("onions", "Produce").copy(listId = "weekly"),
            row("tp", null).copy(listId = "household"),
            row("apples", "Produce").copy(listId = "household"),
        )
        val (sections, _) = groupForShopping(rows, mergeLabelOrders(listOf(mapOf("id-Produce" to 0))))
        assertEquals(listOf("Produce", UNLABELED), sections.map { it.labelName })
        assertEquals(setOf("weekly", "household"), sections.first().rows.map { it.listId }.toSet())
    }
}
