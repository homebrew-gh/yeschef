package app.yeschef.data.cook

import kotlinx.serialization.Serializable

/**
 * In-progress single-recipe cook session, saved in SettingsStore so switching tabs or
 * leaving the app never drops cook mode, checked ingredients, completed steps, or scaling.
 */
@Serializable
data class ActiveCookSession(
    val slug: String,
    val name: String,
    val checkedIngredients: Set<Int> = emptySet(),
    val completedSteps: Set<Int> = emptySet(),
    val servings: Double? = null,
    val factor: Double = 1.0,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
) {
    /** Progress line for the in-progress bar, e.g. "2 of 4 steps done" or "Step 2 of 4". */
    fun progressText(): String? = when {
        totalSteps <= 0 -> null
        completedSteps.isNotEmpty() -> {
            val done = completedSteps.count { it in 0 until totalSteps }
            if (done > 0) "$done of $totalSteps steps done" else null
        }
        stepIndex in 0 until totalSteps -> "Step ${stepIndex + 1} of $totalSteps"
        else -> null
    }
}
