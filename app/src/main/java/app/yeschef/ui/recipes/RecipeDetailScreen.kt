package app.yeschef.ui.recipes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import app.yeschef.data.RecipeDraft
import app.yeschef.data.changesFrom
import app.yeschef.data.cook.CookNote
import app.yeschef.data.cook.CookNotes
import app.yeschef.ui.cook.clockText
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Mic
import app.yeschef.data.remote.MealieJson
import app.yeschef.ui.cook.MealRequest
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.automirrored.filled.Label
import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.baseServings
import app.yeschef.data.cook.scaleIngredient
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import java.time.LocalDate
import app.yeschef.data.sync.SyncScheduler
import app.yeschef.data.Cookbook
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.yeschef.ui.ImageVersions
import kotlinx.coroutines.launch
import retrofit2.HttpException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.cook.DetectedDuration
import app.yeschef.data.cook.detectDurations
import app.yeschef.data.cook.formatShort
import app.yeschef.data.cook.IngredientLine
import app.yeschef.data.cook.StepLine
import app.yeschef.data.cook.ingredientsForSteps
import app.yeschef.data.cook.timerStepLabel
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.isWide
import app.yeschef.ui.cook.CustomTimerDialog
import app.yeschef.ui.cook.TimerChip
import app.yeschef.ui.cook.rememberTimerStarter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    container: AppContainer,
    slug: String,
    onBack: (() -> Unit)?,
    onCookMeal: (MealRequest) -> Unit = {},
    onSlugChanged: (String) -> Unit = {},
) {
    val repo = container.repository
    val data by remember(slug) { repo.recipe(slug) }.collectAsStateWithLifecycle(null to null)
    val (entity, detail) = data
    val config by container.config.collectAsStateWithLifecycle()
    LaunchedEffect(slug) { repo.refreshRecipe(slug) }
    // Renaming a recipe changes its slug in Mealie; follow it once the sync brings it back.
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(entity?.id) { entity?.id?.let { recipeId = it } }
    LaunchedEffect(entity == null, recipeId) {
        val id = recipeId
        if (entity == null && id != null) repo.slugOf(id)?.takeIf { it != slug }?.let(onSlugChanged)
    }

    // Cook mode keeps the screen awake while this recipe is open.
    var cookMode by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(cookMode) {
        view.keepScreenOn = cookMode
        onDispose { view.keepScreenOn = false }
    }
    val recipeName = detail?.name ?: entity?.name ?: "Timer"
    val startTimer = rememberTimerStarter(container.timers)
    var showCustomTimer by rememberSaveable { mutableStateOf(false) }
    if (showCustomTimer) {
        CustomTimerDialog(
            defaultLabel = recipeName,
            onDismiss = { showCustomTimer = false },
            onStart = { label, seconds ->
                startTimer(label, seconds, slug, recipeName)
                showCustomTimer = false
            },
        )
    }

    // Recipe photo: take one with the camera or pick one, shrink it, upload it to Mealie.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var uploading by remember { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    fun upload(uri: Uri) {
        scope.launch {
            uploading = true
            val result = runCatching {
                val version = repo.uploadRecipeImage(slug, preparePhoto(context, uri))
                entity?.id?.let { ImageVersions.bump(it, version) }
            }
            uploading = false
            snackbar.showSnackbar(result.fold({ "Photo saved to Mealie" }, ::photoError))
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) upload(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) upload(uri)
    }

    // Leaving cook mode asks whether to log the recipe as cooked (Mealie's "last made").
    var askCooked by rememberSaveable { mutableStateOf(false) }
    if (askCooked) {
        AlertDialog(
            onDismissRequest = { askCooked = false },
            title = { Text("Mark as cooked?") },
            text = { Text("Save today as the last time you made $recipeName.") },
            confirmButton = {
                TextButton(onClick = {
                    askCooked = false
                    scope.launch {
                        repo.markCooked(slug)
                        SyncScheduler.requestSync(context)
                        snackbar.showSnackbar("Marked as cooked today")
                    }
                }) { Text("Yes, I made it") }
            },
            dismissButton = { TextButton(onClick = { askCooked = false }) { Text("Not now") } },
        )
    }

    // Cooking notes: the mic button in cook mode takes a spoken note, tagged with the step
    // on screen. They stay on the phone until reviewed on leaving cook mode, then go to the
    // recipe's timeline in Mealie for an assistant to read.
    val noteId = detail?.id ?: entity?.id
    val cookNotes by remember(noteId) {
        noteId?.let { container.settings.cookNotes(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList<CookNote>())
    }.collectAsStateWithLifecycle(emptyList())
    fun changeNotes(change: (List<CookNote>) -> List<CookNote>) {
        val id = noteId ?: return
        scope.launch { container.settings.updateCookNotes(id, change) }
    }
    var noteStep by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteAt by rememberSaveable { mutableStateOf(0L) }
    var typingNote by rememberSaveable { mutableStateOf(false) }
    var editingNoteAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var reviewingNotes by rememberSaveable { mutableStateOf(false) }
    var markCookedToo by rememberSaveable { mutableStateOf(true) }
    // Deleting the last note closes the review; a later note shouldn't reopen it.
    LaunchedEffect(cookNotes.isEmpty()) { if (cookNotes.isEmpty()) reviewingNotes = false }
    fun addNote(text: String) {
        val step = noteStep
        val s = step?.let { detail?.recipeInstructions?.getOrNull(it) }
        val label = s?.let { CookNotes.stepLabel(it.summary?.takeIf { t -> t.isNotBlank() } ?: it.title, it.text) }
        val note = CookNote(at = noteAt, text = text, step = step?.plus(1), stepLabel = label)
        changeNotes { it + note }
        if (!reviewingNotes) scope.launch { snackbar.showSnackbar("Note added") }
    }
    val dictate = rememberDictation(
        prompt = { noteStep?.let { "Note for step ${it + 1}" } ?: "Cooking note" },
        onText = ::addNote,
    )
    fun startNote(step: Int?) {
        noteStep = step
        noteAt = System.currentTimeMillis()
        if (!dictate()) typingNote = true
    }
    if (typingNote) {
        CookNoteDialog(
            title = noteStep?.let { "Note for step ${it + 1}" } ?: "Cooking note",
            initial = "",
            onDismiss = { typingNote = false },
            onSave = { typingNote = false; addNote(it) },
        )
    }
    cookNotes.firstOrNull { it.at == editingNoteAt }?.let { editing ->
        CookNoteDialog(
            title = "Edit note",
            initial = editing.text,
            onDismiss = { editingNoteAt = null },
            onSave = { text ->
                editingNoteAt = null
                changeNotes { notes -> notes.map { if (it.at == editing.at) it.copy(text = text) else it } }
            },
        )
    }
    if (reviewingNotes && noteId != null && cookNotes.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { reviewingNotes = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CookNotesReviewContent(
                recipeName = recipeName,
                notes = cookNotes,
                clock = { clockText(it) },
                markCooked = markCookedToo,
                onMarkCooked = { markCookedToo = it },
                onEdit = { editingNoteAt = it.at },
                onDelete = { gone -> changeNotes { notes -> notes.filterNot { it.at == gone.at } } },
                onAdd = {
                    noteStep = null
                    noteAt = System.currentTimeMillis()
                    typingNote = true
                },
                onKeep = { reviewingNotes = false },
                onSave = {
                    reviewingNotes = false
                    val message = CookNotes.message(cookNotes) { clockText(it) }
                    val cooked = markCookedToo
                    scope.launch {
                        repo.saveCookNotes(noteId, message)
                        if (cooked) repo.markCooked(slug)
                        container.settings.updateCookNotes(noteId) { emptyList() }
                        SyncScheduler.requestSync(context)
                        snackbar.showSnackbar(if (cooked) "Notes saved and marked as cooked" else "Notes saved to Mealie")
                    }
                },
            )
        }
    }

    // Chapter and tags editor, in a bottom sheet.
    var editingTags by rememberSaveable { mutableStateOf(false) }
    val library by remember { repo.recipes() }.collectAsStateWithLifecycle(emptyList())
    if (editingTags && entity != null) {
        var selection by remember(entity.id) { mutableStateOf(Cookbook.selectionOf(entity)) }
        ModalBottomSheet(onDismissRequest = { editingTags = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            TagEditorContent(
                selection = selection,
                chapters = remember(library) { Cookbook.chapterChoices(library) },
                tags = remember(library) { Cookbook.tagChoices(library) },
                onChange = { selection = it },
                onCancel = { editingTags = false },
                onSave = {
                    editingTags = false
                    scope.launch {
                        repo.setOrganizers(slug, Cookbook.categoriesFor(selection), Cookbook.tagsFor(selection))
                        SyncScheduler.requestSync(context)
                        snackbar.showSnackbar("Tags saved")
                    }
                },
            )
        }
    }

    // "Add a side": pick sides, then cook them together with this recipe on one timeline.
    var pickingSides by rememberSaveable { mutableStateOf(false) }
    if (pickingSides) {
        ModalBottomSheet(onDismissRequest = { pickingSides = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SidePickerContent(
                recipes = library.filter { it.slug != slug },
                onCancel = { pickingSides = false },
                onCook = { sides ->
                    pickingSides = false
                    onCookMeal(MealRequest("$recipeName meal", listOf(slug) + sides, LocalDate.now(), null))
                },
            )
        }
    }

    // Hand edits: the whole recipe in a full-screen editor.
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<RecipeDraft?>(null) }
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val editing = draft
    if (editing != null && detail != null) {
        val dirty = editing.changesFrom(detail) != null
        val cancel: () -> Unit = { if (dirty) { askDiscard = true } else { draft = null } }
        BackHandler(onBack = cancel)
        if (askDiscard) {
            AlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text("Discard changes?") },
                text = { Text("Your edits to $recipeName haven't been saved.") },
                confirmButton = { TextButton(onClick = { askDiscard = false; draft = null }) { Text("Discard") } },
                dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } },
            )
        }
        RecipeEditorContent(
            draft = editing,
            onChange = { draft = it },
            onCancel = cancel,
            onSave = {
                draft = null
                scope.launch {
                    if (repo.saveRecipe(slug, detail, editing)) {
                        SyncScheduler.requestSync(context)
                        snackbar.showSnackbar("Recipe saved")
                    }
                }
            },
        )
        return
    }

    // Add to grocery list: pick a list, servings, and what you already have.
    var addingToList by rememberSaveable { mutableStateOf(false) }
    val lists by remember { repo.shoppingLists() }.collectAsStateWithLifecycle(emptyList())
    if (addingToList && detail != null) {
        ModalBottomSheet(onDismissRequest = { addingToList = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AddToListContent(
                recipeName = detail.name,
                lines = detail.recipeIngredient.withIndex()
                    .filterNot { app.yeschef.data.isHeadingOnly(it.value) }
                    .map { it.index to ingredientText(it.value) },
                baseServings = baseServings(detail.recipeServings, detail.recipeYield),
                lists = lists.map { it.id to it.name },
                initialListId = null,
                onCreateList = { name -> repo.createList(name) },
                onCancel = { addingToList = false },
                onAdd = { listId, scale, skip ->
                    addingToList = false
                    val listName = lists.firstOrNull { it.id == listId }?.name ?: "the list"
                    scope.launch {
                        repo.addRecipeToList(listId, detail, scale, skip)
                        SyncScheduler.requestSync(context)
                        snackbar.showSnackbar("Added to $listName")
                    }
                },
            )
        }
    }

    RecipeDetailContent(
        title = recipeName,
        onEdit = if (detail != null) ({ draft = RecipeDraft.of(detail) }) else null,
        onAddToList = if (detail != null) ({ addingToList = true }) else null,
        onCookWithSides = if (detail != null) ({ pickingSides = true }) else null,
        detail = detail,
        notLoadedMessage = if (entity == null) "Recipe not found." else "This recipe hasn't been downloaded yet. Connect the VPN to load it.",
        serverUrl = config?.serverUrl,
        cookMode = cookMode,
        onToggleCookMode = {
            if (cookMode && detail != null) {
                if (cookNotes.isNotEmpty()) reviewingNotes = true else askCooked = true
            }
            cookMode = !cookMode
        },
        tagLine = entity?.let { Cookbook.names(it.categories) + Cookbook.names(it.tags) }.orEmpty(),
        onEditTags = if (entity != null) ({ editingTags = true }) else null,
        lastCooked = Cookbook.lastCookedText(entity?.lastMade ?: detail?.lastMade, LocalDate.now()),
        onBack = onBack,
        onCustomTimer = { showCustomTimer = true },
        onStartTimer = { label, seconds -> startTimer(label, seconds, slug, recipeName) },
        photoUploading = uploading,
        onTakePhoto = {
            val uri = newCameraUri(context)
            cameraUri = uri
            takePhoto.launch(uri)
        },
        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        noteCount = cookNotes.size,
        onAddNote = if (noteId != null) ({ step -> startNote(step) }) else null,
        onReviewNotes = if (noteId != null && cookNotes.isNotEmpty()) ({ reviewingNotes = true }) else null,
        snackbarHostState = snackbar,
    )
}

private fun photoError(e: Throwable): String = when (e) {
    is HttpException -> if (e.code() == 400) "Mealie couldn't read that image." else "Mealie returned HTTP ${e.code()}."
    is java.io.IOException -> "Couldn't reach Mealie. Connect the VPN and try again."
    else -> "Couldn't use that photo (${e.message ?: e.javaClass.simpleName})."
}

/** Stateless recipe page, also used for screenshots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailContent(
    title: String,
    detail: RecipeDetailDto?,
    notLoadedMessage: String,
    serverUrl: String?,
    cookMode: Boolean,
    onToggleCookMode: () -> Unit,
    onBack: (() -> Unit)?,
    lastCooked: String? = null,
    tagLine: List<String> = emptyList(),
    onEditTags: (() -> Unit)? = null,
    onCookWithSides: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAddToList: (() -> Unit)? = null,
    initialServings: Double? = null,
    onCustomTimer: () -> Unit,
    onStartTimer: (label: String, seconds: Long) -> Unit,
    photoUploading: Boolean = false,
    onTakePhoto: (() -> Unit)? = null,
    onPickPhoto: (() -> Unit)? = null,
    noteCount: Int = 0,
    onAddNote: ((step: Int?) -> Unit)? = null,
    onReviewNotes: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    val wide = isWide()
    var checkedIngredients by remember(detail?.id) { mutableStateOf(setOf<Int>()) }
    // Servings scaler: by servings when the recipe says how many it makes, else by a multiplier.
    val servingsBase = remember(detail?.id) { detail?.let { baseServings(it.recipeServings, it.recipeYield) } }
    var servings by remember(detail?.id) { mutableStateOf(initialServings ?: servingsBase ?: 1.0) }
    var factor by remember(detail?.id) { mutableStateOf(1.0) }
    val scale = servingsBase?.let { servings / it } ?: factor
    val listState = rememberLazyListState()
    val bodyStyle = if (cookMode) MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
    else MaterialTheme.typography.bodyLarge

    Scaffold(
        topBar = {
            YcTopBar(
                title = if (cookMode) title else "",
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (onEdit != null && !cookMode) {
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit recipe") }
                    }
                    if (onEditTags != null && !cookMode) {
                        IconButton(onClick = onEditTags) { Icon(Icons.AutoMirrored.Filled.Label, "Edit chapter and tags") }
                    }
                    IconButton(onClick = onCustomTimer) { Icon(Icons.Filled.Timer, "New timer") }
                    FilterChip(
                        selected = cookMode,
                        onClick = onToggleCookMode,
                        label = { Text("Cook mode") },
                        leadingIcon = {
                            Icon(if (cookMode) Icons.Filled.LightMode else Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tertiaryContainer,
                            selectedLabelColor = colors.onTertiaryContainer,
                            selectedLeadingIconColor = colors.tertiary,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (cookMode && detail != null && onAddNote != null) {
                CookNoteButton(noteCount) { onAddNote(stepInView(listState)) }
            }
        },
    ) { padding ->
        if (detail == null) {
            Text(notLoadedMessage, Modifier.padding(padding).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
            return@Scaffold
        }
        val stepIngredients = remember(detail, cookMode) { if (cookMode) stepIngredientsFor(detail) else emptyList() }
        // On the tablet the recipe keeps a readable width, centred.
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 860.dp),
            state = listState,
            // Room under the last step for the note button.
            contentPadding = PaddingValues(bottom = if (cookMode && onAddNote != null) 96.dp else 32.dp),
        ) {
            if (!cookMode) {
                item {
                    Box(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .then(if (wide) Modifier.height(300.dp) else Modifier.aspectRatio(16f / 10f))
                            .clip(MaterialTheme.shapes.extraLarge),
                    ) {
                        RecipeImage(serverUrl, detail.id, Modifier.fillMaxSize())
                        if (photoUploading) {
                            Box(Modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        if (onTakePhoto != null && onPickPhoto != null && !photoUploading) {
                            PhotoButton(
                                hasPhoto = !detail.image.isNullOrBlank(),
                                onTakePhoto = onTakePhoto,
                                onPickPhoto = onPickPhoto,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            )
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(detail.name, style = MaterialTheme.typography.headlineMedium)
                        if (tagLine.isNotEmpty()) {
                            Spacer(Modifier.size(6.dp))
                            Text(
                                tagLine.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.tertiary,
                                modifier = if (onEditTags != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onEditTags) else Modifier,
                            )
                        }
                        detail.description?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.size(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            val facts = listOfNotNull(
                detail.recipeYield?.takeIf { it.isNotBlank() }?.let { "Makes" to it },
                detail.prepTime?.takeIf { it.isNotBlank() }?.let { "Prep" to it },
                detail.performTime?.takeIf { it.isNotBlank() }?.let { "Cook" to it },
                detail.totalTime?.takeIf { it.isNotBlank() }?.let { "Total" to it },
                lastCooked?.let { "Last cooked" to it.removePrefix("Cooked ") },
            )
            if (facts.isNotEmpty()) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        facts.forEach { (label, value) -> FactPill(label, value) }
                    }
                }
            }

            if ((onCookWithSides != null || onAddToList != null || onReviewNotes != null) && !cookMode) {
                item {
                    FlowRow(Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp)) {
                        if (onReviewNotes != null) {
                            TextButton(onClick = onReviewNotes) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (noteCount == 1) "Save 1 cooking note" else "Save $noteCount cooking notes")
                            }
                        }
                        if (onAddToList != null) {
                            TextButton(onClick = onAddToList) {
                                Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add to grocery list")
                            }
                        }
                        if (onCookWithSides != null) {
                            TextButton(onClick = onCookWithSides) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add a side and cook together")
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Ingredients", Modifier.weight(1f))
                    ServingsStepper(servingsBase, servings, factor, onServings = { servings = it }, onFactor = { factor = it })
                }
            }
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colors.surfaceContainerLow,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        detail.recipeIngredient.forEachIndexed { index, ingredient ->
                            ingredient.title?.takeIf { it.isNotBlank() }?.let {
                                Text(it, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = colors.primary)
                            }
                            val done = index in checkedIngredients
                            IngredientRow(scaleIngredient(ingredientText(ingredient), scale), done, bodyStyle) {
                                checkedIngredients = if (done) checkedIngredients - index else checkedIngredients + index
                            }
                        }
                    }
                }
            }

            nutritionRows(detail.nutrition).takeIf { it.isNotEmpty() }?.let { rows ->
                item { NutritionCard(rows, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
            }

            item { SectionLabel("Method") }
            itemsIndexed(detail.recipeInstructions, key = { index, _ -> STEP_KEY + index }) { index, step ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    step.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = colors.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 8.dp))
                    }
                    Row {
                        Box(
                            Modifier.size(if (cookMode) 40.dp else 32.dp).background(colors.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).padding(top = 4.dp)) {
                            Text(step.text, style = bodyStyle)
                            stepIngredients.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { used ->
                                StepIngredients(
                                    lines = used.map { i -> i to scaleIngredient(ingredientText(detail.recipeIngredient[i]), scale) },
                                    checked = checkedIngredients,
                                    onToggle = { i -> checkedIngredients = if (i in checkedIngredients) checkedIngredients - i else checkedIngredients + i },
                                )
                            }
                            val found = remember(step.text, step.durationSeconds) { stepTimers(step.text, step.durationSeconds) }
                            if (found.isNotEmpty()) {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    found.forEach { d ->
                                        TimerChip(formatShort(d.seconds)) {
                                            onStartTimer(timerStepLabel(index + 1, step.summary?.takeIf { it.isNotBlank() } ?: step.title, step.text, d.text), d.seconds)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            detail.orgURL?.takeIf { it.isNotBlank() }?.let { url ->
                item {
                    Text("Source: $url", Modifier.padding(20.dp), style = MaterialTheme.typography.labelMedium, color = colors.outline)
                }
            }
        }
    }
}

private const val STEP_KEY = "step-"

/** The method step the cook is looking at (index from 0), or null before the method. */
private fun stepInView(state: LazyListState): Int? {
    val info = state.layoutInfo
    val steps = info.visibleItemsInfo.mapNotNull { item ->
        (item.key as? String)?.takeIf { it.startsWith(STEP_KEY) }?.removePrefix(STEP_KEY)?.toIntOrNull()
            ?.let { Triple(it, item.offset, item.size) }
    }
    return CookNotes.stepInView(steps, info.viewportStartOffset)
}

@Composable
private fun PhotoButton(hasPhoto: Boolean, onTakePhoto: () -> Unit, onPickPhoto: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { open = true },
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (hasPhoto) "Change photo" else "Add photo")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                onClick = { open = false; onTakePhoto() },
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                onClick = { open = false; onPickPhoto() },
            )
        }
    }
}

/** "− Serves 6 +" when the recipe has a serving count, otherwise "− ×1½ +". */
@Composable
private fun ServingsStepper(
    base: Double?,
    servings: Double,
    factor: Double,
    onServings: (Double) -> Unit,
    onFactor: (Double) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val changed = if (base != null) servings != base else factor != 1.0
    val label = if (base != null) {
        val n = formatAmount(servings)
        if (servings == 1.0) "Serves 1" else "Serves $n"
    } else "×" + formatAmount(factor)
    val steps = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
    Surface(shape = CircleShape, color = if (changed) colors.tertiaryContainer else colors.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (base != null) onServings((servings - 1).coerceAtLeast(1.0))
                    else onFactor(steps.lastOrNull { it < factor } ?: factor)
                },
                modifier = Modifier.size(36.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer servings", modifier = Modifier.size(18.dp)) }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (changed) colors.onTertiaryContainer else colors.onSurface,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            IconButton(
                onClick = {
                    if (base != null) onServings(servings + 1)
                    else onFactor(steps.firstOrNull { it > factor } ?: factor)
                },
                modifier = Modifier.size(36.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "More servings", modifier = Modifier.size(18.dp)) }
        }
    }
}

private val NUTRIENTS = listOf(
    "calories" to "Calories", "proteinContent" to "Protein", "carbohydrateContent" to "Carbs",
    "fatContent" to "Fat", "saturatedFatContent" to "Saturated fat", "fiberContent" to "Fibre",
    "sugarContent" to "Sugar", "sodiumContent" to "Sodium", "cholesterolContent" to "Cholesterol",
)

/** Mealie's nutrition values, labelled, with a unit added when the value is a bare number. */
private fun nutritionRows(nutrition: Map<String, kotlinx.serialization.json.JsonElement?>?): List<Pair<String, String>> {
    if (nutrition == null) return emptyList()
    return NUTRIENTS.mapNotNull { (key, label) ->
        val raw = (nutrition[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()
        if (raw.isNullOrEmpty() || raw == "null" || raw == "0") return@mapNotNull null
        val unit = when (key) { "calories" -> " kcal"; "sodiumContent", "cholesterolContent" -> " mg"; else -> " g" }
        label to (if (raw.toDoubleOrNull() != null) raw + unit else raw)
    }
}

@Composable
private fun NutritionCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val headline = rows.firstOrNull { it.first == "Calories" }?.second
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Nutrition", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(headline, "per serving").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Hide nutrition" else "Show nutrition", tint = colors.outline)
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    rows.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            pair.forEach { (label, value) ->
                                Row(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 16.dp))
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** In cook mode, the ingredients a step uses, shown right under it with their amounts. */
@Composable
internal fun StepIngredients(lines: List<Pair<Int, String>>, checked: Set<Int>, onToggle: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.tertiaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                "YOU'LL NEED",
                Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.tertiary,
            )
            lines.forEach { (index, text) ->
                val done = index in checked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onToggle(index) }.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (done) colors.tertiary else colors.surface)
                            .then(if (done) Modifier else Modifier.border(1.5.dp, colors.tertiary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) colors.outline else colors.onSurface,
                    )
                }
            }
        }
    }
}

internal fun stepIngredientsFor(detail: RecipeDetailDto): List<List<Int>> = ingredientsForSteps(
    detail.recipeIngredient.map { i ->
        val text = ingredientText(i)
        IngredientLine(i.referenceId, i.food?.name, text, isHeadingOnly = text.isBlank() && i.food == null)
    },
    detail.recipeInstructions.map { st -> StepLine(st.text, st.ingredientReferences.mapNotNull { it.referenceId }) },
)

@Composable
private fun FactPill(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.tertiaryContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onTertiaryContainer)
        }
    }
}

@Composable
private fun IngredientRow(text: String, done: Boolean, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(if (done) colors.primary else colors.surface)
                .then(if (done) Modifier else Modifier.border(1.5.dp, colors.outline, RoundedCornerShape(7.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = style,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) colors.outline else colors.onSurface,
        )
    }
}

/** Keeps an open editor's draft across rotation. */
private val DraftSaver = androidx.compose.runtime.saveable.Saver<RecipeDraft?, String>(
    save = { d -> d?.let { MealieJson.encodeToString(RecipeDraft.serializer(), it) } ?: "" },
    restore = { s -> if (s.isEmpty()) null else runCatching { MealieJson.decodeFromString(RecipeDraft.serializer(), s) }.getOrNull() },
)

internal fun ingredientText(i: IngredientDto): String = app.yeschef.data.ingredientLineText(i)

/** Times mentioned in the step text, plus the step's own duration from Mealie if it isn't already among them. */
internal fun stepTimers(text: String, durationSeconds: Int?): List<DetectedDuration> {
    val found = detectDurations(text)
    val fromMealie = durationSeconds?.toLong()?.takeIf { s -> s > 0 && found.none { it.seconds == s } }
    return if (fromMealie != null) listOf(DetectedDuration(fromMealie, "")) + found else found
}
