package app.yeschef.data.cook

import kotlinx.serialization.Serializable

/** A mid-timer reminder inside a longer cook step, e.g. "Cover with foil" at 25 min in (15 min left). */
@Serializable
data class TimerCheckpoint(
    /** Seconds after the timer starts when this mid-timer alert should buzz. */
    val atSeconds: Long,
    /** Short kitchen cue shown on the timer card and notification, e.g. "Cover with foil". */
    val label: String,
)

/** A time found in a recipe step, e.g. "40-48 min" (with optional upper bound and mid-timer checkpoint). */
data class DetectedDuration(
    val seconds: Long,
    val text: String,
    /** Upper bound for ranges like "40-48 min", or null for exact durations. */
    val maxSeconds: Long? = null,
    /** Optional mid-timer alert folded into this timer (e.g. "last 15 min", "after 20 min", "halfway"). */
    val checkpoint: TimerCheckpoint? = null,
)

private const val NUM = """(?:\d+(?:[.,]\d+)?(?:\s+\d+/\d+)?|\d+/\d+|\d*\s*[½¼¾⅓⅔])"""
private const val UNIT = """(hours?|hrs?|minutes?|mins?|seconds?|secs?)"""

private val amountPattern = Regex("""(?i)(?<![\w/.])($NUM)(?:\s*(?:-|–|to)\s*($NUM))?\s*$UNIT\b""")
private val wordPattern = Regex("""(?i)\b(half an hour|an hour and a half|an hour|a minute)\b""")
private val joiner = Regex("""(?i)^\s*(?:,|and|,\s*and)?\s*$""")

// Patterns used to decide whether a second, shorter duration in the same sentence is nested inside the first.
private val sentenceBreak = Regex("""[.!?\n;]""")
private val sequentialCue = Regex("""(?i)\b(?:then|next|afterwards?|more|additional|extra|longer|another|rest|cool|stand|chill|sit)\b""")
private val remainingCue = Regex("""(?i)\b(?:last|final|remaining)\b""")
private val elapsedCue = Regex("""(?i)\b(?:first|after|initial)\b""")
private val nestedActionCue = Regex("""(?i)\b(?:cover(?:ing|ed)?|uncover(?:ing|ed)?|tent(?:ing|ed)?|foil|lid|flip(?:ping)?|turn(?:ing)?|rotat(?:e|ing)|stir(?:ring)?|bast(?:e|ing)|broil(?:ing)?|add(?:ing)?|top(?:ping)?|sprinkl(?:e|ing))\b""")
private val halfwayCue = Regex("""(?i)\bhalf[\s-]?way\b""")

private data class Hit(
    val start: Int,
    val end: Int,
    val seconds: Long,
    val maxSeconds: Long?,
    val unitRank: Int,
    val checkpoint: TimerCheckpoint? = null,
)

/**
 * Finds cooking times in free text. Ranges ("40-48 minutes") start at the lower bound so
 * the timer rings when it's time to check, while preserving [DetectedDuration.maxSeconds]
 * for the remaining window. Adjacent parts such as "1 hour 30 minutes" are combined into
 * one timer, and nested mid-cook cues ("covering with foil for the last 15 min",
 * "flipping halfway") fold into a single timer with a [TimerCheckpoint].
 */
fun detectDurations(text: String): List<DetectedDuration> {
    val hits = mutableListOf<Hit>()
    for (m in amountPattern.findAll(text)) {
        val amount = parseNumber(m.groupValues[1]) ?: continue
        val (unitSeconds, rank) = unitOf(m.groupValues[3])
        val minSec = (amount * unitSeconds).toLong()
        val maxAmount = m.groupValues[2].takeIf { it.isNotBlank() }?.let(::parseNumber)
        val maxSec = maxAmount?.let { (it * unitSeconds).toLong() }?.takeIf { it > minSec }
        hits += Hit(m.range.first, m.range.last + 1, minSec, maxSec, rank)
    }
    for (m in wordPattern.findAll(text)) {
        if (hits.any { m.range.first < it.end && it.start <= m.range.last }) continue
        val seconds = when (m.value.lowercase()) {
            "half an hour" -> 1800L
            "an hour and a half" -> 5400L
            "an hour" -> 3600L
            else -> 60L
        }
        hits += Hit(m.range.first, m.range.last + 1, seconds, null, if (seconds >= 3600) 3 else 2)
    }
    hits.sortBy { it.start }

    // Combine compound durations like "1 hour 30 minutes".
    val merged = mutableListOf<Hit>()
    for (hit in hits) {
        val prev = merged.lastOrNull()
        if (prev != null && prev.maxSeconds == null && prev.unitRank > hit.unitRank && joiner.matches(text.substring(prev.end, hit.start))) {
            merged[merged.lastIndex] = Hit(
                start = prev.start,
                end = hit.end,
                seconds = prev.seconds + hit.seconds,
                maxSeconds = hit.maxSeconds?.let { prev.seconds + it },
                unitRank = hit.unitRank,
            )
        } else {
            merged += hit
        }
    }

    // Fold nested mid-cook durations (e.g. "bake 40-48 min, covering with foil for at least 15 min")
    // into a single timer with a mid-timer checkpoint instead of two conflicting timers.
    val folded = mutableListOf<Hit>()
    for (hit in merged) {
        val prev = folded.lastOrNull()
        if (prev != null && prev.checkpoint == null && hit.seconds > 0 && hit.seconds < prev.seconds) {
            val gap = text.substring(prev.end, hit.start)
            val cp = detectNestedCheckpoint(gap, prev.seconds, hit.seconds)
            if (cp != null) {
                folded[folded.lastIndex] = prev.copy(checkpoint = cp)
                continue
            }
        }
        folded += hit
    }

    // Detect "halfway" checkpoints when a duration has no checkpoint yet in the same sentence.
    val withHalfway = folded.map { hit ->
        if (hit.checkpoint != null || hit.seconds < 240L) return@map hit
        val sentence = sentenceAround(text, hit.start, hit.end)
        if (!halfwayCue.containsMatchIn(sentence)) return@map hit
        val label = extractCheckpointLabel(sentence, isHalfway = true)
        hit.copy(checkpoint = TimerCheckpoint(atSeconds = hit.seconds / 2, label = label))
    }

    return withHalfway
        .filter { it.seconds > 0 }
        .map {
            DetectedDuration(
                seconds = it.seconds,
                text = text.substring(it.start, it.end).trim(),
                maxSeconds = it.maxSeconds,
                checkpoint = it.checkpoint,
            )
        }
}

private fun detectNestedCheckpoint(gap: String, parentSeconds: Long, subSeconds: Long): TimerCheckpoint? {
    if (sentenceBreak.containsMatchIn(gap)) return null
    if (sequentialCue.containsMatchIn(gap)) return null

    val isRemaining = remainingCue.containsMatchIn(gap)
    val isElapsed = elapsedCue.containsMatchIn(gap)
    val isNestedAction = nestedActionCue.containsMatchIn(gap)

    if (!isRemaining && !isElapsed && !isNestedAction) return null

    val atSeconds = when {
        isRemaining -> parentSeconds - subSeconds
        isElapsed -> subSeconds
        // Phrases like "bake 40-48 min, covered for 15 min" (past participle, already covered) fire after subSeconds;
        // "bake 40-48 min, covering with foil for at least 15 min" fire with subSeconds remaining.
        Regex("""(?i)\b(?:covered|tented)\b""").containsMatchIn(gap) &&
            !Regex("""(?i)\b(?:covering|tenting|uncover(?:ing|ed)?)\b""").containsMatchIn(gap) -> subSeconds
        else -> parentSeconds - subSeconds
    }
    if (atSeconds <= 0L || atSeconds >= parentSeconds) return null
    return TimerCheckpoint(atSeconds = atSeconds, label = extractCheckpointLabel(gap, isHalfway = false))
}

private fun sentenceAround(text: String, start: Int, end: Int): String {
    var s = start
    while (s > 0 && !sentenceBreak.matches(text[s - 1].toString())) s--
    var e = end
    while (e < text.length && !sentenceBreak.matches(text[e].toString())) e++
    return text.substring(s, e)
}

private fun extractCheckpointLabel(clause: String, isHalfway: Boolean): String {
    val lower = clause.lowercase()
    return when {
        "uncover" in lower -> "Uncover"
        "foil" in lower || "tent" in lower -> "Cover with foil"
        "lid" in lower || "cover" in lower -> "Check cover"
        "flip" in lower -> if (isHalfway) "Flip halfway" else "Flip"
        "turn" in lower -> if (isHalfway) "Turn halfway" else "Turn"
        "rotat" in lower -> if (isHalfway) "Rotate halfway" else "Rotate pan"
        "stir" in lower -> if (isHalfway) "Stir halfway" else "Stir"
        "bast" in lower -> "Baste"
        "broil" in lower -> "Switch to broil"
        "cheese" in lower || "top" in lower || "sprinkl" in lower || "add" in lower -> "Add topping"
        isHalfway -> "Halfway check"
        else -> "Mid-cook check"
    }
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

/** "40–48 min" for same-unit minute ranges, or "50 min–1 hr 5 min" across units. */
fun formatRangeShort(minSeconds: Long, maxSeconds: Long?): String {
    if (maxSeconds == null || maxSeconds <= minSeconds) return formatShort(minSeconds)
    if (minSeconds < 3600 && maxSeconds < 3600 && minSeconds % 60 == 0L && maxSeconds % 60 == 0L) {
        return "${minSeconds / 60}–${maxSeconds / 60} min"
    }
    if (minSeconds < 60 && maxSeconds < 60) {
        return "$minSeconds–$maxSeconds sec"
    }
    return "${formatShort(minSeconds)}–${formatShort(maxSeconds)}"
}

/** Label for a step timer chip, e.g. "40–48 min · alert @ 25 min" or "25 min". */
fun formatTimerChip(d: DetectedDuration): String {
    val base = formatRangeShort(d.seconds, d.maxSeconds)
    val cp = d.checkpoint ?: return base
    return "$base · alert @ ${formatShort(cp.atSeconds)}"
}
