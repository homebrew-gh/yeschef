package app.yeschef.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Casino
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.VerticalDivider
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
import app.yeschef.ui.HatLogo
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.isWide

/** Recipe grid; on the tablet the selected recipe opens beside it. */
@Composable
fun RecipesScreen(container: AppContainer, openSlug: String?, onOpen: (String?) -> Unit) {
    val recipes by remember { container.repository.recipes() }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    val list: @Composable (Modifier) -> Unit = { modifier ->
        RecipeGrid(recipes, config?.serverUrl, openSlug, onOpen, actions = { SyncAction(container) }, modifier = modifier)
    }
    if (isWide()) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(400.dp).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (openSlug != null) {
                    RecipeDetailScreen(container, openSlug, onBack = null)
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        HatLogo(120.dp)
                        Text("Pick a recipe", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else if (openSlug != null) {
        BackHandler { onOpen(null) }
        RecipeDetailScreen(container, openSlug, onBack = { onOpen(null) })
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

    Scaffold(
        topBar = {
            YcTopBar("Cookbook", actions = {
                IconButton(enabled = shown.isNotEmpty(), onClick = { surprise(shown, today)?.let { onOpen(it.slug) } }) {
                    Icon(Icons.Filled.Casino, contentDescription = "Surprise me")
                }
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
                        if (recipes.isEmpty()) "No recipes yet. Add some in Mealie, or ask your agent to." else "Nothing matches.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            sections.forEach { section ->
                if (section.title.isNotEmpty()) {
                    // The whole book: each chapter is a header and a shelf of cards to swipe through.
                    item(span = { GridItemSpan(maxLineSpan) }, key = "chapter-" + section.title) {
                        Column {
                            ChapterHeader(section.title, section.recipes.size) { chapter = section.title }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 10.dp),
                            ) {
                                items(section.recipes, key = { it.id }) { recipe ->
                                    RecipeCard(recipe, serverUrl, recipe.slug == selectedSlug, today, Modifier.width(176.dp)) { onOpen(recipe.slug) }
                                }
                            }
                        }
                    }
                } else {
                    items(section.recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe, serverUrl, recipe.slug == selectedSlug, today) { onOpen(recipe.slug) }
                    }
                }
            }
        }
    }
}

/** A random recipe from what's shown, preferring ones not cooked in the last two weeks. */
private fun surprise(shown: List<RecipeEntity>, today: LocalDate): RecipeEntity? {
    val fresh = shown.filter { r ->
        val text = Cookbook.lastCookedText(r.lastMade, today)
        text == null || !(text == "Cooked today" || text == "Cooked yesterday" || text.endsWith("days ago"))
    }
    return (fresh.ifEmpty { shown }).randomOrNull()
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
            Text(if (sort == Cookbook.Sort.Chapters && !chapterView) Cookbook.Sort.AtoZ.label else sort.label, maxLines = 1)
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
