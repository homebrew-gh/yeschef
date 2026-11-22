package app.yeschef.data.cook

/** A time found in a recipe step, e.g. "25 minutes" or "1 hr 15 min". */
data class DetectedDuration(val seconds: Long, val text: String)

private const val NUM = """(?:\d+(?:[.,]\d+)?(?:\s+\d+/\d+)?|\d+/\d+|\d*\s*[½¼¾⅓⅔])"""
private const val UNIT = """(hours?|hrs?|minutes?|mins?|seconds?|secs?)"""

private val amountPattern = Regex("""(?i)(?<![\w/.])($NUM)(?:\s*(?:-|–|to)\s*($NUM))?\s*$UNIT\b""")
private val wordPattern = Regex("""(?i)\b(half an hour|an hour and a half|an hour|a minute)\b""")
private val joiner = Regex("""(?i)^\s*(?:,|and|,\s*and)?\s*$""")

private data class Hit(val start: Int, val end: Int, val seconds: Long, val unitRank: Int)

/**
 * Finds cooking times in free text. Ranges ("10-12 minutes") use the lower bound so
 * the timer goes off when it's time to check. Adjacent parts such as "1 hour 30 minutes"
 * are combined into one timer.
 */
fun detectDurations(text: String): List<DetectedDuration> {
    val hits = mutableListOf<Hit>()
    for (m in amountPattern.findAll(text)) {
        val amount = parseNumber(m.groupValues[1]) ?: continue
        val (unitSeconds, rank) = unitOf(m.groupValues[3])
        hits += Hit(m.range.first, m.range.last + 1, (amount * unitSeconds).toLong(), rank)
    }
    for (m in wordPattern.findAll(text)) {
        if (hits.any { m.range.first < it.end && it.start <= m.range.last }) continue
        val seconds = when (m.value.lowercase()) {
            "half an hour" -> 1800L
            "an hour and a half" -> 5400L
            "an hour" -> 3600L
            else -> 60L
        }
        hits += Hit(m.range.first, m.range.last + 1, seconds, if (seconds >= 3600) 3 else 2)
    }
    hits.sortBy { it.start }

    val merged = mutableListOf<Hit>()
    for (hit in hits) {
        val prev = merged.lastOrNull()
        if (prev != null && prev.unitRank > hit.unitRank && joiner.matches(text.substring(prev.end, hit.start))) {
            merged[merged.lastIndex] = Hit(prev.start, hit.end, prev.seconds + hit.seconds, hit.unitRank)
        } else {
            merged += hit
        }
    }
    return merged.filter { it.seconds > 0 }.map { DetectedDuration(it.seconds, text.substring(it.start, it.end).trim()) }
}

private fun unitOf(unit: String): Pair<Long, Int> = when {
    unit.startsWith("h", ignoreCase = true) -> 3600L to 3
    unit.startsWith("m", ignoreCase = true) -> 60L to 2
    else -> 1L to 1
}

private val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

private fun parseNumber(raw: String): Double? {
    val s = raw.trim()
    fractions[s.lastOrNull()]?.let { frac ->
        val whole = s.dropLast(1).trim()
        return frac + (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null)
    }
    val parts = s.split(Regex("""\s+"""))
    var total = 0.0
    for (part in parts) {
        total += if ('/' in part) {
            val (n, d) = part.split('/').map { it.toDoubleOrNull() ?: return null }
            if (d == 0.0) return null else n / d
        } else {
            part.replace(',', '.').toDoubleOrNull() ?: return null
        }
    }
    return total
}

/** "1:05:00", "12:30", "0:45". */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "1 hr 15 min", "45 min", "30 sec". */
fun formatShort(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it hr" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 && h == 0L }?.let { "$it sec" },
    ).joinToString(" ").ifEmpty { "0 sec" }
}
