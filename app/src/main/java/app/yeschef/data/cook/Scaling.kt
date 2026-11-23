package app.yeschef.data.cook

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Scales the amount at the start of an ingredient line: "1 1/2 cups flour" x2 is
 * "3 cups flour". Works on plain text, so unparsed ingredients scale too. Lines
 * that don't start with an amount ("salt to taste") are left alone.
 */
fun scaleIngredient(text: String, factor: Double): String {
    if (factor == 1.0) return text
    val m = LEADING.find(text) ?: return text
    val first = parseAmount(m.groupValues[1]) ?: return text
    val second = m.groupValues[3].takeIf { it.isNotEmpty() }?.let(::parseAmount)
    val scaled = formatAmount(first * factor) + (second?.let { m.groupValues[2] + formatAmount(it * factor) } ?: "")
    return scaled + text.substring(m.range.last + 1)
}

/** The recipe's base servings: Mealie's number if set, else the number at the start of the yield text. */
fun baseServings(recipeServings: Double?, recipeYield: String?): Double? {
    recipeServings?.takeIf { it > 0 }?.let { return it }
    val m = LEADING.find(recipeYield.orEmpty()) ?: return null
    return parseAmount(m.groupValues[1])?.takeIf { it > 0 }
}

/** 1.5 -> "1½", 0.333 -> "⅓", 12.4 -> "12". */
fun formatAmount(value: Double): String {
    if (value <= 0) return "0"
    if (value >= 10) return value.roundToLong().toString()
    val whole = floor(value).toLong()
    val frac = value - whole
    val (fracValue, glyph) = FRACTIONS.minBy { abs(it.first - frac) }
    return when {
        fracValue == 1.0 -> (whole + 1).toString()
        glyph.isEmpty() -> if (whole == 0L) "⅛" else whole.toString()
        whole == 0L -> glyph
        else -> "$whole$glyph"
    }
}

private val FRACTIONS = listOf(
    0.0 to "", 0.125 to "⅛", 0.25 to "¼", 1.0 / 3 to "⅓", 0.375 to "⅜", 0.5 to "½",
    0.625 to "⅝", 2.0 / 3 to "⅔", 0.75 to "¾", 0.875 to "⅞", 1.0 to "",
)

private val GLYPHS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)

private const val NUM = """(?:\d+(?:[.,]\d+)?(?:\s+\d+/\d+|\s*[½¼¾⅓⅔⅛⅜⅝⅞])?|\d+/\d+|[½¼¾⅓⅔⅛⅜⅝⅞])"""

/** An amount at the very start, optionally a range: "2", "1 1/2", "1½", "2-3", "2 to 3". */
private val LEADING = Regex("""^\s*($NUM)(?:(\s*(?:-|–|to)\s*)($NUM))?(?![\d/.]|\s*[x×]\s*\d)""")

/** "1 1/2", "1½", "0.5" or "2,5" to a number; null if it isn't one. */
fun parseAmount(s: String): Double? {
    val t = s.trim().replace(',', '.')
    var total = 0.0
    val parts = Regex("""(\d+(?:\.\d+)?)?\s*(?:(\d+)/(\d+)|([½¼¾⅓⅔⅛⅜⅝⅞]))?""").matchEntire(t) ?: return null
    parts.groupValues[1].takeIf { it.isNotEmpty() }?.let { total += it.toDouble() }
    val n = parts.groupValues[2]; val d = parts.groupValues[3]
    if (n.isNotEmpty() && d.isNotEmpty()) {
        val den = d.toDouble(); if (den == 0.0) return null
        total += n.toDouble() / den
    }
    parts.groupValues[4].firstOrNull()?.let { total += GLYPHS[it] ?: 0.0 }
    return total.takeIf { it > 0 }
}
