package app.yeschef.ui.recipes

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.yeschef.data.cook.CookNote
import java.util.Locale

/**
 * Starts Android speech recognition and hands back what was said.
 *
 * Prefers the in-app [SpeechRecognizer] (with a visible listening dialog, live partial results,
 * and a one-tap switch to typing) so voice notes work reliably on tablets and phones regardless
 * of whether the Google Search speech overlay or offline packs are installed. Falls back to the
 * system [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] intent, and finally to [onFallbackToType].
 */
@Composable
fun rememberDictation(
    prompt: () -> String,
    onText: (String) -> Unit,
    onFallbackToType: () -> Unit = {},
): () -> Boolean {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val latestPrompt by rememberUpdatedState(prompt)
    val latestFallback by rememberUpdatedState(onFallbackToType)

    var listening by remember { mutableStateOf(false) }

    val intentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spoken.isNullOrEmpty()) {
                latest(spoken.replaceFirstChar { c -> c.uppercase() })
                return@rememberLauncherForActivityResult
            }
        }
        // Only stay silent if the user explicitly cancelled; on recognizer error fall back to typing.
        if (result.resultCode != Activity.RESULT_CANCELED) {
            latestFallback()
        }
    }

    fun launchSystemIntent(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, latestPrompt())
        return try {
            intentLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
        } else if (!launchSystemIntent()) {
            latestFallback()
        }
    }

    if (listening) {
        VoiceListeningDialog(
            title = latestPrompt(),
            onResult = { spoken ->
                listening = false
                latest(spoken.replaceFirstChar { c -> c.uppercase() })
            },
            onTypeInstead = {
                listening = false
                latestFallback()
            },
            onDismiss = { listening = false },
        )
    }

    return remember(context, intentLauncher, permissionLauncher) {
        {
            if (canUseSpeechRecognizer(context)) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    listening = true
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                true
            } else {
                launchSystemIntent()
            }
        }
    }
}

private fun canUseSpeechRecognizer(context: Context): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

@Composable
private fun VoiceListeningDialog(
    title: String,
    onResult: (String) -> Unit,
    onTypeInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val latestResult by rememberUpdatedState(onResult)

    var retryNonce by remember { mutableIntStateOf(0) }
    var preferOnDevice by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Starting microphone…") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(retryNonce, preferOnDevice) {
        val recognizer = createRecognizer(context, preferOnDevice)
        if (recognizer == null) {
            errorMessage = "No speech recognizer found on this device."
            return@DisposableEffect onDispose {}
        }
        errorMessage = null
        statusText = "Starting microphone…"
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText = "Listening… speak your note"
            }
            override fun onBeginningOfSpeech() {
                statusText = "Listening…"
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                statusText = "Finishing…"
            }
            override fun onError(error: Int) {
                // If network recognition failed while offline, automatically try the on-device engine once.
                val networkFail = error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                if (networkFail && !preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    preferOnDevice = true
                    return
                }
                val captured = partialText.trim()
                if (captured.isNotEmpty() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    latestResult(captured)
                    return
                }
                errorMessage = speechErrorMessage(error)
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    ?: partialText.trim()
                if (spoken.isNotEmpty()) {
                    latestResult(spoken)
                } else {
                    errorMessage = "Didn't catch that. Tap Try again or Type."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) partialText = text
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { errorMessage = "Couldn't start voice recognition." }
        onDispose {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(64.dp).background(
                        if (errorMessage != null) colors.errorContainer else colors.tertiaryContainer,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (errorMessage != null) colors.onErrorContainer else colors.onTertiaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = errorMessage ?: partialText.ifBlank { statusText },
                    style = if (partialText.isNotBlank() && errorMessage == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) colors.error else if (partialText.isNotBlank()) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            when {
                partialText.isNotBlank() -> TextButton(onClick = { onResult(partialText.trim()) }) { Text("Save") }
                errorMessage != null -> TextButton(onClick = { partialText = ""; retryNonce++ }) { Text("Try again") }
                else -> TextButton(onClick = onTypeInstead) { Text("Type instead") }
            }
        },
        dismissButton = {
            Row {
                if (partialText.isNotBlank() || errorMessage != null) {
                    TextButton(onClick = onTypeInstead) { Text("Type") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun createRecognizer(context: Context, preferOnDevice: Boolean): SpeechRecognizer? {
    val hasOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    val hasDefault = SpeechRecognizer.isRecognitionAvailable(context)
    return when {
        preferOnDevice && hasOnDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        hasDefault -> SpeechRecognizer.createSpeechRecognizer(context)
        hasOnDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else -> null
    }
}

private fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything. Tap Try again or Type."
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't read audio from the microphone."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice notes."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs a connection or an offline speech pack. Tap Type to enter your note."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy. Tap Try again."
    else -> "Couldn't use voice recognition right now. Tap Type to enter your note."
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
