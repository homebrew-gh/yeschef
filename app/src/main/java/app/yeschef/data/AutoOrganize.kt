package app.yeschef.data

import app.yeschef.data.remote.OrganizerDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The tidy-up after an import, and the shopping list's magic wand: links lines to Mealie
 * foods (so they sort into aisles) and picks the recipe's chapter and tags from the ones
 * already in Mealie. It never makes new tags or categories.
 */
object AutoOrganize {

    // ---- Ingredient lines ----

    /** The text of a recipe line to send to the parser, or null when it already has a food or is empty. */
    fun lineText(ing: JsonObject): String? {
        if ((ing["food"] as? JsonObject)?.string("id") != null) return null
        val text = ing.string("originalText") ?: ing.string("note") ?: ing.string("display")
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** A parsed line in place of the imported one, keeping its place (reference id, section title). */
    fun mergeLine(original: JsonObject, parsed: JsonObject, text: String): JsonObject =
        JsonObject(
            parsed + mapOf(
                "referenceId" to (original["referenceId"] ?: JsonNull),
                "title" to (original["title"] ?: JsonNull),
                "originalText" to JsonPrimitive(text),
            ),
        )

    // ---- Shopping list items ----

    /** A hand-added item that the wand can try: not ticked, no food and no aisle yet. */
    fun needsAisle(item: JsonObject): Boolean =
        (item["checked"] as? JsonPrimitive)?.booleanOrNull != true &&
            item.string("foodId") == null && (item["food"] as? JsonObject)?.string("id") == null &&
            item.string("labelId") == null && itemText(item) != null

    fun itemText(item: JsonObject): String? =
        (item.string("note") ?: item.string("display"))?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The item linked to [food] (a Mealie food with an id), with the parser's amount, unit and
     * note, and the food's aisle. Mealie rebuilds the display text from those.
     */
    fun linkedItem(item: JsonObject, parsed: JsonObject, food: JsonObject): JsonObject {
        val out = item.toMutableMap()
        out.remove("display")
        out.remove("label")
        out["foodId"] = JsonPrimitive(food.string("id"))
        out["food"] = food
        (parsed["unit"] as? JsonObject)?.takeIf { it.string("id") != null }?.let {
            out["unitId"] = JsonPrimitive(it.string("id"))
            out["unit"] = it
        }
        (parsed["quantity"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { out["quantity"] = JsonPrimitive(it) }
        out["note"] = JsonPrimitive(parsed.string("note").orEmpty())
        (food.string("labelId") ?: (food["label"] as? JsonObject)?.string("id"))?.let { out["labelId"] = JsonPrimitive(it) }
        return JsonObject(out)
    }

    // ---- Chapter and tags ----

    /** The recipe page's own course, cuisine and keywords, from Mealie's scrape of it (schema.org Recipe). */
    fun siteWords(schema: JsonElement?): List<String> {
        val o = schema as? JsonObject ?: return emptyList()
        return listOf("recipeCategory", "recipeCuisine", "keywords").flatMap { key -> words(o[key]) }.distinct()
    }

    private fun words(e: JsonElement?): List<String> = when (e) {
        is JsonArray -> e.flatMap(::words)
        is JsonPrimitive -> e.contentOrNull.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    data class Picked(val categories: List<OrganizerDto>, val tags: List<OrganizerDto>)

    /**
     * Chapter and tags for a new recipe, only from [categories] and [tags] that exist in Mealie.
     * The chapter comes from the page's course (or a soup, salad or cocktail in the name), and
     * only when the recipe has none. Protein tags come from the linked foods and the name;
     * other tags only when the page names them exactly.
     */
    fun pick(
        name: String,
        foods: List<String>,
        site: List<String>,
        categories: List<OrganizerDto>,
        tags: List<OrganizerDto>,
        hasCategory: Boolean,
    ): Picked {
        val siteLower = site.map { it.lowercase() }
        val pickedCats = if (hasCategory) emptyList() else listOfNotNull(chapterFor(name, siteLower, categories))

        val proteinText = (foods.filterNot { f -> NOT_PROTEIN.any { hasWord(f.lowercase(), it) } } + name + site)
            .joinToString(" | ").lowercase()
        val proteinTags = Cookbook.proteins
            .filter { (protein, aliases) -> protein !in DONT_INFER && aliases.any { hasWord(proteinText, it) } }
            .mapNotNull { (protein, aliases) ->
                tags.firstOrNull { it.name.equals(protein, ignoreCase = true) } ?: tags.firstOrNull { it.name.lowercase() in aliases }
            }
        val named = tags.filter { it.name.lowercase() in siteLower }
        // A page that says vegetarian or vegan is believed; the foods alone never make that call.
        val diet = Cookbook.proteins.filter { it.first in DONT_INFER }.flatMap { (protein, aliases) ->
            if (siteLower.any { it in aliases }) tags.filter { it.name.equals(protein, ignoreCase = true) || it.name.lowercase() in aliases } else emptyList()
        }
        return Picked(pickedCats, (proteinTags + diet + named).distinctBy { it.id })
    }

    private fun chapterFor(name: String, site: List<String>, categories: List<OrganizerDto>): OrganizerDto? {
        fun categoryFor(chapter: Pair<String, Set<String>>) =
            categories.firstOrNull { it.name.equals(chapter.first, ignoreCase = true) } ?: categories.firstOrNull { it.name.lowercase() in chapter.second }
        // The page's own words, in its order: exact names first, then phrases like "Easy Main Course".
        for (word in site) {
            categories.firstOrNull { it.name.equals(word, ignoreCase = true) }?.let { return it }
            Cookbook.chapters.firstOrNull { word in it.second }?.let { ch -> categoryFor(ch)?.let { return it } }
        }
        for (word in site) {
            Cookbook.chapters.firstOrNull { ch -> ch.second.any { hasWord(word, it) } }?.let { ch -> categoryFor(ch)?.let { return it } }
        }
        val lower = name.lowercase()
        NAME_CHAPTERS.firstOrNull { (words, _) -> words.any { hasWord(lower, it) } }?.let { (_, chapter) ->
            Cookbook.chapters.firstOrNull { it.first == chapter }?.let { return categoryFor(it) }
        }
        return null
    }

    /** Whether [phrase] appears in [text] as whole words (a plural "s" allowed). */
    private fun hasWord(text: String, phrase: String): Boolean =
        Regex("""(^|[^a-z])${Regex.escape(phrase)}s?($|[^a-z])""").containsMatchIn(text)

    private val DONT_INFER = setOf("Vegetarian", "Vegan")

    /** Foods that taste of meat or fish without making the dish about it. */
    private val NOT_PROTEIN = listOf("broth", "stock", "bouillon", "sauce", "paste", "fat", "grease", "base", "gelatin")

    /** Names that say the chapter outright. Others (bread, sweet, side) are too easy to get wrong. */
    private val NAME_CHAPTERS = listOf(
        setOf("soup", "salad", "chowder", "bisque") to "Soups & Salads",
        setOf("cocktail", "smoothie", "margarita", "lemonade", "sangria") to "Drinks",
    )
}
