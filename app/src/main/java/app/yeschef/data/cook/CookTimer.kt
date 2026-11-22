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
) {
    val running: Boolean get() = endAt != null && !done
    fun remainingMs(now: Long): Long = when {
        done -> 0
        endAt != null -> (endAt - now).coerceAtLeast(0)
        else -> pausedRemainingMs
    }
}
