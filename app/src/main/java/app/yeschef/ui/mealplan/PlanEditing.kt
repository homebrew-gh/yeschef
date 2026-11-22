package app.yeschef.ui.mealplan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yeschef.data.local.RecipeEntity
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.recipes.RecipePickerContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The meals you can move things into. Mealie's other types (snack, dessert...) stay as they are. */
val planMealTypes = listOf("breakfast", "lunch", "dinner")

/** The next two weeks, for moving and adding meals. */
fun planDays(today: LocalDate, count: Int = 14): List<LocalDate> = (0 until count).map { today.plusDays(it.toLong()) }

fun planDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + date.dayOfMonth
}

fun planDayLongLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.plusDays(1) -> "tomorrow"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " + date.format(DateTimeFormatter.ofPattern("MMM d"))
}

@Composable
private fun DayChips(days: List<LocalDate>, today: LocalDate, selected: LocalDate?, disabled: LocalDate? = null, onSelect: (LocalDate) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { d ->
            FilterChip(
                selected = d == selected,
                onClick = { onSelect(d) },
                enabled = d != disabled,
                label = { Text(planDayLabel(d, today)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun MealTypeChips(selected: String, onSelect: (String) -> Unit) {
    val types = if (selected in planMealTypes) planMealTypes else planMealTypes + selected
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { t ->
            FilterChip(
                selected = t == selected,
                onClick = { onSelect(t) },
                label = { Text(t.replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }
    }
}

/** Move a whole meal (main and sides) to another day or meal, or take it off the plan. */
@Composable
fun MoveMealContent(
    title: String,
    recipeCount: Int,
    date: LocalDate,
    entryType: String,
    today: LocalDate,
    onMove: (LocalDate, String) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    var toDate by rememberSaveable { mutableStateOf(date.toString()) }
    var toType by rememberSaveable { mutableStateOf(entryType) }
    val target = LocalDate.parse(toDate)
    val changed = target != date || toType != entryType
    val days = planDays(today).let { if (date in it) it else listOf(date) + it }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            if (recipeCount > 1) "Moves all $recipeCount recipes together." else "Planned for ${entryType} ${planDayLongLabel(date, today)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        SectionLabel("Move to")
        DayChips(days, today, target) { toDate = it.toString() }
        Spacer(Modifier.size(10.dp))
        MealTypeChips(toType) { toType = it }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Remove from plan", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onMove(target, toType) }, enabled = changed) { Text("Move") }
        }
    }
}

/** Swap everything planned on one day with another day. */
@Composable
fun SwapDayContent(day: LocalDate, today: LocalDate, onSwap: (LocalDate) -> Unit, onCancel: () -> Unit) {
    var other by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text(
            "Swap " + planDayLongLabel(day, today).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Its meals trade places with the day you pick. Each keeps its breakfast, lunch or dinner slot.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        SectionLabel("Swap with")
        DayChips(planDays(today), today, other?.let(LocalDate::parse), disabled = day) { other = it.toString() }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { other?.let { onSwap(LocalDate.parse(it)) } }, enabled = other != null) { Text("Swap days") }
        }
    }
}

/** Add one or more recipes (a main and its sides, say) to a day and meal. */
@Composable
fun AddToPlanContent(
    recipes: List<RecipeEntity>,
    today: LocalDate,
    initialDate: LocalDate,
    onAdd: (LocalDate, String, List<String>) -> Unit,
    onCancel: () -> Unit,
    initialPicked: List<String> = emptyList(),
) {
    var date by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var type by rememberSaveable { mutableStateOf("dinner") }
    RecipePickerContent(
        recipes = recipes,
        title = "Add to the plan",
        subtitle = "Pick a main and any sides; they're planned together.",
        priorityChapters = listOf("Mains", "Sides"),
        actionLabel = { n -> if (n <= 1) "Add" else "Add $n recipes" },
        onCancel = onCancel,
        onConfirm = { onAdd(LocalDate.parse(date), type, it) },
        initialPicked = initialPicked,
        extra = {
            DayChips(planDays(today), today, LocalDate.parse(date)) { date = it.toString() }
            Spacer(Modifier.size(8.dp))
            MealTypeChips(type) { type = it }
            Spacer(Modifier.size(12.dp))
        },
    )
}
