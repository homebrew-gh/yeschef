package app.yeschef.data

import app.yeschef.data.cook.baseServings
import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.scaleIngredient
import app.yeschef.data.remote.RecipeDetailDto

/**
 * A recipe laid out for sharing: the same pieces feed the plain-text share and the
 * PDF, so both read the same. Ingredients come at the servings shown on screen.
 */
sealed interface SharePart {
    data class Title(val text: String) : SharePart
    /** Description, times, servings and source: smaller text under the title. */
    data class Info(val text: String) : SharePart
    data class Heading(val text: String) : SharePart
    /** A section label inside the ingredients or steps ("For the sauce"). */
    data class SubHeading(val text: String) : SharePart
    data class Ingredient(val text: String) : SharePart
    data class Step(val number: Int, val text: String) : SharePart
}

fun recipeShareParts(detail: RecipeDetailDto, scale: Double = 1.0): List<SharePart> = buildList {
    add(SharePart.Title(detail.name.trim()))
    detail.description?.trim()?.takeIf { it.isNotEmpty() }?.let { add(SharePart.Info(it)) }

    val base = baseServings(detail.recipeServings, detail.recipeYield)
    val makes = when {
        scale == 1.0 -> detail.recipeYield?.trim()?.takeIf { it.isNotEmpty() }
        base != null -> "${formatAmount(base * scale)} servings"
        else -> "${formatAmount(scale)}× the recipe"
    }
    val facts = listOfNotNull(
        makes?.let { "Makes: $it" },
        detail.prepTime?.trim()?.takeIf { it.isNotEmpty() }?.let { "Prep: $it" },
        detail.performTime?.trim()?.takeIf { it.isNotEmpty() }?.let { "Cook: $it" },
        detail.totalTime?.trim()?.takeIf { it.isNotEmpty() }?.let { "Total: $it" },
    )
    if (facts.isNotEmpty()) add(SharePart.Info(facts.joinToString("  ·  ")))
    detail.orgURL?.trim()?.takeIf { it.isNotEmpty() }?.let { add(SharePart.Info("Source: $it")) }

    if (detail.recipeIngredient.isNotEmpty()) {
        add(SharePart.Heading("Ingredients"))
        detail.recipeIngredient.forEach { i ->
            i.title?.trim()?.takeIf { it.isNotEmpty() }?.let { add(SharePart.SubHeading(it)) }
            if (!isHeadingOnly(i)) add(SharePart.Ingredient(scaleIngredient(ingredientLineText(i).trim(), scale)))
        }
    }

    val steps = detail.recipeInstructions.filter { it.text.isNotBlank() || !it.title.isNullOrBlank() }
    if (steps.isNotEmpty()) {
        add(SharePart.Heading("Steps"))
        var n = 0
        steps.forEach { s ->
            s.title?.trim()?.takeIf { it.isNotEmpty() }?.let { add(SharePart.SubHeading(it)) }
            if (s.text.isNotBlank()) add(SharePart.Step(++n, s.text.trim()))
        }
    }
}

/** The recipe as plain text, for the share sheet (messages, email, notes). */
fun recipeShareText(detail: RecipeDetailDto, scale: Double = 1.0): String {
    val out = StringBuilder()
    var previous: SharePart? = null
    recipeShareParts(detail, scale).forEach { part ->
        // A blank line before each section and between steps; lists stay tight.
        val gap = previous != null && when (part) {
            is SharePart.Heading -> true
            is SharePart.SubHeading, is SharePart.Step -> previous !is SharePart.Heading && previous !is SharePart.SubHeading
            is SharePart.Info -> previous is SharePart.Title
            else -> false
        }
        if (gap) out.append('\n')
        when (part) {
            is SharePart.Title -> out.append(part.text)
            is SharePart.Info -> out.append(part.text)
            is SharePart.Heading -> out.append(part.text.uppercase())
            is SharePart.SubHeading -> out.append(part.text)
            is SharePart.Ingredient -> out.append("• ").append(part.text)
            is SharePart.Step -> out.append(part.number).append(". ").append(part.text)
        }
        out.append('\n')
        previous = part
    }
    return out.toString().trimEnd() + "\n"
}
