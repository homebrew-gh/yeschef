package app.yeschef.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.ShoppingRow
import app.yeschef.data.ShoppingSection
import app.yeschef.data.SyncState
import app.yeschef.data.assignRecipeColors
import app.yeschef.data.groupForShopping
import app.yeschef.data.parseRecipeIds
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.sync.SyncScheduler
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.theme.OverlineStyle
import app.yeschef.ui.theme.RecipeTagColors
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(container: AppContainer) {
    val repo = container.repository
    val lists by remember { repo.shoppingLists() }.collectAsStateWithLifecycle(emptyList())
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(lists) {
        if (lists.isNotEmpty() && lists.none { it.id == selectedId }) selectedId = lists.first().id
    }
    val selected = lists.firstOrNull { it.id == selectedId }
    val syncState by repo.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items by remember(selected?.id) { selected?.let { repo.shoppingItems(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    val labelOrder = remember(selected?.labelOrderJson) {
        selected?.labelOrderJson?.let { runCatching { MealieJson.decodeFromString<Map<String, Int>>(it) }.getOrNull() }.orEmpty()
    }
    val recipeNames = remember(selected?.recipeNamesJson) {
        selected?.recipeNamesJson?.let { runCatching { MealieJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
    }

    ShoppingContent(
        title = selected?.name ?: "Shopping",
        lists = lists.map { it.id to it.name },
        onPickList = { selectedId = it },
        hasList = selected != null,
        rows = items.map {
            ShoppingRow(it.id, it.text, it.checked, it.position, it.labelId, it.labelName, it.labelColor, it.pending, parseRecipeIds(it.recipeIds))
        },
        labelOrder = labelOrder,
        recipeNames = recipeNames,
        emptyMessage = when (val s = syncState) {
            SyncState.Syncing -> "Loading your shopping lists…"
            is SyncState.Offline -> "Can't reach Mealie yet. Connect the VPN and pull down to sync."
            is SyncState.Failed -> s.message
            else -> "No shopping lists yet. Create one in Mealie, or ask your agent to."
        },
        refreshing = syncState == SyncState.Syncing,
        onRefresh = { scope.launch { repo.sync() } },
        onToggle = { row ->
            scope.launch {
                repo.setChecked(row.id, !row.checked)
                SyncScheduler.requestSync(context)
            }
        },
        onAdd = { text ->
            selected?.let { list ->
                scope.launch {
                    repo.addItem(list.id, text)
                    SyncScheduler.requestSync(context)
                }
            }
        },
        actions = { SyncAction(container) },
    )
}

/** Stateless shopping screen, also used for screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingContent(
    title: String,
    lists: List<Pair<String, String>>,
    onPickList: (String) -> Unit,
    hasList: Boolean,
    rows: List<ShoppingRow>,
    labelOrder: Map<String, Int>,
    recipeNames: Map<String, String>,
    emptyMessage: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggle: (ShoppingRow) -> Unit,
    onAdd: (String) -> Unit,
    actions: @Composable () -> Unit,
) {
    var recipeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val colorSlots = remember(recipeNames) { assignRecipeColors(recipeNames, RecipeTagColors.size) }
    val recipeColor: (String) -> Color? = { id -> colorSlots[id]?.let { RecipeTagColors[it] } }
    val visible = remember(rows, recipeFilter) {
        if (recipeFilter == null) rows else rows.filter { recipeFilter in it.recipeIds }
    }
    val (sections, checked) = remember(visible, labelOrder) { groupForShopping(visible, labelOrder) }
    var showChecked by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            YcTopBar(title = "", actions = { actions() }, navigationIcon = {
                ListPicker(lists, title, onPickList)
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!hasList) {
                Text(emptyMessage, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                return@PullToRefreshBox
            }
            Column(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    item(key = "progress") { ProgressCard(done = rows.count { it.checked }, total = rows.size) }
                    if (recipeNames.isNotEmpty()) {
                        item(key = "recipes") {
                            RecipeFilterRow(recipeNames, colorSlots, recipeFilter) { recipeFilter = it }
                        }
                    }
                    if (sections.isEmpty()) {
                        item(key = "done") {
                            Text(
                                if (rows.isEmpty()) "This list is empty. Add something below." else "All done! Everything's in the cart.",
                                Modifier.padding(24.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    sections.forEach { section ->
                        item(key = "s-" + section.labelName) { SectionCard(section, recipeNames, recipeColor, onToggle) }
                    }
                    if (checked.isNotEmpty()) {
                        item(key = "checked") {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showChecked = !showChecked }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "IN THE CART · ${checked.size}",
                                        style = OverlineStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(if (showChecked) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                                }
                                if (showChecked) {
                                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                        Column { checked.forEach { ItemRow(it, recipeNames, recipeColor, onToggle) } }
                                    }
                                }
                            }
                        }
                    }
                }
                AddItemBar(onAdd)
            }
        }
    }
}

@Composable
private fun ListPicker(lists: List<Pair<String, String>>, title: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp)) {
        Row(
            Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = lists.size > 1) { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lists.size > 1) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose list")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lists.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onPick(id); open = false }) }
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", style = MaterialTheme.typography.headlineLarge, color = colors.onPrimaryContainer)
                Text(" of $total in the cart", style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                color = colors.tertiary,
                trackColor = colors.surface.copy(alpha = 0.7f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeFilterRow(
    recipeNames: Map<String, String>,
    colorSlots: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All items") })
        recipeNames.entries.sortedBy { colorSlots[it.key] ?: 0 }.forEach { (id, name) ->
            val color = RecipeTagColors[colorSlots[id] ?: 0]
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(if (selected == id) null else id) },
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Box(Modifier.size(10.dp).background(color, CircleShape)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun SectionCard(
    section: ShoppingSection,
    recipeNames: Map<String, String>,
    recipeColor: (String) -> Color?,
    onToggle: (ShoppingRow) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(start = 8.dp, bottom = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(parseColor(section.labelColor) ?: colors.tertiary, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(section.labelName.uppercase(), style = OverlineStyle, color = colors.primary)
            Spacer(Modifier.width(6.dp))
            Text("${section.rows.size}", style = OverlineStyle, color = colors.outline)
        }
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow, tonalElevation = 0.dp) {
            Column {
                section.rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant.copy(alpha = 0.6f))
                    ItemRow(row, recipeNames, recipeColor, onToggle)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    row: ShoppingRow,
    recipeNames: Map<String, String>,
    recipeColor: (String) -> Color?,
    onToggle: (ShoppingRow) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tagColors = row.recipeIds.mapNotNull(recipeColor)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onToggle(row) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Recipe colour stripe: one band per recipe the item came from.
        Column(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp))) {
            tagColors.forEach { Box(Modifier.weight(1f).fillMaxWidth().background(if (row.checked) it.copy(alpha = 0.35f) else it)) }
        }
        Checkbox(
            checked = row.checked,
            onCheckedChange = { onToggle(row) },
            colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.outline),
        )
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                color = if (row.checked) colors.outline else colors.onSurface,
            )
            val tags = row.recipeIds.mapNotNull { id -> recipeNames[id]?.let { id to it } }
            if (tags.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    tags.forEachIndexed { i, (id, name) ->
                        if (i > 0) Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(7.dp).background(recipeColor(id) ?: colors.outline, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
        if (row.pending) {
            Icon(Icons.Filled.CloudUpload, "Waiting to sync", Modifier.padding(end = 16.dp).size(18.dp), tint = colors.outline)
        }
    }
}

@Composable
private fun AddItemBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Add an item") },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Add, "Add")
            }
        }
    }
}

private fun parseColor(hex: String?): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex ?: return null))
}.getOrNull()
