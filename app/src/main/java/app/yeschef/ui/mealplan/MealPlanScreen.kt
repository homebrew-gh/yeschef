package app.yeschef.ui.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.local.MealPlanEntity
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.theme.OverlineStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val mealOrder = listOf("breakfast", "lunch", "dinner", "side", "snack", "drink", "dessert")

@Composable
fun MealPlanScreen(container: AppContainer, onOpenRecipe: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    val entries by remember { container.repository.mealPlan(today) }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    MealPlanContent(entries, today, config?.serverUrl, onOpenRecipe, actions = { SyncAction(container) })
}

/** Stateless meal plan, also used for screenshots. */
@Composable
fun MealPlanContent(
    entries: List<MealPlanEntity>,
    today: LocalDate,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    actions: @Composable () -> Unit,
) {
    val byDay = remember(entries) {
        entries.groupBy { it.date }.toSortedMap().mapValues { (_, day) ->
            day.sortedBy { mealOrder.indexOf(it.entryType).let { i -> if (i < 0) 99 else i } }
        }
    }
    Scaffold(
        topBar = { YcTopBar("Meal plan", actions = { actions() }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (byDay.isEmpty()) {
                item {
                    Text(
                        "Nothing planned yet. Plan meals in Mealie, or ask your agent to fill in the week.",
                        Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            byDay.forEach { (date, day) ->
                item(key = "d-$date") { DayHeader(date, today) }
                day.forEach { entry -> item(key = entry.id) { EntryCard(entry, serverUrl, onOpenRecipe) } }
            }
        }
    }
}

@Composable
private fun DayHeader(isoDate: String, today: LocalDate) {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull()
    val isToday = date == today
    Row(Modifier.padding(top = 16.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            when (date) {
                null -> isoDate
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (date != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                date.format(DateTimeFormatter.ofPattern("MMM d")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun EntryCard(entry: MealPlanEntity, serverUrl: String?, onOpenRecipe: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = entry.recipeName ?: entry.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val slug = entry.recipeSlug
    Surface(
        onClick = { slug?.let(onOpenRecipe) },
        enabled = slug != null,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.recipeId != null) {
                RecipeImage(serverUrl, entry.recipeId, Modifier.size(64.dp).clip(MaterialTheme.shapes.medium), size = "tiny-original")
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).padding(vertical = 4.dp, horizontal = if (entry.recipeId == null) 6.dp else 0.dp)) {
                Surface(shape = MaterialTheme.shapes.small, color = colors.tertiaryContainer) {
                    Text(
                        entry.entryType.uppercase(),
                        style = OverlineStyle,
                        color = colors.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.text?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (slug != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.outline)
        }
    }
}
