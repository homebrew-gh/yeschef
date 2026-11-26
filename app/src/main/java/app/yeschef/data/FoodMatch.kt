package app.yeschef.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Shared food-matching and combined-line splitting logic mirroring the Mealie StartOS MCP.
 * Keeps mobile imports, hand-edited recipes, and the shopping-list magic wand from
 * accepting aggressive fuzzy mismatches ("simple syrup" -> "maple syrup", "peanut butter"
 * -> "butter", "chicken broth" -> "chicken") or creating junk foods from combined lines
 * ("salt and pepper, to taste") and prep words ("large finely chopped yellow onions").
 */
object FoodMatch {

    // ---- Splitting combined bare food lines ("salt and pepper, to taste") ----

    private val COMPOUND_FOODS = listOf(
        "half and half", "mac and cheese", "macaroni and cheese", "salt and vinegar",
        "sweet and sour", "sweet and spicy", "hot and sour", "bread and butter",
        "peanut butter and jelly", "cookies and cream", "fish and chips", "surf and turf",
        "franks and beans", "pork and beans", "beans and franks", "rice and beans",
        "red beans and rice", "chips and salsa", "oil and vinegar", "cream and sugar",
        "cheese and crackers", "pb and j",
    )

    private val NOTE_PHRASE_RE = Regex(
        """[\s,]+((?:to taste|as needed|as desired|optional|for (?:serving|garnish|garnishing|frying|greasing|topping|dipping))\.?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val PREP_WORDS = Regex(
        """\b(diced|chopped|minced|sliced|peeled|grated|shredded|crushed|cubed|halved|quartered|softened|melted|beaten|divided|drained|rinsed|trimmed|seeded|cored|julienned|toasted|room temperature|finely|roughly|thinly)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LEADING_QTY = Regex(
        """^\s*(?:\d|[¼½¾⅓⅔⅛⅜⅝⅞]|a\s+(?:pinch|dash|handful)|one|two|three|four|five|six)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val SPLIT_RE = Regex("""\s*,\s*(?:and\s+|&\s+)?|\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)
    private val FOOD_NAME_RE = Regex("""^[a-z][a-z' -]*$""", RegexOption.IGNORE_CASE)

    /** Splits a quantity-free combined food list ("salt and pepper, to taste") into separate lines. */
    fun splitCombinedIngredient(line: String): List<String> {
        val text = line.trim()
        if (text.isEmpty() || LEADING_QTY.containsMatchIn(text) || '(' in text || ')' in text) return listOf(line)
        val lower = text.lowercase().replace(Regex("""\s*&\s*"""), " and ")
        if (COMPOUND_FOODS.any { it in lower }) return listOf(line)

        var head = text
        var note = ""
        val phrase = NOTE_PHRASE_RE.find(text)
        if (phrase != null) {
            head = text.substring(0, phrase.range.first).trim()
            note = phrase.groupValues[1].trimEnd('.')
        } else {
            val comma = text.lastIndexOf(',')
            if (comma >= 0) {
                val tail = text.substring(comma + 1).trim()
                val continuesList = Regex("""^(?:and|&)\s""", RegexOption.IGNORE_CASE).containsMatchIn(tail) ||
                    (Regex("""\s(?:and|&)\s""", RegexOption.IGNORE_CASE).containsMatchIn(tail) && !PREP_WORDS.containsMatchIn(tail))
                if (!continuesList) {
                    head = text.substring(0, comma).trim()
                    note = tail
                }
            }
        }
        head = head.trimEnd(',', ' ')

        val parts = head.split(SPLIT_RE).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return listOf(line)
        val looksLikeFood: (String) -> Boolean = { p ->
            p.split(Regex("""\s+""")).size <= 4 && !PREP_WORDS.containsMatchIn(p) && FOOD_NAME_RE.matches(p)
        }
        if (!parts.all(looksLikeFood)) return listOf(line)
        return parts.map { p -> if (note.isNotEmpty()) "$p, $note" else p }
    }

    data class SplitPiece(val text: String, val fromIndex: Int)

    fun splitLines(lines: List<String>): List<SplitPiece> = buildList {
        lines.forEachIndexed { i, line ->
            splitCombinedIngredient(line).forEach { add(SplitPiece(it, i)) }
        }
    }

    // ---- Normalization, core food extraction, and fuzzy-match guardrails ----

    private val COMMON_UNITS = listOf(
        "fluid ounce", "fl oz", "tablespoon", "teaspoon", "kilogram", "milliliter",
        "millilitre", "package", "bottle", "handful", "gallon", "jigger", "splash",
        "bundle", "bunch", "clove", "ounce", "pound", "quart", "slice", "piece",
        "sprig", "stick", "pinch", "stalk", "head", "liter", "litre", "pint", "tbsp",
        "drop", "dash", "part", "shot", "gram", "cup", "tsp", "tbs", "can", "jar",
        "pkg", "bag", "box", "oz", "lb", "kg", "ml", "qt", "pt", "g", "l",
    )

    private val LEADING_PREP_WORDS = setOf(
        "large", "medium", "small", "big", "jumbo", "extra", "whole", "ripe",
        "fresh", "freshly", "finely", "roughly", "thinly", "coarsely", "lightly",
        "chopped", "diced", "minced", "sliced", "shredded", "grated", "crushed",
        "peeled", "cubed", "halved", "quartered", "trimmed", "rinsed", "drained",
        "melted", "softened", "beaten", "divided", "seeded", "cored", "julienned",
        "boneless", "skinless", "pitted", "deveined", "cooked", "uncooked", "raw",
        "chilled", "cold", "warm", "hot", "room", "temperature", "heaping", "level",
        "packed", "optional", "organic", "natural", "pure", "local", "quality",
    )

    private val HARMLESS_QUALIFIERS = setOf(
        "organic", "natural", "pure", "local", "raw", "unbleached", "unenriched",
        "plain", "unflavored", "unsweetened", "unsalted", "salted", "kosher",
        "fine", "coarse", "flaky", "table", "iodized", "extra", "virgin",
        "cold", "pressed", "expeller", "low", "reduced", "sodium", "fat", "free",
        "nonfat", "lean", "grass", "fed", "pasture", "raised", "wild", "caught",
        "italian", "flat", "leaf", "curly", "english", "persian", "fresh", "dried",
    )

    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val LEADING_AMOUNT = Regex("""^[\s\d./\-–—¼½¾⅓⅔⅛⅜⅝⅞]+""")

    fun singularWord(w: String): String = when {
        w.length <= 3 -> w
        w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
        w.endsWith("oes") && w.length > 4 -> w.dropLast(2)
        Regex("""(?:ch|sh|x|z)es$""").containsMatchIn(w) && w.length > 4 -> w.dropLast(2)
        w.endsWith("s") && !Regex("""(?:ss|us|is)$""").containsMatchIn(w) -> w.dropLast(1)
        else -> w
    }

    /** Canonical comparison key: lowercase, punctuation/hyphens collapsed, head noun singularized. */
    fun normalizeKey(name: String): String {
        val cleaned = name.lowercase()
            .replace('’', '\'')
            .replace(Regex("""[-–—/.,]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.isEmpty()) return ""
        val tokens = cleaned.split(' ').toMutableList()
        tokens[tokens.lastIndex] = singularWord(tokens.last())
        return tokens.joinToString(" ")
    }

    /** All normalized keys a Mealie food JSON object matches under (name, plural, abbreviation, aliases). */
    fun foodKeys(food: JsonObject): Set<String> {
        val names = buildList {
            listOf("name", "pluralName", "abbreviation", "pluralAbbreviation").forEach { k ->
                food.string(k)?.takeIf { it.isNotBlank() }?.let(::add)
            }
            (food["aliases"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().forEach { alias ->
                alias.string("name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return names.mapNotNull { normalizeKey(it).takeIf(String::isNotEmpty) }.toSet()
    }

    /** Extracts the cleaned core food phrase from a free-text ingredient line. */
    fun extractCoreFood(input: String, note: String? = null, unit: JsonObject? = null): String? {
        var t = input.trim().lowercase()
        if (t.isEmpty()) return null
        t = t.replace(BRACKETED, " ")
        val normNote = note?.trim()?.lowercase().orEmpty()
        if (normNote.isNotEmpty() && normNote in t) {
            val without = t.replace(normNote, " ").trim()
            if (without.replace(Regex("""[,;\s]+"""), "").isNotEmpty()) t = without
        }
        t = t.split(',', ';').first()
        t = t.replace(LEADING_AMOUNT, " ").trim()
        t = t.replace(Regex("""^(?:a|an)\s+"""), "")

        val unitNames = buildList {
            unit?.string("name")?.let(::add)
            unit?.string("pluralName")?.let(::add)
            unit?.string("abbreviation")?.let(::add)
            addAll(COMMON_UNITS)
        }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }

        for (u in unitNames) {
            val m = Regex("""^${Regex.escape(u)}(?:s|es)?\.?(?:\s+|$)""").find(t)
            if (m != null) {
                t = t.substring(m.range.last + 1)
                break
            }
        }
        t = t.replace(Regex("""^of\s+"""), "").replace(Regex("""\s+"""), " ").trim()

        val words = t.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (words.size > 1) {
            val w = words.first().trim('-')
            if (w == "room" && words.getOrNull(1) == "temperature" && words.size > 2) {
                words.removeAt(0)
                words.removeAt(0)
                continue
            }
            if (w in LEADING_PREP_WORDS) {
                words.removeAt(0)
                continue
            }
            break
        }
        t = words.joinToString(" ").trim()
        if (t.isEmpty() || words.size > 5 || !FOOD_NAME_RE.matches(t)) return null
        return t
    }

    /** True when [food] genuinely represents [input] (honoring aliases, plurals, and safe qualifiers). */
    fun isGoodFoodMatch(input: String, food: JsonObject, note: String? = null, unit: JsonObject? = null): Boolean {
        val keys = foodKeys(food)
        if (keys.isEmpty()) return false
        val core = extractCoreFood(input, note, unit)
        if (core == null) {
            val lineKey = normalizeKey(input)
            return keys.any { k -> Regex("""(^| )${Regex.escape(k)}( |$)""").containsMatchIn(lineKey) }
        }
        val coreKey = normalizeKey(core)
        if (coreKey in keys) return true

        val coreTokens = coreKey.split(' ')
        val coreHead = coreTokens.last()
        for (fk in keys) {
            val foodTokens = fk.split(' ')
            if (foodTokens.last() != coreHead) continue
            if (foodTokens.all { it in coreTokens }) {
                val foodSet = foodTokens.toSet()
                if (coreTokens.filterNot { it in foodSet }.all { it in HARMLESS_QUALIFIERS }) return true
            }
            if (coreTokens.all { it in foodTokens }) {
                val coreSet = coreTokens.toSet()
                if (foodTokens.filterNot { it in coreSet }.all { it in HARMLESS_QUALIFIERS }) return true
            }
        }
        return false
    }

    /** Returns the line's cleaned core food phrase when [food] is a fuzzy mismatch, else null. */
    fun ownFoodText(input: String, food: JsonObject, note: String? = null, unit: JsonObject? = null): String? =
        if (isGoodFoodMatch(input, food, note, unit)) null else extractCoreFood(input, note, unit)

    /** Finds an existing Mealie food matching [name] by exact name, plural, abbreviation, or alias. */
    fun findMatchingFood(name: String, foods: List<JsonObject>): JsonObject? {
        val exact = name.trim().lowercase()
        if (exact.isEmpty()) return null
        foods.firstOrNull { f ->
            listOf("name", "pluralName", "abbreviation", "pluralAbbreviation").any {
                f.string(it)?.trim()?.lowercase() == exact
            } || (f["aliases"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().any {
                it.string("name")?.trim()?.lowercase() == exact
            }
        }?.let { return it }

        val target = normalizeKey(name)
        if (target.isEmpty()) return null
        return foods.firstOrNull { target in foodKeys(it) }
    }
}
