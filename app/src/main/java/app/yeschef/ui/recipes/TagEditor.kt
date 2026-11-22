package app.yeschef.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.yeschef.data.Cookbook
import app.yeschef.ui.SectionLabel

/**
 * Pick a recipe's chapter (one Mealie category), protein and other tags. Stateless,
 * so it can be shown in a bottom sheet and in screenshots.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorContent(
    selection: Cookbook.TagSelection,
    chapters: List<String>,
    tags: List<String>,
    onChange: (Cookbook.TagSelection) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newTag by rememberSaveable { mutableStateOf("") }
    // Tags picked but not in the offered list (e.g. just typed) still need a chip.
    val allTags = tags + selection.tags.filter { t -> tags.none { it.equals(t, ignoreCase = true) } }.sorted()
    fun addTag() {
        val t = newTag.trim()
        if (t.isNotEmpty()) {
            val existing = allTags.firstOrNull { it.equals(t, ignoreCase = true) } ?: t
            onChange(selection.copy(tags = selection.tags + existing))
        }
        newTag = ""
    }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text(
            "Chapter and tags",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        Text(
            "Saved to Mealie. Works offline too; changes go with the next sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )

        SectionLabel("Chapter")
        Chips {
            chapters.forEach { c ->
                Chip(c, selection.chapter == c) { onChange(selection.copy(chapter = if (selection.chapter == c) null else c)) }
            }
        }

        SectionLabel("Protein")
        Chips {
            Cookbook.proteins.map { it.first }.forEach { p ->
                val on = p in selection.proteins
                Chip(p, on) { onChange(selection.copy(proteins = if (on) selection.proteins - p else selection.proteins + p)) }
            }
        }

        SectionLabel("Style and other tags")
        Chips {
            allTags.forEach { t ->
                val on = t in selection.tags
                Chip(t, on) { onChange(selection.copy(tags = if (on) selection.tags - t else selection.tags + t)) }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                placeholder = { Text("Add a tag, e.g. Holiday") },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::addTag, enabled = newTag.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add tag") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onSave, shape = CircleShape) { Text("Save") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.tertiaryContainer,
            selectedLabelColor = colors.onTertiaryContainer,
            selectedLeadingIconColor = colors.tertiary,
        ),
    )
}
