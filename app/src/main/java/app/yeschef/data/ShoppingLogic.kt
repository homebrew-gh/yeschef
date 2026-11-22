package app.yeschef.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One row on the shopping screen. Pure data so the sorting can be unit tested. */
data class ShoppingRow(
    val id: String,
    val text: String,
    val checked: Boolean,
    val position: Int,
    val labelId: String?,
    val labelName: String?,
    val labelColor: String?,
    val pending: Boolean,
)

data class ShoppingSection(
    val labelName: String,
    val labelColor: String?,
    val rows: List<ShoppingRow>,
)

const val UNLABELED = "Other"

/**
 * Groups unchecked items into store sections (Mealie labels), ordered by the list's
 * label settings (the order you arranged in Mealie, i.e. your walk through the store).
 * Labels without a setting come after, alphabetically; unlabeled items come last.
 * Checked items are returned separately so the UI can tuck them away at the bottom.
 */
fun groupForShopping(
    rows: List<ShoppingRow>,
    labelOrder: Map<String, Int>,
): Pair<List<ShoppingSection>, List<ShoppingRow>> {
    val (checked, open) = rows.partition { it.checked }
    val sections = open
        .groupBy { it.labelId }
        .map { (labelId, items) ->
            val first = items.first()
            Triple(
                labelId,
                ShoppingSection(
                    labelName = first.labelName ?: UNLABELED,
                    labelColor = first.labelColor,
                    rows = items.sortedWith(compareBy<ShoppingRow> { it.position }.thenBy { it.text.lowercase() }),
                ),
                labelId?.let { labelOrder[it] },
            )
        }
        .sortedWith(
            compareBy<Triple<String?, ShoppingSection, Int?>> {
                when {
                    it.first == null -> 2
                    it.third == null -> 1
                    else -> 0
                }
            }.thenBy { it.third ?: 0 }.thenBy { it.second.labelName.lowercase() },
        )
        .map { it.second }
    return sections to checked.sortedBy { it.text.lowercase() }
}

/** Best human-readable text for a Mealie shopping item. */
fun itemText(display: String?, note: String?, foodName: String?, quantity: Double?): String {
    display?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val name = listOfNotNull(foodName?.trim()?.takeIf { it.isNotEmpty() }, note?.trim()?.takeIf { it.isNotEmpty() })
        .joinToString(" ")
    val qty = quantity?.takeIf { it > 0 && it != 1.0 }?.let { formatQuantity(it) }
    return listOfNotNull(qty, name.ifEmpty { null }).joinToString(" ").ifEmpty { "(unnamed item)" }
}

fun formatQuantity(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else "%.2f".format(q).trimEnd('0').trimEnd('.')

/** Returns a copy of a Mealie item JSON object with `checked` replaced. */
fun JsonObject.withChecked(checked: Boolean): JsonObject =
    JsonObject(this + ("checked" to JsonPrimitive(checked)))
