package app.yeschef.data.cook

import kotlinx.serialization.Serializable

/** A heads-up at a step's start time, so a long cook day doesn't need the app open. */
@Serializable
data class MealReminder(val id: Int, val at: Long, val title: String, val lines: List<String>)

/** A meal being cooked: which recipes, when it should be ready, and the reminders set for it. */
@Serializable
data class MealSession(
    val title: String,
    val slugs: List<String>,
    val readyAt: Long,
    val reminders: List<MealReminder> = emptyList(),
    /** When the cook paused the meal; its reminders are off until it resumes. */
    val pausedAt: Long? = null,
) {
    /** The ready time, pushed back by however long the meal has been paused so far. */
    fun readyAtNow(now: Long): Long = readyAt + (pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun paused(at: Long): MealSession = if (pausedAt != null) this else copy(pausedAt = at)

    /** Resuming pushes the ready time and every reminder back by the time spent paused. */
    fun resumed(at: Long): MealSession {
        val p = pausedAt ?: return this
        val shift = (at - p).coerceAtLeast(0)
        return copy(readyAt = readyAt + shift, reminders = reminders.map { it.copy(at = it.at + shift) }, pausedAt = null)
    }

    /** Nudges the ready time and every reminder earlier (negative) or later (positive) by [deltaMs]. */
    fun shifted(deltaMs: Long): MealSession =
        if (deltaMs == 0L) this
        else copy(readyAt = readyAt + deltaMs, reminders = reminders.map { it.copy(at = it.at + deltaMs) })
}

/** After this long with nothing to do, the next step gets a reminder. */
const val IDLE_GAP_SECONDS = 10 * 60L

/**
 * Picks the steps worth a notification: every recipe's first step ("Start the rice"), and
 * any step that comes after a quiet stretch, like shredding the pork after eight hours in
 * the slow cooker. Steps due at the same minute share one reminder. Steps already past
 * [now] are left out.
 */
fun planReminders(
    timeline: MealTimeline,
    recipes: List<MealRecipeIn>,
    readyAt: Long,
    now: Long,
    clock: (Long) -> String,
): List<MealReminder> {
    val due = mutableListOf<Pair<Long, String>>()
    var previousStart: Long? = null
    val lastByRecipe = mutableMapOf<Int, Long>()
    for (step in timeline.steps) {
        val at = readyAt - step.startsBefore * 1000
        val first = step.recipeIndex !in lastByRecipe
        val afterGlobalGap = previousStart?.let { (at - it) / 1000 >= IDLE_GAP_SECONDS } ?: true
        val afterRecipeGap = lastByRecipe[step.recipeIndex]?.let { (at - it) / 1000 >= IDLE_GAP_SECONDS } ?: false
        previousStart = at
        lastByRecipe[step.recipeIndex] = at
        if (!first && !afterGlobalGap && !afterRecipeGap) continue
        if (at < now) continue
        val recipe = recipes[step.recipeIndex]
        val what = recipe.steps.getOrNull(step.stepIndex)?.text?.let(::firstSentence)
        val line = if (first) "Start ${recipe.name}" + (what?.let { ": $it" } ?: "")
        else "${recipe.name}, step ${step.stepIndex + 1}" + (what?.let { ": $it" } ?: "")
        due += at to line
    }
    return due.groupBy { it.first / 60_000 }.toSortedMap().values.mapIndexed { i, group ->
        val at = group.first().first
        val lines = group.map { it.second }
        val head = lines.first().substringBefore(":")
        val title = clock(at) + " · " + head + if (lines.size > 1) " (+${lines.size - 1} more)" else ""
        MealReminder(i + 1, at, title, lines)
    }
}

private fun firstSentence(text: String, max: Int = 70): String? {
    val s = text.trim().split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
    if (s.length <= max) return s
    return s.take(max).substringBeforeLast(' ').trimEnd(',', ' ') + "…"
}
