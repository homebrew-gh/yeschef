package app.yeschef.data.cook

import kotlinx.serialization.Serializable

/**
 * A note spoken (or typed) in cook mode. Kept on the phone until the cook is done,
 * then saved to the recipe's timeline in Mealie as one "Cook notes" entry that an
 * assistant can read through the AI connector.
 */
@Serializable
data class CookNote(
    /** When it was taken, epoch ms. Also its identity in the list. */
    val at: Long,
    val text: String,
    /** The step on screen when it was taken, from 1; null before the method. */
    val step: Int? = null,
    /** A few words naming that step, so the note still reads right if the steps change. */
    val stepLabel: String? = null,
)

object CookNotes {
    /** Subject of the Mealie timeline entry. The AI connector looks for it. */
    const val SUBJECT = "Cook notes"

    /** "Sear the steak" from a step's title, or the first words of its text. */
    fun stepLabel(title: String?, text: String): String? {
        val what = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: text.split(Regex("(?<=[.!?;])\\s+")).firstOrNull()?.trim()?.trimEnd('.', '!', ';', ',')
        if (what.isNullOrEmpty() || what.none(Char::isLetter)) return null
        return shorten(what)
    }

    /**
     * The timeline message: optional context lines (scale, co-cooked dishes) followed by
     * one line per note, in the order taken.
     * "6:42 PM, step 3 (Sear the steak): needed 2 more minutes a side"
     */
    fun message(
        notes: List<CookNote>,
        clock: (Long) -> String,
        contextLines: List<String> = emptyList(),
    ): String {
        val body = notes.filter { it.text.isNotBlank() }.sortedBy { it.at }.map { n ->
            val where = when {
                n.step == null -> ""
                n.stepLabel.isNullOrBlank() -> ", step ${n.step}"
                else -> ", step ${n.step} (${n.stepLabel})"
            }
            "- ${clock(n.at)}$where: ${n.text.trim()}"
        }
        if (body.isEmpty()) return ""
        val header = contextLines.map { it.trim() }.filter { it.isNotEmpty() }
        return (header + body).joinToString("\n")
    }

    /** Context line when a recipe was scaled away from its base servings, or null at 1×. */
    fun scaleContext(scale: Double, baseServings: Double?): String? {
        if (kotlin.math.abs(scale - 1.0) < 0.01) return null
        val mult = "×${formatAmount(scale)}"
        return if (baseServings != null && baseServings > 0) {
            "Cooked at ${formatAmount(baseServings * scale)} servings ($mult)"
        } else {
            "Cooked at $mult"
        }
    }

    /** Context line listing the other dishes cooked alongside this one, or null if solo. */
    fun mealContext(otherDishNames: List<String>): String? {
        val names = otherDishNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (names.isEmpty()) null else "Cooked with: ${names.joinToString(", ")}"
    }

    /** Short preview for the snackbar confirmation right after dictating a note. */
    fun snippet(text: String, max: Int = 48): String = shorten(text.trim(), max)

    /**
     * Which step the cook is looking at, from the steps on screen: the first one at
     * least half in view. [visible] holds (step index, top offset, height) of the step
     * rows laid out now. Null when no step is on screen (still on the ingredients).
     */
    fun stepInView(visible: List<Triple<Int, Int, Int>>, viewportStart: Int): Int? =
        visible.sortedBy { it.second }
            .firstOrNull { (_, top, height) -> top + height / 2 >= viewportStart }
            ?.first
            ?: visible.maxByOrNull { it.second }?.first

    private fun shorten(s: String, max: Int = 40): String {
        if (s.length <= max) return s
        val cut = s.take(max).substringBeforeLast(' ').ifEmpty { s.take(max) }
        return cut.trimEnd(',', ' ') + "…"
    }
}
