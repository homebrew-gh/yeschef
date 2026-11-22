package app.yeschef.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yeschef.data.DraftIngredient
import app.yeschef.data.DraftStep
import app.yeschef.data.DraftTime
import app.yeschef.data.RecipeDraft
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar

/**
 * Edit a recipe by hand: name, description, servings, times, ingredients and steps.
 * Stateless, so it can be shown in screenshots.
 */
@Composable
fun RecipeEditorContent(
    draft: RecipeDraft,
    onChange: (RecipeDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val canSave = draft.name.isNotBlank()
    Scaffold(
        topBar = {
            YcTopBar(
                title = "Edit recipe",
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    Button(onClick = onSave, enabled = canSave, shape = CircleShape, modifier = Modifier.padding(end = 12.dp)) { Text("Save") }
                },
            )
        },
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 860.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { onChange(draft.copy(name = it)) },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = !canSave,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { onChange(draft.copy(description = it)) },
                        label = { Text("Description") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.servings,
                        onValueChange = { v -> onChange(draft.copy(servings = v.filter { it.isDigit() || it == '.' || it == ',' })) },
                        label = { Text("Serves") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp),
                    )
                }
            }

            item { SectionLabel("Times") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeRow("Prep", draft.prep) { onChange(draft.withPrep(it)) }
                    TimeRow("Cook", draft.cook) { onChange(draft.withCook(it)) }
                    TimeRow("Total", draft.total) { onChange(draft.withTotal(it)) }
                    Text(
                        "Leave a time blank if you don't know it. Cook 0 means no cooking. Total follows prep and cook until you change it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            item { SectionLabel("Ingredients") }
            item {
                Text(
                    "One per line, amount first: \"2 cups flour, sifted\". Mealie reads the amounts when it syncs, so scaling and the shopping list keep working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(draft.ingredients, key = { _, line -> "i" + line.referenceId }) { index, line ->
                Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 6.dp)) {
                    line.title?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = line.text,
                            onValueChange = { onChange(draft.setIngredientText(index, it)) },
                            placeholder = { Text("e.g. 1 tbsp olive oil") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                        )
                        MoveButtons(
                            canUp = index > 0,
                            canDown = index < draft.ingredients.lastIndex,
                            onUp = { onChange(draft.moveIngredient(index, -1)) },
                            onDown = { onChange(draft.moveIngredient(index, 1)) },
                            onDelete = { onChange(draft.removeIngredient(index)) },
                            what = "ingredient",
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { onChange(draft.copy(ingredients = draft.ingredients + DraftIngredient(""))) }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add ingredient")
                }
            }

            item { SectionLabel("Method") }
            itemsIndexed(draft.steps, key = { _, step -> "s" + step.id }) { index, step ->
                Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                    step.title?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.padding(top = 14.dp).size(30.dp).background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { v -> onChange(draft.copy(steps = draft.steps.mapIndexed { i, s -> if (i == index) s.copy(text = v) else s })) },
                            placeholder = { Text("What to do") },
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                        )
                        MoveButtons(
                            canUp = index > 0,
                            canDown = index < draft.steps.lastIndex,
                            onUp = { onChange(draft.moveStep(index, -1)) },
                            onDown = { onChange(draft.moveStep(index, 1)) },
                            onDelete = { onChange(draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != index })) },
                            what = "step",
                        )
                    }
                }
            }
            item {
                TextButton(onClick = { onChange(draft.copy(steps = draft.steps + DraftStep(""))) }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add step")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Saved to Mealie. Works offline too; changes go with the next sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onSave, enabled = canSave, shape = CircleShape) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, time: DraftTime, onChange: (DraftTime) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
        OutlinedTextField(
            value = time.hours,
            onValueChange = { v -> onChange(time.copy(hours = v.filter(Char::isDigit).take(3))) },
            suffix = { Text("hr") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(104.dp),
        )
        OutlinedTextField(
            value = time.minutes,
            onValueChange = { v -> onChange(time.copy(minutes = v.filter(Char::isDigit).take(4))) },
            suffix = { Text("min") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp),
        )
    }
}

@Composable
private fun MoveButtons(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit, what: String) {
    Column {
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.KeyboardArrowUp, "Move $what up", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.KeyboardArrowDown, "Move $what down", modifier = Modifier.size(20.dp))
        }
    }
    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Filled.Delete, "Remove $what", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}
