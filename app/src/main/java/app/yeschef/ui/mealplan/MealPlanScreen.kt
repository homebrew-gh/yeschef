package app.yeschef.ui.mealplan

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.FilledTonalButton
import app.yeschef.AppContainer
import app.yeschef.data.cook.ActiveCookSession
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yeschef.data.sync.SyncScheduler
import kotlinx.coroutines.launch
import app.yeschef.data.MealGroup
import app.yeschef.data.groupMeals
import app.yeschef.data.mainsAndSides
import app.yeschef.data.Cookbook
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.OutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(container: AppContainer, onOpenRecipe: (String) -> Unit, onCookMeal: (MealRequest) -> Unit) {
    val repo = container.repository
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = LocalDate.now()
                if (now != today) today = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The last week too, so a meal that didn't get made can still be found and moved.
    val entries by remember(today) { repo.mealPlan(today.minusDays(EARLIER_DAYS)) }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    val recipes by remember { repo.recipes() }.collectAsStateWithLifecycle(emptyList())
    val sideSlugs = remember(recipes) { recipes.filter { Cookbook.chapterOf(it) == "Sides" }.map { it.slug }.toSet() }
    val recipeMinutes = remember(recipes) {
        recipes.mapNotNull { r -> Cookbook.minutes(r.totalTime)?.let { r.slug to it } }.toMap()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Edits show at once and reach Mealie with the next sync, so they work offline.
    fun edit(change: suspend () -> Unit) {
        scope.launch {
            change()
            SyncScheduler.requestSync(context)
        }
    }

    var editing by remember { mutableStateOf<MealGroup?>(null) }
    var editingToToday by remember { mutableStateOf(false) }
    var addingSideTo by remember { mutableStateOf<MealGroup?>(null) }
    var confirmRemove by remember { mutableStateOf<MealGroup?>(null) }
    var swapping by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    editing?.let { meal ->
        val date = runCatching { LocalDate.parse(meal.date) }.getOrDefault(today)
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheetState) {
            MoveMealContent(
                initialDate = if (editingToToday) today else date,
                title = mealTitle(meal),
                recipeCount = meal.entries.size,
                date = date,
                entryType = meal.entryType,
                today = today,
                onMove = { toDate, toType ->
                    editing = null
                    edit { repo.moveMeal(meal.entries, toDate, toType) }
                },
                onRemove = { editing = null; confirmRemove = meal },
                onCancel = { editing = null },
            )
        }
    }
    addingSideTo?.let { meal ->
        val date = runCatching { LocalDate.parse(meal.date) }.getOrDefault(today)
        ModalBottomSheet(onDismissRequest = { addingSideTo = null }, sheetState = sheetState) {
            AddSideToMealContent(
                mealTitle = mealTitle(meal),
                recipes = recipes,
                existingSlugs = meal.slugs.toSet(),
                onAdd = { slugs ->
                    addingSideTo = null
                    val picked = slugs.mapNotNull { s -> recipes.firstOrNull { it.slug == s } }
                    edit { repo.addToPlan(date, meal.entryType, picked) }
                },
                onCancel = { addingSideTo = null },
            )
        }
    }
    confirmRemove?.let { meal ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove from the plan?") },
            text = { Text(mealTitle(meal) + if (meal.entries.size > 1) " and all its sides come off the plan. The recipes stay in your cookbook." else " comes off the plan. The recipe stays in your cookbook.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    edit { repo.removeMeal(meal.entries) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
    swapping?.let { iso ->
        ModalBottomSheet(onDismissRequest = { swapping = null }, sheetState = sheetState) {
            SwapDayContent(
                day = LocalDate.parse(iso),
                today = today,
                onSwap = { other ->
                    swapping = null
                    edit { repo.swapDays(LocalDate.parse(iso), other) }
                },
                onCancel = { swapping = null },
            )
        }
    }
    adding?.let { iso ->
        ModalBottomSheet(onDismissRequest = { adding = null }, sheetState = sheetState) {
            AddToPlanContent(
                recipes = recipes,
                today = today,
                initialDate = LocalDate.parse(iso),
                onAdd = { date, type, slugs ->
                    adding = null
                    val picked = slugs.mapNotNull { s -> recipes.firstOrNull { it.slug == s } }
                    edit { repo.addToPlan(date, type, picked) }
                },
                onCancel = { adding = null },
            )
        }
    }

    MealPlanContent(
        entries, today, config?.serverUrl, onOpenRecipe,
        actions = { SyncAction(container) }, onCookMeal = onCookMeal, sideSlugs = sideSlugs,
        recipeMinutes = recipeMinutes,
        onCookRecipe = { slug, name ->
            if (container.activeCook.value?.slug != slug) {
                scope.launch { container.setActiveCook(ActiveCookSession(slug = slug, name = name)) }
            }
            onOpenRecipe(slug)
        },
        onEditMeal = { editingToToday = false; editing = it },
        onAddSide = { addingSideTo = it },
        onRemoveMeal = { confirmRemove = it },
        onMakeToday = { meal, slotTaken ->
            // Today's slot already has a meal: let him pick another meal or day instead of merging them.
            if (slotTaken) {
                editingToToday = true
                editing = meal
            } else {
                edit { repo.moveMeal(meal.entries, today, meal.entryType) }
            }
        },
        onSwapDay = { swapping = it.toString() },
        onAddToDay = { adding = it.toString() },
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
    /** Jump straight into cook mode for a single-dish meal on Today. */
    onCookRecipe: ((slug: String, name: String) -> Unit)? = null,
    /** Recipes in the Sides chapter, so they can be shown under the main they go with. */
    sideSlugs: Set<String> = emptySet(),
    /** Total cook minutes per recipe slug, for showing a meal's lead time on the card. */
    recipeMinutes: Map<String, Long> = emptyMap(),
    /** Move or remove a whole meal (long-press or the ⋮ menu). */
    onEditMeal: ((MealGroup) -> Unit)? = null,
    /** Add a side or extra dish directly to this meal so it joins "Cook this meal together". */
    onAddSide: ((MealGroup) -> Unit)? = null,
    /** Remove a whole meal directly from the ⋮ menu. */
    onRemoveMeal: ((MealGroup) -> Unit)? = null,
    onSwapDay: ((LocalDate) -> Unit)? = null,
    onAddToDay: ((LocalDate) -> Unit)? = null,
    /** Move a meal from an earlier day to today; true when today already has that meal. */
    onMakeToday: ((MealGroup, Boolean) -> Unit)? = null,
    showEarlierAtStart: Boolean = false,
) {
    // Each day's meals, with sides next to the main they go with.
    val allDays = remember(entries) { groupMeals(entries).groupBy { it.date } }
    val todayIso = today.toString()
    val earlier = allDays.filterKeys { it < todayIso }
    val byDay = allDays.filterKeys { it >= todayIso }
    val todayTypes = allDays[todayIso].orEmpty().map { it.entryType }.toSet()
    var showEarlier by rememberSaveable { mutableStateOf(showEarlierAtStart) }
    val earlierCount = earlier.values.sumOf { it.size }
    Scaffold(
        topBar = {
            YcTopBar("Meal plan", actions = {
                if (onAddToDay != null) IconButton(onClick = { onAddToDay(today) }) { Icon(Icons.Filled.Add, "Add to the plan") }
                actions()
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (earlier.isNotEmpty()) {
                item(key = "earlier") {
                    PlanColumnItem {
                        EarlierToggle(earlierCount, showEarlier) { showEarlier = !showEarlier }
                    }
                }
            }
            if (showEarlier) earlier.forEach { (date, meals) ->
                item(key = "d-$date") { PlanColumnItem { DayHeader(date, today, past = true) } }
                meals.forEach { meal ->
                    val (mains, sides) = meal.mainsAndSides { it in sideSlugs }
                    val ordered = meal.copy(entries = mains + sides)
                    item(key = "m-$date-${meal.entryType}") {
                        PlanColumnItem {
                            MealCard(
                                type = meal.entryType,
                                mains = mains,
                                sides = sides,
                                serverUrl = serverUrl,
                                onOpenRecipe = onOpenRecipe,
                                onEdit = onEditMeal?.let { edit -> { edit(ordered) } },
                                onRemove = onRemoveMeal?.let { remove -> { remove(ordered) } },
                                onCook = null,
                                onMakeToday = onMakeToday?.let { make -> { make(ordered, meal.entryType in todayTypes) } },
                            )
                        }
                    }
                }
            }
            if (byDay.isEmpty()) {
                item {
                    PlanColumnItem {
                        Text(
                            "Nothing planned yet. Plan meals in Mealie, or ask your agent to fill in the week.",
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            byDay.entries.forEachIndexed { dayIndex, (date, meals) ->
                val day = runCatching { LocalDate.parse(date) }.getOrNull()
                val isToday = day == today
                item(key = "d-$date") {
                    PlanColumnItem {
                        DayHeader(
                            date, today,
                            first = dayIndex == 0 && earlier.isEmpty(),
                            onSwap = if (day != null && onSwapDay != null) ({ onSwapDay(day) }) else null,
                        )
                    }
                }
                meals.forEach { meal ->
                    // One card per meal: the main on top, its sides under it.
                    val (mains, sides) = meal.mainsAndSides { it in sideSlugs }
                    val ordered = meal.copy(entries = mains + sides)
                    val leadMinutes = ordered.slugs.mapNotNull { recipeMinutes[it] }.maxOrNull()
                    val singleEntry = if (!ordered.cookable && isToday) {
                        ordered.entries.firstOrNull { it.recipeSlug != null }
                    } else null
                    item(key = "m-$date-${meal.entryType}") {
                        PlanColumnItem {
                            MealCard(
                                type = meal.entryType,
                                mains = mains,
                                sides = sides,
                                serverUrl = serverUrl,
                                onOpenRecipe = onOpenRecipe,
                                onEdit = onEditMeal?.let { edit -> { edit(ordered) } },
                                onAddSide = onAddSide?.let { add -> { add(ordered) } },
                                onRemove = onRemoveMeal?.let { remove -> { remove(ordered) } },
                                onCook = if (ordered.cookable) ({
                                    onCookMeal(MealRequest(mealTitle(ordered), ordered.slugs, day ?: today, meal.entryType))
                                }) else null,
                                onCookSingle = if (singleEntry != null && onCookRecipe != null) ({
                                    val slug = singleEntry.recipeSlug!!
                                    val name = singleEntry.recipeName ?: singleEntry.title ?: slug
                                    onCookRecipe(slug, name)
                                }) else null,
                                dishCount = ordered.slugs.size,
                                leadMinutes = leadMinutes,
                                isToday = isToday,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Centers each item to a comfortable editorial width on tablets while keeping full-screen touch scroll. */
@Composable
private fun PlanColumnItem(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 680.dp).fillMaxWidth()) { content() }
    }
}

/** A meal: main(s) on top, then "with" and its sides, then Cook. Today gets primary emphasis; future days stay calm. */
@Composable
private fun MealCard(
    type: String,
    mains: List<MealPlanEntity>,
    sides: List<MealPlanEntity>,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    onEdit: (() -> Unit)?,
    onCook: (() -> Unit)?,
    onCookSingle: (() -> Unit)? = null,
    onAddSide: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onMakeToday: (() -> Unit)? = null,
    dishCount: Int = 0,
    leadMinutes: Long? = null,
    isToday: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val metaText = buildList {
        if (dishCount > 1) add("$dishCount dishes")
        leadMinutes?.let { add("~${app.yeschef.data.cook.formatShort(it * 60)}") }
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    val hasFooter = onCook != null || onCookSingle != null || onMakeToday != null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isToday) colors.surfaceContainer else colors.surfaceContainerLow,
        border = if (isToday) BorderStroke(1.dp, colors.primary.copy(alpha = 0.32f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = if (sides.isEmpty() && !hasFooter) 0.dp else 12.dp)) {
            mains.forEachIndexed { i, entry ->
                MealRow(
                    entry, serverUrl, onOpenRecipe, imageSize = if (isToday) 76 else 60,
                    label = if (i == 0) type else null,
                    metaText = if (i == 0) metaText else null,
                    onLongClick = onEdit,
                    onMore = if (i == 0) onEdit else null,
                    onAddSide = if (i == 0) onAddSide else null,
                    onRemove = if (i == 0) onRemove else null,
                )
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
                        sides.forEach { entry -> MealRow(entry, serverUrl, onOpenRecipe, imageSize = 44, label = null, compact = true, onLongClick = onEdit) }
                    }
                }
            }
            if (onCook != null) {
                val buttonMod = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp)
                if (isToday) {
                    Button(onClick = onCook, modifier = buttonMod) {
                        Icon(Icons.Filled.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cook this meal")
                    }
                } else {
                    OutlinedButton(
                        onClick = onCook,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = buttonMod,
                    ) {
                        Icon(Icons.Filled.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cook this meal")
                    }
                }
            } else if (onCookSingle != null) {
                FilledTonalButton(
                    onClick = onCookSingle,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp),
                ) {
                    Icon(Icons.Filled.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Let's cook")
                }
            }
            if (onMakeToday != null) {
                OutlinedButton(onClick = onMakeToday, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp)) {
                    Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Make it today")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealRow(
    entry: MealPlanEntity,
    serverUrl: String?,
    onOpenRecipe: (String) -> Unit,
    imageSize: Int,
    label: String?,
    metaText: String? = null,
    compact: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onAddSide: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val title = entry.recipeName ?: entry.title?.takeIf { it.isNotBlank() } ?: "Untitled"
    val slug = entry.recipeSlug
    var expandNote by remember(entry.id) { mutableStateOf(false) }
    var menuOpen by remember(entry.id) { mutableStateOf(false) }
    val hasMenu = onAddSide != null || onRemove != null
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick) { slug?.let(onOpenRecipe) }
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.recipeId != null) {
            RecipeImage(serverUrl, entry.recipeId, Modifier.size(imageSize.dp).clip(MaterialTheme.shapes.medium), size = "tiny-original")
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = MaterialTheme.shapes.small, color = colors.tertiaryContainer) {
                        Text(label.uppercase(), style = OverlineStyle, color = colors.onTertiaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (metaText != null) {
                        Text(metaText, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
            Text(
                title,
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.text?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (expandNote) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { expandNote = !expandNote }.padding(top = 2.dp),
                )
            }
        }
        if (hasMenu) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "Meal options", tint = colors.outline)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onAddSide != null) {
                        DropdownMenuItem(
                            text = { Text("Add a side") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = { menuOpen = false; onAddSide() },
                        )
                    }
                    if (onMore != null) {
                        DropdownMenuItem(
                            text = { Text("Move meal") },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            onClick = { menuOpen = false; onMore() },
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from plan", color = colors.error) },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = colors.error) },
                            onClick = { menuOpen = false; onRemove() },
                        )
                    }
                }
            }
        } else if (onMore != null) {
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "Move or remove", tint = colors.outline) }
        } else if (slug != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.outline)
        }
    }
}

@Composable
private fun DayHeader(isoDate: String, today: LocalDate, first: Boolean = false, onSwap: (() -> Unit)? = null, past: Boolean = false) {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull()
    val isToday = date == today
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 10.dp else 20.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            when (date) {
                null -> isoDate
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                today.minusDays(1) -> "Yesterday"
                else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            },
            style = MaterialTheme.typography.titleLarge,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                past -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
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
        Spacer(Modifier.weight(1f))
        if (onSwap != null) {
            TextButton(
                onClick = onSwap,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Swap", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** How far back the plan shows, matching what sync keeps (a week). */
const val EARLIER_DAYS = 7L

/** "Past week · 2 meals": opens the last week's meals, hidden by default. */
@Composable
private fun EarlierToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = colors.outline, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Past week", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(if (count == 1) "1 meal" else "$count meals", style = MaterialTheme.typography.labelLarge, color = colors.outline)
        Spacer(Modifier.weight(1f))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Hide earlier days" else "Show earlier days", tint = colors.outline)
    }
}
