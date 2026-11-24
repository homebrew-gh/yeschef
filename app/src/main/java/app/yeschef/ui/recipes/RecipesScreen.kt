package app.yeschef.ui.recipes

import app.yeschef.ui.cook.MealRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import app.yeschef.data.Cookbook
import java.time.LocalDate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.local.RecipeEntity
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.cook.KitchenTool
import app.yeschef.ui.cook.KitchenToolsSheet
import app.yeschef.ui.cook.rememberKitchenToolsState

/** Recipe grid; on the tablet the selected recipe opens beside it. */
@Composable
fun RecipesScreen(container: AppContainer, openSlug: String?, onOpen: (String?) -> Unit, onCookMeal: (MealRequest) -> Unit = {}) {
    val recipes by remember { container.repository.recipes() }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    // "Add a recipe" sheet, opened from the top bar or by a link shared from another app.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val shared by container.sharedLink.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            adding = it
            container.sharedLink.value = null
            onOpen(null)
        }
    }
    adding?.let { initialUrl ->
        AddRecipeSheet(
            container,
            initialUrl = initialUrl,
            onDismiss = { adding = null },
            onImported = { slug -> adding = null; onOpen(slug) },
        )
    }
    val kitchenTools = rememberKitchenToolsState()
    KitchenToolsSheet(kitchenTools)
    val diceChapter by container.settings.diceChapter.collectAsStateWithLifecycle(Cookbook.DICE_DEFAULT)
    val scope = rememberCoroutineScope()
    val list: @Composable (Modifier) -> Unit = { modifier ->
        RecipeGrid(
            recipes, config?.serverUrl, openSlug, onOpen,
            actions = {
                IconButton(onClick = { adding = "" }) { Icon(Icons.Filled.Add, contentDescription = "Add a recipe") }
                IconButton(onClick = { kitchenTools.show(KitchenTool.REFERENCE) }) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Kitchen reference")
                }
                SyncAction(container)
            },
            modifier = modifier,
            diceChapter = diceChapter,
            onDiceChapter = { scope.launch { container.settings.saveDiceChapter(it) } },
        )
    }
    // A recipe opens full screen with a back arrow, on the tablet as well as the phone.
    if (openSlug != null) {
        BackHandler { onOpen(null) }
        RecipeDetailScreen(container, openSlug, onBack = { onOpen(null) }, onCookMeal = onCookMeal, onSlugChanged = { onOpen(it) })
    } else {
        list(Modifier.fillMaxSize())
    }
}

/** Stateless cookbook view of the recipes, also used for screenshots. */
@Composable
fun RecipeGrid(
    recipes: List<RecipeEntity>,
    serverUrl: String?,
    selectedSlug: String?,
    onOpen: (String) -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    initialChapter: String? = null,
    initialProtein: String? = null,
    diceChapter: String? = Cookbook.DICE_DEFAULT,
    onDiceChapter: (String?) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var chapter by rememberSaveable { mutableStateOf(initialChapter) }
    var protein by rememberSaveable { mutableStateOf(initialProtein) }
    var sort by rememberSaveable { mutableStateOf(Cookbook.Sort.Chapters) }
    val chapters = remember(recipes) { Cookbook.chapterOrder(recipes) }
    // Only offer protein chips for recipes in the chapter being looked at.
    val proteins = remember(recipes, chapter) {
        val inChapter = Cookbook.filter(recipes, "", chapter, null).flatMap(Cookbook::proteinsOf).toSet()
        Cookbook.proteins.map { it.first }.filter { it in inChapter }
    }
    val shown = remember(recipes, query, chapter, protein) { Cookbook.filter(recipes, query, chapter, protein) }
    // Inside one chapter there's nothing to group, so cookbook order is just A to Z.
    val sections = remember(shown, sort, chapter) {
        Cookbook.sections(shown, if (chapter != null && sort == Cookbook.Sort.Chapters) Cookbook.Sort.AtoZ else sort)
    }
    val colors = MaterialTheme.colorScheme
    // The recipe the dice landed on, shown in the roll animation; the count replays it on "Roll again".
    var rolledSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var rollCount by rememberSaveable { mutableStateOf(0) }
    fun roll(from: String?) {
        // Roll from the picked chapter; if the filters leave nothing there, from anything.
        fun draw() = Cookbook.surprise(recipes, query, from, protein, today)
            ?: Cookbook.surprise(recipes, query, null, protein, today)
        // "Roll again" tries not to land on the same recipe twice running.
        var pick = draw() ?: return
        repeat(4) { if (pick.slug == rolledSlug) pick = draw() ?: pick }
        rolledSlug = pick.slug
        rollCount++
    }
    recipes.firstOrNull { it.slug == rolledSlug }?.let { rolled ->
        DiceRollDialog(
            rolled, rollCount, serverUrl, today,
            onOpen = { rolledSlug = null; onOpen(rolled.slug) },
            onRollAgain = { roll(diceChapter) },
            onDismiss = { rolledSlug = null },
        )
    }

    Scaffold(
        topBar = {
            YcTopBar("Cookbook", actions = {
                DiceButton(
                    recipes, query, protein, diceChapter,
                    onRoll = { roll(diceChapter) },
                    onPick = { picked ->
                        onDiceChapter(picked)
                        roll(picked)
                    },
                )
                actions()
            })
        },
        containerColor = colors.background,
        modifier = modifier,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search ${recipes.size} recipes") },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = colors.surfaceContainer,
                        focusedContainerColor = colors.surfaceContainerLowest,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            if (chapters.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChapterTabs(listOf<String?>(null) + chapters, chapter) { picked ->
                        chapter = picked
                        if (protein != null && picked != null &&
                            Cookbook.filter(recipes, "", picked, protein).isEmpty()
                        ) protein = null
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        proteins.forEach { p ->
                            FilterChip(
                                selected = protein == p,
                                onClick = { protein = if (protein == p) null else p },
                                label = { Text(p) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.tertiaryContainer,
                                    selectedLabelColor = colors.onTertiaryContainer,
                                ),
                            )
                        }
                    }
                    SortButton(sort, chapterView = chapter == null) { sort = it }
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (recipes.isEmpty()) "No recipes yet. Tap + to add one from a link, or ask your agent to." else "Nothing matches.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            sections.forEach { section ->
                // The whole book: each chapter is a header followed by its recipes.
                if (section.title.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "chapter-" + section.title) {
                        ChapterHeader(section.title, section.recipes.size) { chapter = section.title }
                    }
                }
                items(section.recipes, key = { it.id }) { recipe ->
                    RecipeCard(recipe, serverUrl, recipe.slug == selectedSlug, today) { onOpen(recipe.slug) }
                }
            }
        }
    }
}

/**
 * The dice rolls a random recipe from the chapter picked last (Mains until changed). The small
 * arrow beside it picks another chapter (Drinks, Sides, Anything...), which rolls right away
 * and is remembered. Both honour the search and protein filter.
 */
@Composable
private fun DiceButton(
    recipes: List<RecipeEntity>,
    query: String,
    protein: String?,
    current: String?,
    onRoll: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Only chapters with something to roll, given the search and protein filter.
    val choices = remember(recipes, query, protein) {
        val matching = Cookbook.filter(recipes, query, null, protein)
        Cookbook.chapterOrder(matching).map { c -> c to matching.count { Cookbook.chapterOf(it) == c } } to matching.size
    }
    val enabled = choices.second > 0
    IconButton(enabled = enabled, onClick = onRoll) {
        Icon(Icons.Filled.Casino, contentDescription = "Surprise me with " + (current ?: "anything"))
    }
    Box {
        IconButton(enabled = enabled, onClick = { open = true }, modifier = Modifier.width(24.dp)) {
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick what the dice rolls")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Roll the dice for",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            (choices.first + (null to choices.second)).forEach { (chapter, count) ->
                DropdownMenuItem(
                    text = { Text(chapter ?: "Anything") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = if (chapter == current) "Last pick" else null,
                                tint = if (chapter == current) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { open = false; onPick(chapter) },
                )
            }
        }
    }
}

@Composable
private fun ChapterTabs(chapters: List<String?>, selected: String?, onPick: (String?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        chapters.forEach { c ->
            val on = c == selected
            Column(
                Modifier.clip(MaterialTheme.shapes.small).clickable { onPick(c) }.padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    c ?: "All",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                    color = if (on) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.height(2.dp).width(28.dp)
                        .background(if (on) colors.tertiary else Color.Transparent, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun ChapterHeader(title: String, count: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(
                (if (count == 1) "1 recipe" else "$count recipes") + "  ›",
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).padding(start = 8.dp, bottom = 4.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(36.dp).height(2.dp).background(colors.tertiary))
            Box(Modifier.weight(1f).height(1.dp).background(colors.outlineVariant))
        }
    }
}

@Composable
private fun SortButton(sort: Cookbook.Sort, chapterView: Boolean, onSort: (Cookbook.Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            // The default order needs no label; any other sort says what it is.
            val shown = if (sort == Cookbook.Sort.Chapters && !chapterView) Cookbook.Sort.AtoZ else sort
            Text(if (shown == Cookbook.Sort.Chapters) "Sort" else shown.label, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Cookbook.Sort.entries
                .filter { chapterView || it != Cookbook.Sort.Chapters }
                .forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        trailingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = { open = false; onSort(option) },
                    )
                }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeEntity,
    serverUrl: String?,
    selected: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow),
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier,
    ) {
        RecipeImage(serverUrl, recipe.id, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                recipe.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            Spacer(Modifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!recipe.totalTime.isNullOrBlank()) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        recipe.totalTime, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                recipe.rating?.takeIf { it > 0 }?.let { rating ->
                    Icon(Icons.Filled.Star, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(formatRating(rating), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            Text(
                Cookbook.lastCookedText(recipe.lastMade, today) ?: "Not cooked yet",
                style = MaterialTheme.typography.labelSmall,
                color = if (recipe.lastMade == null) colors.outline else colors.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatRating(r: Double): String = if (r % 1.0 == 0.0) r.toInt().toString() else "%.1f".format(r)
