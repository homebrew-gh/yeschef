package app.yeschef.data

import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.parseRecipeTime
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.StepDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/** The text of an ingredient line as shown in the app (and edited in the recipe editor). */
fun ingredientLineText(i: IngredientDto): String =
    i.display?.takeIf { it.isNotBlank() }
        ?: i.originalText?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(i.unit?.name, i.food?.name, i.note).joinToString(" ")

/** A heading-only line ("For the sauce") with nothing to buy or measure. */
fun isHeadingOnly(i: IngredientDto): Boolean = ingredientLineText(i).isBlank() && i.food == null

/**
 * One ingredient line in the editor. [original] is the text it had when the editor
 * opened (null for a new line): an unchanged line is sent back to Mealie exactly as it
 * was, and only new or changed lines are re-read by Mealie's ingredient parser.
 */
@Serializable
data class DraftIngredient(
    val text: String,
    val referenceId: String = UUID.randomUUID().toString(),
    val original: String? = null,
    /** Mealie's section heading shown above this line, kept as it was. */
    val title: String? = null,
)

@Serializable
data class DraftStep(
    val text: String,
    val id: String = UUID.randomUUID().toString(),
    val original: String? = null,
    val title: String? = null,
)

/** A time as typed: hours and minutes. Both blank means unknown; "0" means none (no-cook). */
@Serializable
data class DraftTime(val hours: String = "", val minutes: String = "") {
    val totalMinutes: Long?
        get() {
            val h = hours.trim(); val m = minutes.trim()
            if (h.isEmpty() && m.isEmpty()) return null
            return (h.toLongOrNull() ?: 0) * 60 + (m.toLongOrNull() ?: 0)
        }

    companion object {
        fun ofMinutes(total: Long?): DraftTime =
            if (total == null) DraftTime()
            else DraftTime(if (total >= 60) (total / 60).toString() else "", (total % 60).let { if (it == 0L && total >= 60) "" else it.toString() })

        fun of(raw: String?): DraftTime = ofMinutes(parseRecipeTime(raw)?.let { it / 60 })
    }
}

/** Everything the recipe editor changes. Plain data, so it survives rotation as JSON. */
@Serializable
data class RecipeDraft(
    val name: String,
    val description: String,
    val servings: String,
    val prep: DraftTime,
    val cook: DraftTime,
    val total: DraftTime,
    /** True while the total should follow prep + cook (it was blank or their sum when opened). */
    val totalFollows: Boolean,
    val ingredients: List<DraftIngredient>,
    val steps: List<DraftStep>,
) {
    fun withPrep(t: DraftTime) = copy(prep = t).followTotal()
    fun withCook(t: DraftTime) = copy(cook = t).followTotal()
    fun withTotal(t: DraftTime) = copy(total = t, totalFollows = false)

    private fun followTotal(): RecipeDraft {
        if (!totalFollows) return this
        val p = prep.totalMinutes; val c = cook.totalMinutes
        val sum = if (p == null && c == null) null else (p ?: 0) + (c ?: 0)
        return copy(total = DraftTime.ofMinutes(sum))
    }

    /** Removes an ingredient line; a section heading on it moves to the next line. */
    fun removeIngredient(index: Int): RecipeDraft {
        val list = ingredients.toMutableList()
        val removed = list.removeAt(index)
        if (removed.title != null && index < list.size && list[index].title == null) {
            list[index] = list[index].copy(title = removed.title)
        }
        return copy(ingredients = list)
    }

    fun moveIngredient(index: Int, by: Int) = copy(ingredients = ingredients.moved(index, by))
    fun moveStep(index: Int, by: Int) = copy(steps = steps.moved(index, by))

    /** Pasting several lines into one ingredient splits them into one line each. */
    fun setIngredientText(index: Int, text: String): RecipeDraft {
        val parts = text.split('\n')
        if (parts.size == 1) return copy(ingredients = ingredients.mapIndexed { i, d -> if (i == index) d.copy(text = text) else d })
        val list = ingredients.toMutableList()
        list[index] = list[index].copy(text = parts.first().trim())
        list.addAll(index + 1, parts.drop(1).map { it.trim() }.filter { it.isNotEmpty() }.map { DraftIngredient(it) })
        return copy(ingredients = list)
    }

    companion object {
        fun of(detail: RecipeDetailDto): RecipeDraft {
            val prep = parseRecipeTime(detail.prepTime)
            val cook = parseRecipeTime(detail.performTime)
            val total = parseRecipeTime(detail.totalTime)
            val follows = detail.totalTime.isNullOrBlank() ||
                (prep != null || cook != null) && total == (prep ?: 0) + (cook ?: 0)
            return RecipeDraft(
                name = detail.name,
                description = detail.description.orEmpty(),
                // Older imports only say it in the yield text ("8 servings").
                servings = app.yeschef.data.cook.baseServings(detail.recipeServings, detail.recipeYield)?.let(::formatNumber).orEmpty(),
                prep = DraftTime.of(detail.prepTime),
                cook = DraftTime.of(detail.performTime),
                total = DraftTime.of(detail.totalTime),
                totalFollows = follows,
                ingredients = draftLines(detail.recipeIngredient),
                steps = detail.recipeInstructions.map {
                    DraftStep(it.text, it.id ?: UUID.randomUUID().toString(), original = it.text, title = it.title?.takeIf(String::isNotBlank))
                },
            )
        }

        /** A heading-only line ("For the sauce") becomes the heading of the line after it. */
        private fun draftLines(lines: List<IngredientDto>): List<DraftIngredient> {
            val out = mutableListOf<DraftIngredient>()
            var heading: String? = null
            for (line in lines) {
                val title = line.title?.takeIf(String::isNotBlank)
                if (isHeadingOnly(line)) { heading = title ?: heading; continue }
                val text = ingredientLineText(line)
                out += DraftIngredient(text, line.referenceId ?: UUID.randomUUID().toString(), original = text, title = title ?: heading)
                heading = null
            }
            return out
        }
    }
}

/**
 * The fields an edit changed, queued until Mealie can be reached. Fields left null are
 * unchanged. Times are Mealie's text ("1 hour 30 minutes"; "" means unknown).
 */
@Serializable
data class RecipeChange(
    val recipeId: String,
    val name: String? = null,
    val description: String? = null,
    /** 0 clears it. */
    val servings: Double? = null,
    val recipeYield: String? = null,
    val prepTime: String? = null,
    val performTime: String? = null,
    val totalTime: String? = null,
    val ingredients: List<DraftIngredient>? = null,
    val steps: List<DraftStep>? = null,
) {
    val isEmpty: Boolean
        get() = name == null && description == null && servings == null && recipeYield == null &&
            prepTime == null && performTime == null && totalTime == null && ingredients == null && steps == null

    /** Indexes of [ingredients] Mealie has to parse: new or changed lines. */
    fun linesToParse(): List<Int> = ingredients.orEmpty().indices.filter { i ->
        val line = ingredients!![i]
        line.original == null || line.text.trim() != line.original
    }
}

/** What changed between the recipe and the editor, or null if nothing did. */
fun RecipeDraft.changesFrom(detail: RecipeDetailDto): RecipeChange? {
    val original = RecipeDraft.of(detail)
    fun time(now: DraftTime, before: DraftTime): String? =
        if (now.totalMinutes == before.totalMinutes) null else formatMealieTime(now.totalMinutes)

    val newServings = servings.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    val oldServings = detail.recipeServings?.takeIf { it > 0 } ?: 0.0
    val servingsChanged = servings.trim() != original.servings && newServings != oldServings
    val lines = ingredients.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    val steps = steps.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    fun sameLines() = lines.map { it.referenceId to it.text } == original.ingredients.map { it.referenceId to it.text } &&
        lines.map { it.title } == original.ingredients.map { it.title }
    fun sameSteps() = steps.map { it.id to it.text } == original.steps.map { it.id to it.text }

    val change = RecipeChange(
        recipeId = detail.id,
        name = name.trim().takeIf { it.isNotEmpty() && it != detail.name },
        description = description.trim().takeIf { it != detail.description.orEmpty().trim() },
        servings = if (servingsChanged) newServings else null,
        recipeYield = if (servingsChanged) yieldFor(detail.recipeYield, newServings) else null,
        prepTime = time(prep, original.prep),
        performTime = time(cook, original.cook),
        totalTime = time(total, original.total),
        ingredients = if (sameLines()) null else lines,
        steps = if (sameSteps()) null else steps,
    )
    return change.takeUnless { it.isEmpty }
}

/** Mealie's time text: 0 is "0 minutes" (no cooking), null is "" (unknown), 90 is "1 hour 30 minutes". */
fun formatMealieTime(minutes: Long?): String {
    if (minutes == null) return ""
    if (minutes <= 0) return "0 minutes"
    val h = minutes / 60; val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { if (it == 1L) "1 hour" else "$it hours" },
        m.takeIf { it > 0 }?.let { if (it == 1L) "1 minute" else "$it minutes" },
    ).joinToString(" ")
}

private val PLAIN_YIELD = Regex("""(?i)^\s*(?:serves\s*)?\d+(?:[.,]\d+)?\s*(?:servings?|portions?|people|persons?)?\s*$""")

/** A yield that only restates the servings ("4 servings") follows them; anything else ("1 loaf") is kept. */
private fun yieldFor(old: String?, servings: Double): String? {
    if (old.isNullOrBlank() || !PLAIN_YIELD.matches(old)) return null
    return if (servings > 0) "${formatNumber(servings)} " + if (servings == 1.0) "serving" else "servings" else ""
}

private fun formatNumber(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else formatAmount(d)

/** The recipe with the change applied, for showing it straight away (offline too). */
fun RecipeDetailDto.withChange(change: RecipeChange): RecipeDetailDto {
    val byRef = recipeIngredient.filter { it.referenceId != null }.associateBy { it.referenceId }
    val byStep = recipeInstructions.filter { it.id != null }.associateBy { it.id }
    return copy(
        name = change.name ?: name,
        description = change.description ?: description,
        recipeServings = change.servings ?: recipeServings,
        recipeYield = change.recipeYield ?: recipeYield,
        prepTime = change.prepTime ?: prepTime,
        performTime = change.performTime ?: performTime,
        totalTime = change.totalTime ?: totalTime,
        recipeIngredient = change.ingredients?.map { line ->
            val old = byRef[line.referenceId]
            if (old != null && line.original != null && line.text == line.original) old.copy(title = line.title)
            else IngredientDto(display = line.text, originalText = line.text, title = line.title, referenceId = line.referenceId)
        } ?: recipeIngredient,
        recipeInstructions = change.steps?.map { step ->
            byStep[step.id]?.copy(text = step.text, title = step.title) ?: StepDto(id = step.id, text = step.text, title = step.title)
        } ?: recipeInstructions,
    )
}

/**
 * The PATCH body for Mealie. [raw] is the recipe as Mealie has it now; unchanged
 * ingredient lines and steps are sent back exactly as they were (keeping foods, units
 * and step links). [parsed] holds Mealie's parse of each re-read line, by index.
 */
fun buildRecipePatch(raw: JsonObject, change: RecipeChange, parsed: Map<Int, JsonObject>): JsonObject {
    val body = mutableMapOf<String, JsonElement>()
    change.name?.let { body["name"] = JsonPrimitive(it) }
    change.description?.let { body["description"] = JsonPrimitive(it) }
    change.servings?.let { body["recipeServings"] = JsonPrimitive(it) }
    change.recipeYield?.let { body["recipeYield"] = JsonPrimitive(it) }
    change.prepTime?.let { body["prepTime"] = JsonPrimitive(it) }
    change.performTime?.let { body["performTime"] = JsonPrimitive(it) }
    change.totalTime?.let { body["totalTime"] = JsonPrimitive(it) }

    change.ingredients?.let { lines ->
        val existing = (raw["recipeIngredient"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            .associateBy { it.string("referenceId") }
        body["recipeIngredient"] = JsonArray(
            lines.mapIndexed { i, line ->
                val base = parsed[i] ?: existing[line.referenceId]
                    ?: JsonObject(mapOf("quantity" to JsonPrimitive(0), "note" to JsonPrimitive(line.text)))
                JsonObject(
                    base + mapOf(
                        "referenceId" to JsonPrimitive(line.referenceId),
                        "title" to (line.title?.let(::JsonPrimitive) ?: JsonNull),
                    ) + (if (i in parsed || existing[line.referenceId] == null) mapOf("originalText" to JsonPrimitive(line.text)) else emptyMap()),
                )
            },
        )
    }
    change.steps?.let { steps ->
        val existing = (raw["recipeInstructions"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            .associateBy { it.string("id") }
        body["recipeInstructions"] = JsonArray(
            steps.map { step ->
                val base = existing[step.id] ?: JsonObject(
                    mapOf("id" to JsonPrimitive(step.id), "summary" to JsonPrimitive(""), "ingredientReferences" to JsonArray(emptyList())),
                )
                JsonObject(base + mapOf("text" to JsonPrimitive(step.text), "title" to JsonPrimitive(step.title.orEmpty())))
            },
        )
    }
    return JsonObject(body)
}

/**
 * Mealie's parse of one line, ready to save: [food] and [unit] replace the parser's
 * unmatched ones (which have no id yet). Lines the parser couldn't read keep their text as a note.
 */
fun parsedIngredient(parsed: JsonObject?, text: String, food: JsonElement?, unit: JsonElement?): JsonObject {
    val ing = parsed ?: return JsonObject(mapOf("quantity" to JsonPrimitive(0), "note" to JsonPrimitive(text)))
    val out = ing.toMutableMap()
    out.remove("display") // Mealie rebuilds it from the parts
    out["food"] = food ?: JsonNull
    out["unit"] = unit ?: JsonNull
    return JsonObject(out)
}

/** A food or unit object from the parser that still needs a Mealie id (it wasn't matched). */
fun JsonElement?.needsId(): Boolean {
    val o = this as? JsonObject ?: return false
    return o.string("id") == null && !o.string("name").isNullOrBlank()
}

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

private fun <T> List<T>.moved(index: Int, by: Int): List<T> {
    val to = index + by
    if (index !in indices || to !in indices) return this
    val list = toMutableList()
    list.add(to, list.removeAt(index))
    return list
}

/** Picks the ingredients to send to a shopping list: by Mealie's reference id, or by position for lines without one. */
fun selectIngredients(raw: List<JsonObject>, refs: Set<String>, indexes: Set<Int>): List<JsonObject> =
    raw.filterIndexed { i, o ->
        val ref = o.string("referenceId")
        if (ref != null) ref in refs else i in indexes
    }
