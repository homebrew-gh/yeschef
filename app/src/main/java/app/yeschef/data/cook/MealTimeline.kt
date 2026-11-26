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

/** How a recipe fits into the meal's timed cooking phase, relative to the ready time. */
data class MealRecipePlan(
    val recipeIndex: Int,
    /** Seconds before the ready time that this recipe's timed cooking phase starts (0 for prep-only dishes). */
    val startsBefore: Long,
    /** Duration of this recipe's timed cooking phase in seconds (0 for prep-only dishes). */
    val totalSeconds: Long,
    /** False when Mealie had no prep, cook or total time and the length was estimated from the steps. */
    val timesKnown: Boolean,
    /** Stated prep time from Mealie (if any), for reference alongside the untimed prep checklist. */
    val prepSeconds: Long = 0L,
)

/** An untimed mise-en-place step to do before starting the cooking clock. */
data class MealPrepStep(
    val recipeIndex: Int,
    val stepIndex: Int,
)

/** An oven temperature mentioned in a recipe's steps. */
data class OvenRequirement(
    val recipeIndex: Int,
    val recipeName: String,
    val tempLabel: String,
    val tempF: Int,
)

/** One step on the merged timed cooking timeline. [startsBefore] counts back from the ready time. */
data class MealTimelineStep(
    val recipeIndex: Int,
    val stepIndex: Int,
    val startsBefore: Long,
    /** Scheduled span of this step on the timeline, in seconds. */
    val durationSeconds: Long = 0L,
    /** True for hands-off waiting steps (baking, simmering, marinating, resting). */
    val passive: Boolean = false,
)

data class MealTimeline(
    val recipes: List<MealRecipePlan>,
    val steps: List<MealTimelineStep>,
    val prepSteps: List<MealPrepStep> = emptyList(),
    val ovenRequirements: List<OvenRequirement> = emptyList(),
    /** Names of dishes whose steps call for bringing water to a boil. */
    val boilWaterDishes: List<String> = emptyList(),
) {
    /** How long before the ready time the first timed cooking step starts. */
    val leadSeconds: Long get() = recipes.maxOfOrNull { it.startsBefore } ?: 0L

    /** True when two dishes require oven temperatures more than 15°F apart. */
    val hasOvenConflict: Boolean
        get() {
            if (ovenRequirements.size < 2) return false
            val minF = ovenRequirements.minOf { it.tempF }
            val maxF = ovenRequirements.maxOf { it.tempF }
            return maxF - minF > 15
        }
}

/** Generous default for untimed mid-cook transition steps ("drain, smash, drizzle" / "rest and carve"). */
private const val MID_COOK_UNTIMED = 5 * 60L

/**
 * Separates untimed prep work from the timed cooking clock, then lays out the timed cooking
 * steps so every dish lands together.
 *
 * Leading untimed steps of each dish (chopping, trimming, rubbing, preheating) — and all steps
 * of no-cook dishes — go into [MealTimeline.prepSteps] with no wall-clock timestamps, because
 * prep speed and oven/burner warm-up vary by kitchen.
 *
 * Inside the timed cooking phase, explicit step timers are summed (so "bake 25 min, uncovered
 * 20 min, rest 15 min" counts as 60 min) and are never squeezed shorter than the step says.
 */
fun buildMealTimeline(recipes: List<MealRecipeIn>): MealTimeline {
    val plans = mutableListOf<MealRecipePlan>()
    val timedSteps = mutableListOf<MealTimelineStep>()
    val prepSteps = mutableListOf<MealPrepStep>()
    val ovens = mutableListOf<OvenRequirement>()
    val boilWater = mutableListOf<String>()

    recipes.forEachIndexed { r, recipe ->
        // Detect oven temperatures and water-boiling setup cues across all steps.
        detectOvenTemp(recipe.steps)?.let { (label, tempF) ->
            ovens += OvenRequirement(r, recipe.name, label, tempF)
        }
        if (recipe.steps.any { BOIL_WATER.containsMatchIn(it.text) }) {
            boilWater += recipe.name
        }

        val stepTimes = recipe.steps.map { stepSeconds(it) }
        val firstTimedIdx = stepTimes.indexOfFirst { it != null }
        val statedPrep = parseRecipeTime(recipe.prepTime) ?: 0L
        val statedCook = parseRecipeTime(recipe.performTime)
        val statedTotal = parseRecipeTime(recipe.totalTime)
        val timesKnown = statedPrep > 0L || statedCook != null || (statedTotal != null && statedTotal > 0L)

        if (firstTimedIdx < 0) {
            // No-cook or fully untimed dish: everything belongs in the untimed Prep & Setup list.
            recipe.steps.indices.forEach { s -> prepSteps += MealPrepStep(r, s) }
            val prepEstimate = statedPrep.takeIf { it > 0 }
                ?: statedTotal?.takeIf { it > 0 }
                ?: (recipe.steps.size * MID_COOK_UNTIMED).coerceAtLeast(if (recipe.steps.isEmpty()) 0L else MID_COOK_UNTIMED)
            plans += MealRecipePlan(
                recipeIndex = r,
                startsBefore = 0L,
                totalSeconds = 0L,
                timesKnown = timesKnown,
                prepSeconds = prepEstimate,
            )
            return@forEachIndexed
        }

        // Leading untimed steps go to Prep & Setup (no clock pressure).
        for (s in 0 until firstTimedIdx) {
            prepSteps += MealPrepStep(r, s)
        }

        val cookStepTimes = stepTimes.subList(firstTimedIdx, stepTimes.size)
        val timedSum = cookStepTimes.sumOf { it ?: 0L }
        val midUntimedCount = cookStepTimes.count { it == null }
        // Never squeeze explicit timers shorter than what the steps actually require.
        val minCookSeconds = timedSum + midUntimedCount * MID_COOK_UNTIMED

        val statedCookSpan = when {
            statedCook != null && statedCook > 0L -> statedCook
            statedTotal != null && statedTotal > 0L -> (statedTotal - statedPrep).coerceAtLeast(0L)
            else -> null
        }
        val cookTotal = maxOf(minCookSeconds, statedCookSpan ?: 0L)

        plans += MealRecipePlan(
            recipeIndex = r,
            startsBefore = cookTotal,
            totalSeconds = cookTotal,
            timesKnown = timesKnown,
            prepSeconds = statedPrep,
        )

        val lengths = spreadGenerous(cookStepTimes, cookTotal)
        var elapsed = 0L
        lengths.forEachIndexed { offset, len ->
            val s = firstTimedIdx + offset
            val explicitSec = cookStepTimes[offset] ?: 0L
            val passive = isPassiveStep(recipe.steps[s].text, explicitSec)
            timedSteps += MealTimelineStep(r, s, cookTotal - elapsed, durationSeconds = len, passive = passive)
            elapsed += len
        }
    }

    // Earliest first; at the same moment the longer-running recipe (usually the main) leads.
    val order = timedSteps.sortedWith(
        compareByDescending<MealTimelineStep> { it.startsBefore }
            .thenByDescending { plans[it.recipeIndex].totalSeconds }
            .thenBy { it.recipeIndex }
            .thenBy { it.stepIndex },
    )
    return MealTimeline(
        recipes = plans,
        steps = order,
        prepSteps = prepSteps,
        ovenRequirements = ovens,
        boilWaterDishes = boilWater,
    )
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

/** A step's own time: Mealie's step duration, else the sum of sequential durations in its text. */
private fun stepSeconds(step: MealStepIn): Long? =
    step.durationSeconds?.toLong()?.takeIf { it > 0 }
        ?: detectDurations(step.text).sumOf { it.seconds }.takeIf { it > 0 }

/**
 * Splits [total] across the timed-phase steps without ever shrinking explicit step timers.
 * Any surplus over explicit timers is distributed evenly across untimed mid-cook transitions
 * (at least [MID_COOK_UNTIMED] each), or added as cushion on the first step if all steps are timed.
 */
private fun spreadGenerous(stepTimes: List<Long?>, total: Long): List<Long> {
    if (stepTimes.isEmpty()) return emptyList()
    val timed = stepTimes.sumOf { it ?: 0L }
    val untimed = stepTimes.count { it == null }
    val share = if (untimed == 0) 0L else ((total - timed) / untimed).coerceAtLeast(MID_COOK_UNTIMED)
    val raw = stepTimes.map { it ?: share }.toMutableList()
    val remainder = total - raw.sum()
    if (remainder > 0) {
        val target = stepTimes.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        raw[target] += remainder
    }
    return raw
}

private val OVEN_TEMP = Regex("""(?i)\b(\d{3})\s*°?\s*(F|C)\b""")
private val OVEN_CONTEXT = Regex("""(?i)\b(oven|preheat|bake|roast|broil)\b""")
private val BOIL_WATER = Regex("""(?i)\b(?:bring\b.{0,40}\bto\s+a\s+boil|boil\b.{0,30}\b(?:water|potatoes|pasta|noodles))\b""")

/** Extracts the primary oven temperature from a recipe's steps, e.g. ("450°F", 450). */
private fun detectOvenTemp(steps: List<MealStepIn>): Pair<String, Int>? {
    for (step in steps) {
        if (!OVEN_CONTEXT.containsMatchIn(step.text)) continue
        val match = OVEN_TEMP.find(step.text) ?: continue
        val num = match.groupValues[1].toIntOrNull() ?: continue
        val unit = match.groupValues[2].uppercase()
        val tempF = if (unit == "C") (num * 9 / 5) + 32 else num
        if (tempF in 150..550) return "${num}°$unit" to tempF
    }
    return null
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
