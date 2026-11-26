package app.yeschef.data.cook

/** One recipe going into a combined cook session. Times are Mealie's free-text fields. */
data class MealRecipeIn(
    val name: String,
    val prepTime: String?,
    val performTime: String?,
    val totalTime: String?,
    val steps: List<MealStepIn>,
)

data class MealStepIn(val text: String, val durationSeconds: Int? = null)

/** How a recipe fits into the meal: when it starts, relative to the moment the meal is ready. */
data class MealRecipePlan(
    val recipeIndex: Int,
    /** Seconds before the ready time that this recipe starts. */
    val startsBefore: Long,
    val totalSeconds: Long,
    /** False when Mealie had no prep, cook or total time and the length was estimated from the steps. */
    val timesKnown: Boolean,
)

/** One step on the merged timeline. [startsBefore] counts back from the ready time. */
data class MealTimelineStep(
    val recipeIndex: Int,
    val stepIndex: Int,
    val startsBefore: Long,
    /** Scheduled span of this step on the timeline, in seconds. */
    val durationSeconds: Long = 0L,
    /** True for hands-off waiting steps (baking, simmering, marinating, resting). */
    val passive: Boolean = false,
)

data class MealTimeline(val recipes: List<MealRecipePlan>, val steps: List<MealTimelineStep>) {
    /** How long before the ready time the first recipe has to start. */
    val leadSeconds: Long get() = recipes.maxOfOrNull { it.startsBefore } ?: 0L
}

/** Used for a step with no time of its own when nothing else tells us how long it takes. */
private const val UNTIMED_STEP = 5 * 60L
private const val MIN_STEP = 60L

/**
 * Lays several recipes out so they all finish together. Each recipe starts its total time
 * before the meal is ready (Mealie's total time, else prep + cook). A recipe with no times
 * gets an estimate from its steps: their timers plus five minutes for each untimed step.
 *
 * Inside a recipe, steps keep their order. A step with a timer ("simmer 20 minutes") takes
 * that long; the rest of the recipe's time is shared among the other steps. The merged
 * list is sorted by start, earliest first, so sides slot in while the main is in the oven.
 */
fun buildMealTimeline(recipes: List<MealRecipeIn>): MealTimeline {
    val plans = mutableListOf<MealRecipePlan>()
    val steps = mutableListOf<MealTimelineStep>()
    recipes.forEachIndexed { r, recipe ->
        val stepTimes = recipe.steps.map { stepSeconds(it) }
        val known = recipeSeconds(recipe)
        val total = known ?: estimate(stepTimes)
        plans += MealRecipePlan(r, total, total, known != null)

        val lengths = spread(stepTimes, total)
        var elapsed = 0L
        lengths.forEachIndexed { s, len ->
            val explicitSec = stepTimes[s] ?: 0L
            val passive = isPassiveStep(recipe.steps[s].text, explicitSec)
            steps += MealTimelineStep(r, s, total - elapsed, durationSeconds = len, passive = passive)
            elapsed += len
        }
    }
    // Earliest first; at the same moment the longer-running recipe (usually the main) leads.
    val order = steps.sortedWith(
        compareByDescending<MealTimelineStep> { it.startsBefore }
            .thenByDescending { plans[it.recipeIndex].totalSeconds }
            .thenBy { it.recipeIndex }
            .thenBy { it.stepIndex },
    )
    return MealTimeline(plans, order)
}

/** Total time from Mealie: "total" if set, else prep + cook; null when none parse. */
fun recipeSeconds(recipe: MealRecipeIn): Long? {
    parseRecipeTime(recipe.totalTime)?.takeIf { it > 0 }?.let { return it }
    // A no-cook recipe stores cook time "0 minutes": it is just its prep.
    val prep = parseRecipeTime(recipe.prepTime)
    val cook = parseRecipeTime(recipe.performTime)
    if (prep == null && cook == null) return null
    return ((prep ?: 0L) + (cook ?: 0L)).takeIf { it > 0 }
}

private val zero = Regex("""(?i)^0+(?:\.0+)?\s*(?:h|hrs?|hours?|m|mins?|minutes?|s|secs?|seconds?)?$""")
private val iso = Regex("""(?i)^P(?:T)?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$""")

/**
 * Mealie stores times as text: "1 hour 30 minutes", "45 min", "PT1H30M", or a bare "40"
 * (minutes). Returns seconds; 0 for an explicit zero such as "0 minutes" (no cooking);
 * null when it is blank or unreadable (unknown).
 */
fun parseRecipeTime(raw: String?): Long? {
    val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    iso.matchEntire(s)?.let { m ->
        val (h, min, sec) = m.destructured
        val total = (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)
        return total
    }
    s.toDoubleOrNull()?.let { return (it * 60).toLong().coerceAtLeast(0) }
    if (zero.matches(s)) return 0L
    // "1:30" as hours and minutes.
    Regex("""^(\d+):(\d{2})$""").matchEntire(s)?.let { m ->
        return (m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60).takeIf { it > 0 }
    }
    val found = detectDurations(s)
    return found.sumOf { it.seconds }.takeIf { it > 0 }
}

/** A step's own time: Mealie's step duration, else the longest time in its text. */
private fun stepSeconds(step: MealStepIn): Long? =
    step.durationSeconds?.toLong()?.takeIf { it > 0 }
        ?: detectDurations(step.text).maxOfOrNull { it.seconds }

private fun estimate(stepTimes: List<Long?>): Long =
    stepTimes.sumOf { it ?: UNTIMED_STEP }.coerceAtLeast(UNTIMED_STEP)

/**
 * Splits [total] across the steps. Timed steps keep their time when it fits; the rest is
 * shared evenly among untimed steps. If the timers alone overrun the total, everything is
 * scaled down so the recipe still finishes on time.
 */
private fun spread(stepTimes: List<Long?>, total: Long): List<Long> {
    if (stepTimes.isEmpty()) return emptyList()
    val timed = stepTimes.sumOf { it ?: 0L }
    val untimed = stepTimes.count { it == null }
    val share = if (untimed == 0) 0L else ((total - timed) / untimed).coerceAtLeast(MIN_STEP)
    val raw = stepTimes.map { it ?: share }
    val sum = raw.sum()
    if (sum <= 0 || sum == total) return raw
    if (sum < total) {
        // Spare time goes to the first untimed step (usually prep), else the first step,
        // so a "simmer 20 minutes" stays 20 minutes.
        val at = stepTimes.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        return raw.toMutableList().also { it[at] += total - sum }
    }
    // The timers overrun the recipe's stated time: squeeze, keeping the proportions.
    val scaled = raw.map { it * total / sum }.toMutableList()
    scaled[scaled.lastIndex] += total - scaled.sum()
    return scaled
}

private val PASSIVE_VERBS = Regex(
    """(?i)\b(bake|roast|simmer|braise|slow\s+cook|cook\s+on\s+(?:low|high)|marinate|chill|refrigerate|freeze|rest|let\s+(?:sit|stand|cool|rest)|rise|proof|steep|soak|brine|smoke|poach|steam|boil)\b""",
)
private val ACTIVE_OVERRIDE = Regex(
    """(?i)\b(stir(?:ring)?\s+constantly|whisk(?:ing)?\s+constantly|continuously|without\s+stopping)\b""",
)

/** True when a timed step (>= 5 min) is mostly hands-off waiting rather than active work. */
fun isPassiveStep(text: String, stepTimerSeconds: Long): Boolean =
    stepTimerSeconds >= 5 * 60L && PASSIVE_VERBS.containsMatchIn(text) && !ACTIVE_OVERRIDE.containsMatchIn(text)
