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
 * When a recipe has explicit Mealie ingredient links on any step, those links are trusted
 * for the whole recipe — steps with no links stay empty (preheat, bake, rest, serve) rather
 * than falling back to text matching. Otherwise, ingredients are matched by name in the step
 * text ("Brown the beef and onion" picks up "1 lb ground beef" and "1 yellow onion, diced").
 *
 * The name comes from the line as shown. Mealie's parsed food name is only used as well
 * when it agrees with that line ("2 large eggs" saved as "egg"), so a line linked to the
 * wrong food ("simple syrup" saved as "maple syrup") isn't matched by the wrong name.
 */
fun ingredientsForSteps(ingredients: List<IngredientLine>, steps: List<StepLine>): List<List<Int>> {
    val byRef = ingredients.withIndex()
        .filter { it.value.referenceId != null && !it.value.isHeadingOnly }
        .associate { it.value.referenceId!! to it.index }
    val linkedPerStep = steps.map { step ->
        step.referenceIds.mapNotNull { byRef[it] }.distinct().sorted()
    }
    if (linkedPerStep.any { it.isNotEmpty() }) return linkedPerStep

    val keys = ingredients.map { if (it.isHeadingOnly) emptyList() else matchKeys(it) }
    // How many ingredients each head noun and each word could stand for.
    val headCounts = keys.flatMap { k -> k.map { it.head }.distinct() }.groupingBy { it }.eachCount()
    val tokenCounts = keys.flatMap { k -> k.flatMap { it.tokens }.distinct() }.groupingBy { it }.eachCount()
    val all = keys.flatten()

    return steps.map { step ->
        val words = words(step.text)
        keys.withIndex()
            .filter { (_, k) -> k.any { it.matches(words, headCounts, tokenCounts, all) } }
            .map { it.index }
    }
}

/** The words that name an ingredient: [tokens] in order, ending in its [head] noun. */
private data class MatchKey(val tokens: List<String>) {
    val head: String get() = tokens.last()

    fun matches(stepWords: List<String>, headCounts: Map<String, Int>, tokenCounts: Map<String, Int>, all: List<MatchKey>): Boolean {
        val shared = (headCounts[head] ?: 0) > 1
        if (tokens.size > 1 && containsRun(stepWords, tokens)) return true
        // A distinctive first word on its own also counts: "stir in the marinara", "then soy
        // and broth". Not for things made from another food, though: "sear the beef" doesn't
        // use the beef broth, "bake the fish" isn't the fish sauce, and "add the garlic" isn't
        // the garlic powder or garlic butter.
        val first = tokens.first()
        val derivedFromFirst = head in MADE_FROM || (head == "sauce" && first in SAUCE_SOURCE_FOODS)
        if (tokens.size > 1 && !derivedFromFirst && first !in MODIFIERS && tokenCounts[first] == 1 && first in stepWords) return true
        // The head noun alone ("beef", "onion") is enough when no other ingredient shares it
        // and it names something on its own. "Until the juices run clear" isn't the lime juice,
        // "until fish flakes" isn't the red pepper flakes, and "mix well" isn't the cake mix,
        // so those need the word before as well.
        if (!shared && (tokens.size < 2 || head !in GENERIC_HEADS)) return head in stepWords
        if (tokens.size >= 2) return containsRun(stepWords, tokens.takeLast(2))
        // A plain "salt" next to "kosher salt": "season with salt" is the plain one, but
        // "add the kosher salt" isn't.
        val longer = all.filter { it.head == head && it.tokens.size >= 2 }
        return stepWords.indices.any { i ->
            stepWords[i] == head && longer.none { i > 0 && stepWords[i - 1] == it.tokens[it.tokens.size - 2] }
        }
    }
}

private fun matchKeys(line: IngredientLine): List<MatchKey> {
    val text = keyFrom(line.text)
    val food = line.food?.takeIf { it.isNotBlank() }?.let(::keyFrom)
    if (text == null) return listOfNotNull(food)
    val shown = words(line.text).toSet()
    return if (food != null && food != text && food.tokens.all { it in shown }) listOf(text, food) else listOf(text)
}

private fun keyFrom(source: String): MatchKey? {
    // Drop notes in brackets ("1 (15 oz) can black beans", "butter (softened)"),
    // anything after a comma ("onion, diced"), and trailing un-comma'd prep/serving phrases
    // ("chicken thighs cut into 1-inch pieces", "cilantro for garnish").
    val main = source.replace(BRACKETED, " ").split(',', ';', '(').first().replace(TRAILING_NOTE, " ")
    val tokens = words(main).filter { it !in IGNORED && it.none(Char::isDigit) }
    return if (tokens.isEmpty()) null else MatchKey(tokens)
}

private val BRACKETED = Regex("\\([^)]*\\)|\\[[^]]*]")

private val TRAILING_NOTE = Regex(
    "\\b(?:cut\\s+into|sliced\\s+into|chopped\\s+into|diced\\s+into|torn\\s+into|broken\\s+into|" +
        "for\\s+(?:serving|garnish|dusting|greasing|frying|brushing|drizzling|topping|the\\s+pan)|" +
        "to\\s+serve|to\\s+taste|at\\s+room\\s+temperature|plus\\s+more|or\\s+more|or\\s+to\\s+taste|" +
        "if\\s+desired|as\\s+needed)\\b.*",
    RegexOption.IGNORE_CASE,
)

private fun containsRun(haystack: List<String>, run: List<String>): Boolean {
    if (run.isEmpty() || run.size > haystack.size) return false
    return (0..haystack.size - run.size).any { start -> run.indices.all { haystack[start + it] == run[it] } }
}

/** Lower-case, singular-ish words. */
private fun words(text: String): List<String> =
    WORD.findAll(text.lowercase()).map { singular(it.value) }.toList()

private fun singular(w: String): String = when {
    w == "leaves" -> "leaf"
    w == "halves" -> "half"
    w == "loaves" -> "loaf"
    w == "knives" -> "knife"
    w == "cloves" -> "clove"
    w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
    w.length > 4 && w.endsWith("oes") -> w.dropLast(2)
    w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
    else -> w
}

private val WORD = Regex("[\\p{L}\\d½¼¾⅓⅔-]+")

/**
 * Words too common in steps to identify an ingredient by themselves: describing words,
 * and the first words of names like "baking soda", "cooking spray", "instant rice" or "half and half".
 */
private val MODIFIERS = setOf(
    "red", "green", "yellow", "white", "black", "brown", "orange", "purple", "golden", "blue", "amber",
    "ground", "hot", "cold", "warm", "chilled", "iced", "sweet", "sweetened", "unsweetened", "tart", "bitter",
    "salted", "unsalted", "light", "dark", "heavy", "low", "fat", "lean", "extra-lean", "mild", "spicy", "sharp",
    "extra-sharp", "aged", "soft", "hard", "firm", "extra-firm", "silken", "thick", "thick-cut", "thin", "thin-sliced",
    "plain", "all-purpose", "kosher", "sea", "table", "fine", "coarse", "flaky", "granulated", "powdered", "confectioner",
    "baby", "petite", "mini", "jumbo", "mixed", "smoked", "toasted", "roasted", "grilled", "canned", "stewed",
    "oven-roasted", "fire-roasted", "sun-dried", "slow-cooked", "store-bought", "homemade",
    "instant", "quick-cooking", "old-fashioned", "rolled", "steel-cut", "long-grain", "short-grain",
    "extra-virgin", "virgin", "low-sodium", "reduced-sodium", "no-salt-added", "reduced-fat", "fat-free",
    "nonfat", "skim", "part-skim", "full-fat", "flat-leaf", "curly", "bone-in", "skin-on", "center-cut",
    "regular", "classic", "original", "pure", "natural", "organic", "active", "rapid-rise", "condensed", "evaporated", "distilled",
    "italian", "mexican", "greek", "french", "english", "spanish", "asian", "chinese", "japanese", "thai", "indian", "american",
    "baking", "cooking", "whipping", "frying", "dipping", "sour", "cream", "simple", "half", "pie", "pizza", "cake",
    "bread", "ice", "stir", "fry", "top", "dry", "wet", "boiling", "simmering", "pan", "sheet", "stock", "season",
)

/** Head nouns that name a whole family ("lime juice", "taco seasoning") or collide with verbs/prose ("red pepper flakes", "bay leaves"). */
private val GENERIC_HEADS = setOf(
    "juice", "seasoning", "mix", "dressing", "rub", "glaze", "spray", "blend", "marinade",
    "topping", "filling", "crust", "drizzle", "dip",
    "leaf", "paste", "powder", "extract", "flake", "seed", "zest", "peel", "rind", "crumb",
    "syrup", "base", "concentrate", "coating", "batter", "dough",
    "piece", "slice", "strip", "chunk", "cube", "wedge", "half", "quarter",
)

/** Head nouns of things made from the food named before them ("beef broth", "garlic powder", "garlic butter"). */
private val MADE_FROM = setOf(
    "broth", "stock", "bouillon", "powder", "paste", "oil", "extract", "flake", "seed",
    "milk", "water", "vinegar", "salt", "fat", "drippings", "dripping", "puree", "purée", "concentrate", "base",
    "zest", "peel", "rind", "butter", "cream", "syrup", "glaze", "marinade", "rub", "seasoning", "dressing",
    "mix", "blend", "flour", "meal", "starch", "crumb", "leaf",
)

/** Whole foods that shouldn't match a sauce made from them ("Bake the fish" != "fish sauce", "Dice tomatoes" != "tomato sauce"). */
private val SAUCE_SOURCE_FOODS = setOf(
    "fish", "oyster", "clam", "crab", "shrimp", "lobster", "duck", "chicken", "beef", "pork", "steak",
    "tomato", "apple", "cranberry", "plum", "mango", "peanut", "garlic", "onion", "mushroom",
    "cheese", "butter", "cream", "chili", "chile", "pepper", "mint", "herb", "lemon", "lime", "orange", "coconut",
)

/** Units, amounts and prep words that don't say what the ingredient is. */
private val IGNORED = setOf(
    "a", "an", "of", "the", "and", "or", "to", "for", "about", "approx", "approximately", "plus", "more", "taste", "optional",
    "cup", "tablespoon", "tbsp", "tbs", "teaspoon", "tsp", "ounce", "oz", "pound", "lb", "gram", "g", "kg",
    "ml", "l", "liter", "litre", "pint", "quart", "gallon", "can", "jar", "package", "pkg", "packet", "bag",
    "box", "bunch", "clove", "pinch", "dash", "handful", "slice", "piece", "stick", "sprig", "head", "stalk",
    "large", "medium", "small", "big", "extra", "fresh", "freshly", "dried", "frozen", "whole", "ripe",
    "chopped", "diced", "minced", "sliced", "shredded", "grated", "crushed", "ground-up", "peeled", "cubed", "chunked",
    "melted", "softened", "beaten", "divided", "halved", "quartered", "trimmed", "rinsed", "drained", "undrained",
    "finely", "roughly", "thinly", "coarsely", "lightly", "packed", "unpacked", "heaping", "level", "room", "temperature",
    "boneless", "skinless", "cooked", "uncooked", "raw", "cut", "into", "against", "grain", "bite-sized", "bite", "inch",
    "seeded", "deseeded", "cored", "stemmed", "pitted", "hulled", "zested", "juiced", "crumbled", "flaked", "torn", "broken",
    "whisked", "sifted", "blanched", "warmed", "thawed", "defrosted", "squeezed", "deveined", "butterflied", "pounded",
    "flattened", "scored", "patted", "serve", "serving", "garnish", "needed", "desired", "x", "-", "/",
)

/**
 * For each ingredient, the first step that uses it. Later steps show it by name only
 * ("Steak, from step 2"), so the amount isn't listed as if it were added again.
 */
fun firstSteps(perStep: List<List<Int>>): Map<Int, Int> {
    val first = mutableMapOf<Int, Int>()
    perStep.forEachIndexed { step, used -> used.forEach { first.putIfAbsent(it, step) } }
    return first
}
