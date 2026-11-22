package app.yeschef.data.cook

/** One ingredient line, reduced to what step matching needs. */
data class IngredientLine(
    /** Mealie's reference id, if any. */
    val referenceId: String?,
    /** The parsed food name from Mealie, if the recipe was parsed. */
    val food: String?,
    /** The line as shown, such as "1 yellow onion, diced". */
    val text: String,
    /** Section headings ("For the sauce") aren't real ingredients. */
    val isHeadingOnly: Boolean = false,
)

data class StepLine(val text: String, val referenceIds: List<String>)

/**
 * For each step, the indexes of the ingredients it uses, in ingredient-list order.
 *
 * A step with ingredients linked in Mealie uses exactly those links. Otherwise the
 * ingredients are matched by name in the step text ("Brown the beef and onion" picks
 * up "1 lb ground beef" and "1 yellow onion, diced").
 */
fun ingredientsForSteps(ingredients: List<IngredientLine>, steps: List<StepLine>): List<List<Int>> {
    val byRef = ingredients.withIndex()
        .filter { it.value.referenceId != null }
        .associate { it.value.referenceId!! to it.index }
    val keys = ingredients.map { if (it.isHeadingOnly) null else matchKey(it) }
    val headCounts = keys.filterNotNull().groupingBy { it.head }.eachCount()
    val tokenCounts = keys.filterNotNull().flatMap { it.tokens.distinct() }.groupingBy { it }.eachCount()

    return steps.map { step ->
        val linked = step.referenceIds.mapNotNull { byRef[it] }
        if (linked.isNotEmpty()) return@map linked.distinct().sorted()
        val words = words(step.text)
        keys.withIndex().filter { (_, key) -> key != null && key.matches(words, headCounts, tokenCounts) }.map { it.index }
    }
}

/** The words that name an ingredient: [tokens] in order, ending in its [head] noun. */
private data class MatchKey(val tokens: List<String>) {
    val head: String get() = tokens.last()

    fun matches(stepWords: List<String>, headCounts: Map<String, Int>, tokenCounts: Map<String, Int>): Boolean {
        if (containsRun(stepWords, tokens)) return true
        // A distinctive first word on its own also counts: "stir in the marinara".
        val first = tokens.first()
        if (tokens.size > 1 && first !in MODIFIERS && tokenCounts[first] == 1 && first in stepWords) return true
        // The head noun alone ("beef", "onion") is enough when no other ingredient shares it.
        // When one does ("bell pepper" and "black pepper"), the word before it must match too.
        val shared = (headCounts[head] ?: 0) > 1
        if (!shared) return head in stepWords
        if (tokens.size < 2) return false
        return containsRun(stepWords, tokens.takeLast(2))
    }
}

private fun matchKey(line: IngredientLine): MatchKey? {
    val source = line.food?.takeIf { it.isNotBlank() } ?: line.text
    // Drop anything after a comma or bracket: "onion, diced" / "butter (softened)".
    val main = source.split(',', '(', ';').first()
    val tokens = words(main).filter { it !in IGNORED && it.none(Char::isDigit) }
    return if (tokens.isEmpty()) null else MatchKey(tokens)
}

private fun containsRun(haystack: List<String>, run: List<String>): Boolean {
    if (run.isEmpty() || run.size > haystack.size) return false
    return (0..haystack.size - run.size).any { start -> run.indices.all { haystack[start + it] == run[it] } }
}

/** Lower-case, singular-ish words. */
private fun words(text: String): List<String> =
    WORD.findAll(text.lowercase()).map { singular(it.value) }.toList()

private fun singular(w: String): String = when {
    w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
    w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
    w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
    else -> w
}

private val WORD = Regex("[\\p{L}\\d½¼¾⅓⅔-]+")

/** Describing words that are too common to identify an ingredient by themselves. */
private val MODIFIERS = setOf(
    "red", "green", "yellow", "white", "black", "brown", "orange", "purple", "golden",
    "ground", "hot", "cold", "warm", "sweet", "salted", "unsalted", "light", "dark", "heavy", "low", "fat",
    "plain", "all-purpose", "kosher", "sea", "baby", "mixed", "smoked", "toasted", "roasted", "grilled", "canned",
)

/** Units, amounts and prep words that don't say what the ingredient is. */
private val IGNORED = setOf(
    "a", "an", "of", "the", "and", "or", "to", "for", "about", "plus", "more", "taste", "optional",
    "cup", "tablespoon", "tbsp", "tbs", "teaspoon", "tsp", "ounce", "oz", "pound", "lb", "gram", "g", "kg",
    "ml", "l", "liter", "litre", "pint", "quart", "gallon", "can", "jar", "package", "pkg", "packet", "bag",
    "box", "bunch", "clove", "pinch", "dash", "handful", "slice", "piece", "stick", "sprig", "head", "stalk",
    "large", "medium", "small", "big", "extra", "fresh", "freshly", "dried", "frozen", "whole", "ripe",
    "chopped", "diced", "minced", "sliced", "shredded", "grated", "crushed", "ground-up", "peeled", "cubed",
    "melted", "softened", "beaten", "divided", "halved", "quartered", "trimmed", "rinsed", "drained",
    "finely", "roughly", "thinly", "coarsely", "lightly", "packed", "heaping", "level", "room", "temperature",
    "boneless", "skinless", "cooked", "uncooked", "raw", "x", "-", "/",
)
