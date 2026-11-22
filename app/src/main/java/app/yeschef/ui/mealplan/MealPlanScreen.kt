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
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.FilledTonalButton
import app.yeschef.AppContainer
import app.yeschef.data.MealGroup
import app.yeschef.data.groupMeals
import app.yeschef.data.mainsAndSides
import app.yeschef.data.Cookbook
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import app.yeschef.ui.cook.MealRequest
import app.yeschef.data.local.MealPlanEntity
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.theme.OverlineStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MealPlanScreen(container: AppContainer, onOpenRecipe: (String) -> Unit, onCookMeal: (MealRequest) -> Unit) {
    val today = remember { LocalDate.now() }
    val entries by remember { container.repository.mealPlan(today) }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    val recipes by remember { container.repository.recipes() }.collectAsStateWithLifecycle(emptyList())
    val sideSlugs = remember(recipes) { recipes.filter { Cookbook.chapterOf(it) == "Sides" }.map { it.slug }.toSet() }
    MealPlanContent(
        entries, today, config?.serverUrl, onOpenRecipe,
        actions = { SyncAction(container) }, onCookMeal = onCookMeal, sideSlugs = sideSlugs,
    )
}

/** "Pulled pork dinner": the main recipe's name and the meal. */
fun mealTitle(group: MealGroup): String {
    val main = group.entries.firstOrNull { it.recipeName != null }?.recipeName ?: return group.entryType.replaceFirstChar { it.uppercase() }
    return if (group.entryType == "side") "$main and sides" else "$main ${group.entryType}"
}

/** Stateless meal plan, also used for screenshots. */
@Composable
fun MealPlanContent(
    entries: List<MealPlanEntity>,
    today: LocalDate,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    actions: @Composable () -> Unit,
    onCookMeal: (MealRequest) -> Unit = {},
    /** Recipes in the Sides chapter, so they can be shown under the main they go with. */
    sideSlugs: Set<String> = emptySet(),
) {
    // Each day's meals, with sides next to the main they go with.
    val byDay = remember(entries) { groupMeals(entries).groupBy { it.date } }
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
            byDay.forEach { (date, meals) ->
                item(key = "d-$date") { DayHeader(date, today) }
                meals.forEach { meal ->
                    if (meal.entries.size == 1) {
                        val entry = meal.entries.single()
                        item(key = entry.id) { EntryCard(entry, serverUrl, onOpenRecipe) }
                    } else {
                        // One card per meal: the main on top, its sides under it.
                        val (mains, sides) = meal.mainsAndSides { it in sideSlugs }
                        val ordered = meal.copy(entries = mains + sides)
                        item(key = "m-$date-${meal.entryType}") {
                            MealCard(
                                type = meal.entryType,
                                mains = mains,
                                sides = sides,
                                serverUrl = serverUrl,
                                onOpenRecipe = onOpenRecipe,
                                onCook = if (ordered.cookable) ({
                                    val day = runCatching { LocalDate.parse(date) }.getOrDefault(today)
                                    onCookMeal(MealRequest(mealTitle(ordered), ordered.slugs, day, meal.entryType))
                                }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A meal with more than one recipe: main(s) on top, then "with" and its sides, then Cook. */
@Composable
private fun MealCard(
    type: String,
    mains: List<MealPlanEntity>,
    sides: List<MealPlanEntity>,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    onCook: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(bottom = 10.dp)) {
            mains.forEachIndexed { i, entry ->
                MealRow(entry, serverUrl, onOpenRecipe, imageSize = 64, label = if (i == 0) type else null)
            }
            if (sides.isNotEmpty()) {
                Text(
                    "WITH",
                    style = OverlineStyle,
                    color = colors.tertiary,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                )
                Row(Modifier.padding(start = 24.dp).height(IntrinsicSize.Min)) {
                    // A gold rule ties the sides to the main above.
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.tertiary.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                    Column(Modifier.weight(1f)) {
                        sides.forEach { entry -> MealRow(entry, serverUrl, onOpenRecipe, imageSize = 44, label = null, compact = true) }
                    }
                }
            }
            if (onCook != null) {
                FilledTonalButton(onClick = onCook, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 8.dp)) {
                    Icon(Icons.Filled.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cook this meal together")
                }
            }
        }
    }
}

@Composable
private fun MealRow(
    entry: MealPlanEntity,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    imageSize: Int,
    label: String?,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val title = entry.recipeName ?: entry.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val slug = entry.recipeSlug
    Row(
        Modifier.fillMaxWidth().clickable(enabled = slug != null) { slug?.let(onOpenRecipe) }
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.recipeId != null) {
            RecipeImage(serverUrl, entry.recipeId, Modifier.size(imageSize.dp).clip(MaterialTheme.shapes.medium), size = "tiny-original")
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Surface(shape = MaterialTheme.shapes.small, color = colors.tertiaryContainer) {
                    Text(label.uppercase(), style = OverlineStyle, color = colors.onTertiaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.size(6.dp))
            }
            Text(
                title,
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (slug != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.outline)
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
