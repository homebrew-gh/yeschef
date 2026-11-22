package app.yeschef.data.cook

/**
 * A short label for a step's timer, so several timers running at once can be told
 * apart: "Step 1 · Stir in the marinara and simmer".
 *
 * Uses the step's own title from Mealie when it has one. Otherwise it takes the words
 * just before the time in the step text ("...and simmer for 15 minutes").
 */
fun timerStepLabel(stepNumber: Int, stepTitle: String?, stepText: String, durationText: String): String {
    val prefix = "Step $stepNumber"
    val what = stepTitle?.trim()?.takeIf { it.isNotEmpty() } ?: actionBefore(stepText, durationText)
    return if (what.isNullOrEmpty()) prefix else "$prefix · ${shorten(what)}"
}

private fun actionBefore(text: String, durationText: String): String? {
    val sentences = text.split(Regex("(?<=[.!?;])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    val sentence = sentences.firstOrNull { durationText.isNotEmpty() && it.contains(durationText) } ?: sentences.firstOrNull() ?: return null
    val clauses = sentence.split(',')
    val at = if (durationText.isEmpty()) -1 else clauses.indexOfFirst { it.contains(durationText) }
    if (at < 0) return tidy(clauses.first())
    // Words before the time in its clause, or the clause before it when the time stands
    // alone ("Brown the beef, about 8 minutes").
    val before = tidy(clauses[at].substringBefore(durationText))
    return before ?: clauses.getOrNull(at - 1)?.let(::tidy)
}

private val LEADING = Regex("(?i)^(then|and|meanwhile|now|next|finally|after that)\\b\\s*")
private val TRAILING = Regex("(?i)\\s*\\b(for|about|around|approximately|roughly|until|in|another|a further|at least|over|or|and)\\s*$")

private fun tidy(raw: String): String? {
    var s = raw.trim().trimEnd('.', ',', ';', ':', '!').trim()
    while (true) {
        val next = s.replace(LEADING, "").replace(TRAILING, "").trim()
        if (next == s) break
        s = next
    }
    if (s.isEmpty() || s.none(Char::isLetter)) return null
    return s.replaceFirstChar { it.uppercase() }
}

private fun shorten(s: String, max: Int = 40): String {
    if (s.length <= max) return s
    val cut = s.take(max).substringBeforeLast(' ').ifEmpty { s.take(max) }
    return cut.trimEnd(',', ' ') + "…"
}
