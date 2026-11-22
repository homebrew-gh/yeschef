package app.yeschef.ui.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.local.MealPlanEntity
import app.yeschef.ui.SyncAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val mealOrder = listOf("breakfast", "lunch", "dinner", "side", "snack", "drink", "dessert")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(container: AppContainer, onOpenRecipe: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    val entries by remember { container.repository.mealPlan(today) }.collectAsStateWithLifecycle(emptyList())
    val byDay = remember(entries) {
        entries.groupBy { it.date }.toSortedMap().mapValues { (_, day) ->
            day.sortedBy { mealOrder.indexOf(it.entryType).let { i -> if (i < 0) 99 else i } }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Meal plan") }, actions = { SyncAction(container) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (byDay.isEmpty()) {
                item {
                    Text(
                        "Nothing planned yet. Plan meals in Mealie, or ask your agent to fill in the week.",
                        Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            byDay.forEach { (date, day) ->
                item(key = "d-$date") {
                    Text(
                        dayLabel(date, today),
                        Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(day, key = { it.id }) { entry -> EntryRow(entry, onOpenRecipe) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MealPlanEntity, onOpenRecipe: (String) -> Unit) {
    val title = entry.recipeName ?: entry.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val slug = entry.recipeSlug
    ListItem(
        overlineContent = { Text(entry.entryType.replaceFirstChar { it.titlecase() }) },
        headlineContent = { Text(title) },
        supportingContent = if (!entry.text.isNullOrBlank()) {
            { Text(entry.text) }
        } else {
            null
        },
        modifier = if (slug != null) Modifier.clickable { onOpenRecipe(slug) } else Modifier,
    )
}

private fun dayLabel(isoDate: String, today: LocalDate): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " +
            date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
