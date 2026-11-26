package app.yeschef.data.cook

import kotlinx.serialization.Serializable

/**
 * A kitchen timer. Times are wall-clock epoch millis so a timer survives the app
 * being killed: the alarm fires from AlarmManager and the state is reloaded from disk.
 */
@Serializable
data class CookTimer(
    val id: Int,
    val label: String,
    val durationMs: Long,
    /** When it goes off, while running. Null while paused or done. */
    val endAt: Long? = null,
    /** Time left while paused. */
    val pausedRemainingMs: Long = 0,
    val done: Boolean = false,
    val recipeSlug: String? = null,
    /** The recipe this timer belongs to, shown under the step label. */
    val recipeName: String? = null,
    /** Upper bound for range timers like 40–48 min (total ms from original start), or null. */
    val maxDurationMs: Long? = null,
    /** Optional mid-timer reminder label (e.g. "Cover with foil"), or null. */
    val checkpointLabel: String? = null,
    /** Wall-clock millis when the checkpoint alarm fires while running, or null. */
    val checkpointAt: Long? = null,
    /** Remaining millis until the checkpoint fires while paused. */
    val checkpointPausedRemainingMs: Long = 0,
    /** True once the mid-timer checkpoint has already chimed. */
    val checkpointFired: Boolean = false,
) {
    /** The recipe name, when it adds something the label doesn't already say. */
    val subtitle: String? get() = recipeName?.takeIf { it.isNotBlank() && it != label }

    val running: Boolean get() = endAt != null && !done

    /** Extra time remaining up to the upper bound of a range timer (e.g. 8 min on a 40–48 min bake). */
    val remainingRangeMs: Long get() = maxDurationMs?.let { (it - durationMs).coerceAtLeast(0L) } ?: 0L

    val hasPendingCheckpoint: Boolean
        get() = checkpointLabel != null && !checkpointFired && !done && (checkpointAt != null || checkpointPausedRemainingMs > 0L)

    fun remainingMs(now: Long): Long = when {
        done -> 0
        endAt != null -> (endAt - now).coerceAtLeast(0)
        else -> pausedRemainingMs
    }

    fun checkpointRemainingMs(now: Long): Long = when {
        !hasPendingCheckpoint -> 0
        checkpointAt != null -> (checkpointAt - now).coerceAtLeast(0)
        else -> checkpointPausedRemainingMs
    }
}
