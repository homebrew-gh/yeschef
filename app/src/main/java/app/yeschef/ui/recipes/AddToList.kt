package app.yeschef.ui.recipes

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.scaleIngredient
import app.yeschef.ui.SectionLabel
import kotlinx.coroutines.launch

/**
 * Add a recipe's ingredients to a shopping list: pick the list (or make one), the
 * servings, and untick what you already have. Mealie scales and merges the lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddToListContent(
    recipeName: String,
    /** Ingredient lines to offer, by their index in the recipe, as the recipe writes them. */
    lines: List<Pair<Int, String>>,
    baseServings: Double?,
    lists: List<Pair<String, String>>,
    initialListId: String?,
    onAdd: (listId: String, scale: Double, skip: Set<Int>) -> Unit,
    /** Creates a list on the server and returns its id; throws when offline. */
    onCreateList: suspend (String) -> String?,
    onCancel: () -> Unit,
    initialServings: Double? = null,
    initialSkip: Set<Int> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var listId by rememberSaveable { mutableStateOf(initialListId ?: lists.firstOrNull()?.first) }
    var servings by rememberSaveable { mutableStateOf(initialServings ?: baseServings ?: 1.0) }
    var factor by rememberSaveable { mutableStateOf(1.0) }
    var skip by rememberSaveable { mutableStateOf(initialSkip.toList()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }
    val scale = baseServings?.let { servings / it } ?: factor
    val count = lines.count { it.first !in skip }

    fun create() {
        val name = newName.trim()
        if (name.isEmpty() || creating) return
        creating = true
        createError = null
        scope.launch {
            try {
                onCreateList(name)?.let { listId = it }
                newName = ""
            } catch (e: Exception) {
                createError = "Couldn't create the list. New lists need Mealie, so connect the VPN and try again."
            }
            creating = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text("Add to grocery list", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
        Text(recipeName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))

        SectionLabel("List")
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lists.forEach { (id, name) ->
                val on = id == listId
                FilterChip(
                    selected = on,
                    onClick = { listId = id },
                    label = { Text(name) },
                    leadingIcon = if (on) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.tertiaryContainer,
                        selectedLabelColor = colors.onTertiaryContainer,
                        selectedLeadingIconColor = colors.tertiary,
                    ),
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("New list, e.g. Costco") },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.weight(1f),
            )
            if (creating) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
            } else {
                IconButton(onClick = ::create, enabled = newName.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Create list") }
            }
        }
        createError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(horizontal = 20.dp)) }

        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Ingredients", Modifier.weight(1f))
            Surface(shape = CircleShape, color = colors.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val steps = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
                    IconButton(
                        onClick = {
                            if (baseServings != null) servings = (servings - 1).coerceAtLeast(1.0)
                            else factor = steps.lastOrNull { it < factor } ?: factor
                        },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer servings", modifier = Modifier.size(18.dp)) }
                    Text(
                        if (baseServings != null) "Serves ${formatAmount(servings)}" else "×" + formatAmount(factor),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            if (baseServings != null) servings += 1
                            else factor = steps.firstOrNull { it > factor } ?: factor
                        },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = "More servings", modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Text(
            "Untick what you already have.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                lines.forEach { (index, text) ->
                    val on = index !in skip
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { skip = if (on) skip + index else skip - index }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (on) colors.primary else colors.surface)
                                .then(if (on) Modifier else Modifier.border(1.5.dp, colors.outline, RoundedCornerShape(6.dp))),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            scaleIngredient(text, scale),
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (on) null else TextDecoration.LineThrough,
                            color = if (on) colors.onSurface else colors.outline,
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (lists.isEmpty()) {
                Text("Make a list first.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = { listId?.let { onAdd(it, scale, skip.toSet()) } },
                enabled = listId != null && count > 0,
                shape = CircleShape,
            ) { Text(if (count == 1) "Add 1 item" else "Add $count items") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
