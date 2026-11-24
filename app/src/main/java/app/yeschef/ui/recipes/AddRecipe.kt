package app.yeschef.ui.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yeschef.AppContainer
import app.yeschef.data.AiStatus
import app.yeschef.data.ImportStage
import app.yeschef.data.RecipeImport
import app.yeschef.ui.SectionLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Most photos a recipe can span (a two-page spread plus a card, say). */
private const val MAX_PHOTOS = 4

/**
 * "Add a recipe" sheet: import from a link (always), or from photos when Mealie has an AI
 * provider for images. Mealie does the work; the new recipe opens when it's ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeSheet(container: AppContainer, initialUrl: String, onDismiss: () -> Unit, onImported: (String) -> Unit) {
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var ai by remember { mutableStateOf<AiStatus?>(null) }
    var stage by remember { mutableStateOf<ImportStage?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    LaunchedEffect(Unit) { ai = repo.aiStatus() }

    fun run(first: ImportStage, block: suspend () -> String) {
        if (stage != null) return
        stage = first
        error = null
        val fromPhotos = first == ImportStage.AiReadingPhotos
        scope.launch {
            try {
                onImported(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = importError(e, fromPhotos)
            }
            stage = null
        }
    }

    fun importLink() {
        val link = RecipeImport.firstUrl(url) ?: url.trim().takeIf { it.isNotEmpty() }?.let { "https://$it" } ?: return
        run(ImportStage.ReadingPage) { repo.importFromUrl(link, aiFallback = ai?.enabled == true) { stage = it } }
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        run(ImportStage.AiReadingPhotos) { repo.importFromPhotos(uris.take(MAX_PHOTOS).map { preparePhoto(context, it, maxSide = 2048) }) }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) importPhotos(listOf(uri))
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        importPhotos(uris)
    }

    // An import in progress keeps the sheet open: Mealie finishes it either way, and this is where the result lands.
    val busy = stage != null
    val busyNow by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busyNow },
    )
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        AddRecipeContent(
            url = url,
            onUrlChange = { url = it; error = null },
            ai = ai,
            stage = stage,
            error = error,
            onImportLink = ::importLink,
            onTakePhoto = {
                val uri = newCameraUri(context)
                cameraUri = uri
                takePhoto.launch(uri)
            },
            onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCancel = onDismiss,
        )
    }
}

private fun importError(e: Throwable, fromPhotos: Boolean): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "Mealie rejected the API token. Check it in Settings."
        400, 422 -> RecipeImport.friendlyError(RecipeImport.errorMessage(runCatching { e.response()?.errorBody()?.string() }.getOrNull()), fromPhotos)
        else -> "Mealie returned HTTP ${e.code()}."
    }
    is java.net.SocketTimeoutException -> "Mealie is taking a long time. The recipe may still appear after the next sync."
    is java.io.IOException -> "Couldn't reach Mealie. Adding recipes needs the server, so connect the VPN and try again."
    else -> "Couldn't add that recipe (${e.message ?: e.javaClass.simpleName})."
}

/** Stateless "Add a recipe" sheet body, also used for screenshots. */
@Composable
fun AddRecipeContent(
    url: String,
    onUrlChange: (String) -> Unit,
    /** Null while YesChef asks Mealie what its AI can do. */
    ai: AiStatus?,
    stage: ImportStage?,
    error: String?,
    onImportLink: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val busy = stage != null
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Text("Add a recipe", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
        Text(
            "Mealie saves it to your cookbook, and it opens here when it's ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )

        SectionLabel("From a link")
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            enabled = !busy,
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            placeholder = { Text("Paste a recipe page's link") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onImportLink() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        if (ai?.enabled == true) {
            Hint("If Mealie can't find the recipe on the page, your AI reads it instead.")
        }
        Button(
            onClick = onImportLink,
            enabled = !busy && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) { Text("Import from link") }

        when {
            ai?.images == true -> {
                SectionLabel("From a photo · AI")
                Text(
                    "Snap a cookbook page or a recipe card and your AI writes it up. Choose up to $MAX_PHOTOS photos for a recipe that runs over pages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onTakePhoto, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Take photo")
                    }
                    OutlinedButton(onClick = onPickPhotos, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose photos")
                    }
                }
            }
            ai != null -> Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.outline, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ai.enabled) "To add recipes from photos too, pick an image provider in Mealie's AI settings."
                    else "Set up an AI provider in Mealie to add recipes from photos too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.outline,
                )
            }
        }

        if (stage != null) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (stage) {
                        ImportStage.ReadingPage -> "Mealie is reading the page…"
                        ImportStage.AiReadingPage -> "No recipe found on the page, so your AI is reading it. This can take a minute…"
                        ImportStage.AiReadingPhotos -> "Your AI is reading the photos. This can take a minute…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
    )
}
