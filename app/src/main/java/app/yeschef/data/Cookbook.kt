package app.yeschef.data

import app.yeschef.data.cook.detectDurations
import app.yeschef.data.local.RecipeEntity
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Turns the recipe box into a cookbook: chapters come from Mealie categories,
 * protein from tags (or categories), and a few useful sort orders.
 *
 * Suggested scheme (also in the README, for whoever tags the library):
 * - Category = chapter: Mains, Sides, Soups & Salads, Breakfast, Desserts, Baking,
 *   Starters & Snacks, Drinks, Sauces & Basics.
 * - Tags = protein (Chicken, Beef, Pork, Lamb, Seafood, Vegetarian, Vegan) and
 *   style (Quick, Slow cooker, Grill, One pot, Make ahead...).
 */
object Cookbook {
    /** Chapter names in book order, each with the Mealie category names that map to it. */
    val chapters: List<Pair<String, Set<String>>> = listOf(
        "Breakfast" to setOf("breakfast", "brunch"),
        "Starters & Snacks" to setOf("starters & snacks", "starter", "starters", "appetizer", "appetizers", "snack", "snacks"),
        "Soups & Salads" to setOf("soups & salads", "soup", "soups", "salad", "salads"),
        "Mains" to setOf("mains", "main", "main course", "main dish", "entree", "entrees", "dinner", "dinners", "lunch"),
        "Sides" to setOf("sides", "side", "side dish", "side dishes"),
        "Baking" to setOf("baking", "bread", "breads", "baked goods"),
        "Desserts" to setOf("desserts", "dessert", "sweets", "sweet"),
        "Drinks" to setOf("drinks", "drink", "beverage", "beverages", "cocktail", "cocktails"),
        "Sauces & Basics" to setOf("sauces & basics", "sauce", "sauces", "basics", "dressing", "dressings", "condiment", "condiments"),
    )
    const val UNFILED = "More recipes"

    /** Protein filters, each with the tag/category names that count as it. */
    val proteins: List<Pair<String, Set<String>>> = listOf(
        "Chicken" to setOf("chicken", "poultry"),
        "Beef" to setOf("beef", "steak"),
        "Pork" to setOf("pork", "bacon", "sausage", "ham"),
        "Lamb" to setOf("lamb"),
        "Turkey" to setOf("turkey"),
        "Seafood" to setOf("seafood", "fish", "salmon", "shrimp", "prawns", "tuna", "shellfish"),
        "Vegetarian" to setOf("vegetarian", "veggie", "meatless"),
        "Vegan" to setOf("vegan", "plant-based", "plant based"),
    )

    enum class Sort(val label: String) {
        Chapters("By chapter"),
        AtoZ("A to Z"),
        NotLately("Not made lately"),
        Newest("Newest"),
        TopRated("Top rated"),
        Quickest("Quickest"),
    }

    data class Section(val title: String, val recipes: List<RecipeEntity>)

    fun names(field: String): List<String> = field.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** The recipe's chapter: the first of its categories that maps to a known chapter, else its first category. */
    fun chapterOf(r: RecipeEntity): String {
        val cats = names(r.categories)
        for (c in cats) chapters.firstOrNull { c.lowercase() in it.second }?.let { return it.first }
        return cats.firstOrNull() ?: UNFILED
    }

    fun proteinsOf(r: RecipeEntity): Set<String> {
        val words = (names(r.tags) + names(r.categories)).map { it.lowercase() }.toSet()
        return proteins.filter { (_, aliases) -> aliases.any { it in words } }.map { it.first }.toSet()
    }

    /** Chapters that have recipes, in book order, then any custom categories A to Z, then unfiled. */
    fun chapterOrder(recipes: List<RecipeEntity>): List<String> {
        val present = recipes.map(::chapterOf).toSet()
        val known = chapters.map { it.first }.filter { it in present }
        val custom = (present - known.toSet() - UNFILED).sortedBy { it.lowercase() }
        return known + custom + listOfNotNull(UNFILED.takeIf { it in present })
    }

    fun filter(recipes: List<RecipeEntity>, query: String, chapter: String?, protein: String?): List<RecipeEntity> =
        recipes.filter { r ->
            (chapter == null || chapterOf(r) == chapter) &&
                (protein == null || protein in proteinsOf(r)) &&
                (query.isBlank() || r.name.contains(query, true) || r.description.orEmpty().contains(query, true) ||
                    names(r.tags).any { it.contains(query, true) })
        }

    fun sorted(recipes: List<RecipeEntity>, sort: Sort): List<RecipeEntity> {
        val byName = compareBy<RecipeEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        return when (sort) {
            Sort.Chapters, Sort.AtoZ -> recipes.sortedWith(byName)
            // Longest since last cooked first; never-cooked recipes after them.
            Sort.NotLately -> recipes.sortedWith(compareBy<RecipeEntity>({ it.lastMade == null }, { it.lastMade }).then(byName))
            Sort.Newest -> recipes.sortedWith(compareByDescending<RecipeEntity> { it.dateAdded ?: "" }.then(byName))
            Sort.TopRated -> recipes.sortedWith(compareByDescending<RecipeEntity> { it.rating ?: 0.0 }.then(byName))
            Sort.Quickest -> recipes.sortedWith(compareBy<RecipeEntity>({ minutes(it.totalTime) == null }, { minutes(it.totalTime) }).then(byName))
        }
    }

    /** Chapters as sections in cookbook order; any other sort gives one flat section. */
    fun sections(recipes: List<RecipeEntity>, sort: Sort): List<Section> {
        if (sort != Sort.Chapters) return listOf(Section("", sorted(recipes, sort)))
        val byChapter = recipes.groupBy(::chapterOf)
        return chapterOrder(recipes).map { Section(it, sorted(byChapter[it].orEmpty(), Sort.AtoZ)) }
    }

    /** Total time in minutes from Mealie's text ("1 hour 10 minutes", "PT45M"), if it can be read. */
    fun minutes(totalTime: String?): Long? {
        if (totalTime.isNullOrBlank()) return null
        Regex("^P(?:T)?(?:(\\d+)H)?(?:(\\d+)M)?$", RegexOption.IGNORE_CASE).matchEntire(totalTime.trim())?.let { m ->
            val h = m.groupValues[1].toLongOrNull() ?: 0
            val min = m.groupValues[2].toLongOrNull() ?: 0
            return (h * 60 + min).takeIf { it > 0 }
        }
        val secs = detectDurations(totalTime).sumOf { it.seconds }
        return if (secs > 0) secs / 60 else null
    }

    /** "Cooked today", "Cooked 3 weeks ago"... or null if never cooked. */
    fun lastCookedText(lastMade: String?, today: LocalDate): String? {
        val date = parseDate(lastMade) ?: return null
        val days = ChronoUnit.DAYS.between(date, today)
        val ago = when {
            days <= 0 -> return "Cooked today"
            days == 1L -> return "Cooked yesterday"
            days < 14 -> "$days days"
            days < 63 -> "${days / 7} weeks"
            days < 365 -> "${days / 30} months"
            else -> return "Cooked " + date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
        }
        return "Cooked $ago ago"
    }

    private fun parseDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
    }
}
