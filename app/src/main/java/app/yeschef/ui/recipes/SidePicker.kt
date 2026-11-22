package app.yeschef.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yeschef.data.Cookbook
import app.yeschef.data.local.RecipeEntity
import app.yeschef.ui.SectionLabel

/** Chapters offered first when picking sides; everything else follows A to Z. */
private val sideChapters = listOf("Sides", "Soups & Salads", "Sauces & Basics", "Baking")

/** Pick one or more recipes to cook alongside the current one. Stateless apart from the picks. */
@Composable
fun SidePickerContent(
    recipes: List<RecipeEntity>,
    onCancel: () -> Unit,
    onCook: (List<String>) -> Unit,
    initialPicked: List<String> = emptyList(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(initialPicked) }
    val sections = remember(recipes, query) {
        val shown = Cookbook.filter(recipes, query, null, null).sortedBy { it.name.lowercase() }
        val byChapter = shown.groupBy(Cookbook::chapterOf)
        val first = sideChapters.mapNotNull { c -> byChapter[c]?.let { c to it } }
        val rest = shown.filter { Cookbook.chapterOf(it) !in sideChapters }
        first + listOfNotNull(rest.takeIf { it.isNotEmpty() }?.let { (if (first.isEmpty()) "Recipes" else "Everything else") to it })
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Text("Add a side", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Everything you pick goes on one timeline, so it's all ready together.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search recipes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 520.dp)) {
            sections.forEach { (title, list) ->
                item(key = "h-$title") { SectionLabel(title) }
                items(list, key = { it.slug }) { r ->
                    val on = r.slug in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (on) picked - r.slug else picked + r.slug }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Cookbook.minutes(r.totalTime)?.let {
                            Text("$it min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onCook(picked) }, enabled = picked.isNotEmpty()) {
                Text(if (picked.isEmpty()) "Cook together" else "Cook together (${picked.size + 1})")
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
