package app.yeschef.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.ShoppingRow
import app.yeschef.data.SyncState
import app.yeschef.data.groupForShopping
import app.yeschef.data.local.ShoppingItemEntity
import app.yeschef.data.remote.MealieJson
import app.yeschef.data.sync.SyncScheduler
import app.yeschef.ui.SyncAction
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ListPicker(lists.map { it.id to it.name }, selected?.name ?: "Shopping") { selectedId = it } },
                actions = { SyncAction(container) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncState == SyncState.Syncing,
            onRefresh = { scope.launch { repo.sync() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (selected == null) {
                EmptyState(syncState)
            } else {
                val items by remember(selected.id) { repo.shoppingItems(selected.id) }.collectAsStateWithLifecycle(emptyList())
                val labelOrder = remember(selected.labelOrderJson) {
                    runCatching { MealieJson.decodeFromString<Map<String, Int>>(selected.labelOrderJson) }.getOrDefault(emptyMap())
                }
                ShoppingList(
                    items = items,
                    labelOrder = labelOrder,
                    onToggle = { row ->
                        scope.launch {
                            repo.setChecked(row.id, !row.checked)
                            SyncScheduler.requestSync(context)
                        }
                    },
                    onAdd = { text ->
                        scope.launch {
                            repo.addItem(selected.id, text)
                            SyncScheduler.requestSync(context)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ListPicker(lists: List<Pair<String, String>>, title: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (lists.size <= 1) {
        Text(title)
        return
    }
    Box {
        TextButton(onClick = { open = true }) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose list")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            lists.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(id); open = false })
            }
        }
    }
}

@Composable
private fun EmptyState(state: SyncState) {
    LazyColumn(Modifier.fillMaxSize().padding(24.dp)) {
        item {
            Text(
                when (state) {
                    SyncState.Syncing -> "Loading your shopping lists…"
                    is SyncState.Offline -> "Can't reach Mealie yet. Connect the VPN and pull down to sync."
                    is SyncState.Failed -> state.message
                    else -> "No shopping lists yet. Create one in Mealie (or ask your agent to)."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ShoppingList(
    items: List<ShoppingItemEntity>,
    labelOrder: Map<String, Int>,
    onToggle: (ShoppingRow) -> Unit,
    onAdd: (String) -> Unit,
) {
    val (sections, checked) = remember(items, labelOrder) {
        groupForShopping(
            items.map { ShoppingRow(it.id, it.text, it.checked, it.position, it.labelId, it.labelName, it.labelColor, it.pending) },
            labelOrder,
        )
    }
    var showChecked by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (sections.isEmpty()) {
                item { Text("All done! Nothing left to buy.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            }
            sections.forEach { section ->
                item(key = "h-" + section.labelName) { SectionHeader(section.labelName, section.labelColor) }
                items(section.rows, key = { it.id }) { row -> ItemRow(row, onToggle) }
            }
            if (checked.isNotEmpty()) {
                item(key = "checked-header") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showChecked = !showChecked }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("In the cart (${checked.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(if (showChecked) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
                if (showChecked) items(checked, key = { it.id }) { row -> ItemRow(row, onToggle) }
            }
        }
        HorizontalDivider()
        AddItemBar(onAdd)
    }
}

@Composable
private fun SectionHeader(name: String, color: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(parseColor(color) ?: MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ItemRow(row: ShoppingRow, onToggle: (ShoppingRow) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onToggle(row) }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.checked, onCheckedChange = { onToggle(row) })
        Text(
            row.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (row.checked) TextDecoration.LineThrough else null,
            color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (row.pending) Icon(Icons.Filled.CloudUpload, "Waiting to sync", Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AddItemBar(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    Row(Modifier.fillMaxWidth().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add an item") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) { Icon(Icons.Filled.Add, "Add") }
    }
}

private fun parseColor(hex: String?): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex ?: return null))
}.getOrNull()
