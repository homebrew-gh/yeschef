package app.yeschef.ui.recipes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.yeschef.data.cook.CookNote

/**
 * Starts Android's own speech recognizer (on the phone, offline when it can) and hands
 * back what was said. Returns a function that starts it; that function returns false
 * when the phone has no recognizer, so the caller can offer typing instead.
 */
@Composable
fun rememberDictation(prompt: () -> String, onText: (String) -> Unit): () -> Boolean {
    val latest by rememberUpdatedState(onText)
    val latestPrompt by rememberUpdatedState(prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { latest(it.replaceFirstChar { c -> c.uppercase() }) }
    }
    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, latestPrompt())
            try {
                launcher.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

/** Cook mode's note button: tap to speak a note. The badge counts notes not yet saved. */
@Composable
fun CookNoteButton(count: Int, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(Icons.Filled.Mic, contentDescription = "Speak a cooking note")
        }
    }
}

/** Type a note, or fix one. */
@Composable
fun CookNoteDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. Needed 5 more minutes in the oven") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The notes from this cook, shown on leaving cook mode: fix, delete or add notes, then
 * save them to the recipe in Mealie. Stateless, so it shows in a sheet and in screenshots.
 */
@Composable
fun CookNotesReviewContent(
    recipeName: String,
    notes: List<CookNote>,
    clock: (Long) -> String,
    markCooked: Boolean,
    onMarkCooked: (Boolean) -> Unit,
    onEdit: (CookNote) -> Unit,
    onDelete: (CookNote) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text(
            "Cooking notes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        Text(
            "Saved to $recipeName's timeline in Mealie. Ask your assistant to review them and suggest changes for next time.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
        notes.sortedBy { it.at }.forEach { note ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val where = note.step?.let { s -> "Step $s" + (note.stepLabel?.let { " · $it" } ?: "") }
                        Text(
                            listOfNotNull(clock(note.at), where).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.tertiary,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = { onEdit(note) }) { Icon(Icons.Filled.Edit, "Edit note") }
                    IconButton(onClick = { onDelete(note) }) { Icon(Icons.Filled.Delete, "Delete note") }
                }
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) { Text("Type a note") }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = markCooked, onCheckedChange = onMarkCooked)
            Text("Also mark as cooked today", style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onKeep) { Text("Keep for later") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = notes.any { it.text.isNotBlank() }) { Text("Save to Mealie") }
        }
    }
}

/**
 * Review sheet for whole-meal cook mode: groups spoken notes by dish so you can fix
 * speech-to-text slips before saving them to each recipe's timeline in Mealie.
 */
@Composable
fun MealCookNotesReviewContent(
    groups: List<Triple<String, String, List<CookNote>>>, // (recipeId, recipeName, notes)
    clock: (Long) -> String,
    markCooked: Boolean,
    onMarkCooked: (Boolean) -> Unit,
    onEdit: (recipeId: String, note: CookNote) -> Unit,
    onDelete: (recipeId: String, note: CookNote) -> Unit,
    onSave: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val nonEmpty = groups.filter { it.third.isNotEmpty() }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text(
            "Cooking notes",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        Text(
            "Saved to each recipe's timeline in Mealie.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
        nonEmpty.forEach { (recipeId, recipeName, notes) ->
            Text(
                recipeName,
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
            notes.sortedBy { it.at }.forEach { note ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val where = note.step?.let { s -> "Step $s" + (note.stepLabel?.let { " · $it" } ?: "") }
                            Text(
                                listOfNotNull(clock(note.at), where).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.tertiary,
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(note.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { onEdit(recipeId, note) }) { Icon(Icons.Filled.Edit, "Edit note") }
                        IconButton(onClick = { onDelete(recipeId, note) }) { Icon(Icons.Filled.Delete, "Delete note") }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = markCooked, onCheckedChange = onMarkCooked)
            Text("Also mark recipes as cooked today", style = MaterialTheme.typography.bodyLarge)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onKeep) { Text("Keep for later") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = nonEmpty.any { g -> g.third.any { it.text.isNotBlank() } },
            ) { Text("Save to Mealie") }
        }
    }
}
