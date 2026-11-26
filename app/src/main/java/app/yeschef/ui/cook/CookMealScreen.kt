package app.yeschef.ui.cook

import app.yeschef.data.cook.CookNote
import app.yeschef.data.cook.CookNotes
import app.yeschef.ui.recipes.CookNoteButton
import app.yeschef.ui.recipes.CookNoteDialog
import app.yeschef.ui.recipes.MealCookNotesReviewContent
import app.yeschef.ui.recipes.rememberDictation
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextDecoration
import app.yeschef.data.isHeadingOnly
import app.yeschef.ui.recipes.ingredientText
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.cook.CookTimer
import app.yeschef.data.cook.MealPrepStep
import app.yeschef.data.cook.MealRecipeIn
import app.yeschef.data.cook.MealSession
import app.yeschef.data.cook.MealStepIn
import app.yeschef.data.cook.MealTimeline
import app.yeschef.data.cook.MealTimelineStep
import app.yeschef.data.cook.baseServings
import app.yeschef.data.cook.buildMealTimeline
import app.yeschef.data.cook.firstSteps
import app.yeschef.data.cook.formatAmount
import app.yeschef.data.cook.formatClock
import app.yeschef.data.cook.formatShort
import app.yeschef.data.cook.planReminders
import app.yeschef.data.cook.scaleIngredient
import app.yeschef.data.cook.timerStepLabel
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.sync.SyncScheduler
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.isWide
import app.yeschef.ui.recipes.StepIngredients
import app.yeschef.ui.recipes.stepIngredientRows
import app.yeschef.ui.recipes.stepIngredientsFor
import app.yeschef.ui.recipes.stepTimers
import app.yeschef.ui.theme.OverlineStyle
import app.yeschef.ui.theme.RecipeTagColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What to cook together: a meal from the plan, or a recipe and the sides picked for it. */
data class MealRequest(val title: String, val slugs: List<String>, val date: LocalDate, val entryType: String?) {
    fun encode(): String = listOf(title, date.toString(), entryType.orEmpty(), slugs.joinToString(",")).joinToString(SEP)

    companion object {
        private const val SEP = "\u0001"
        fun decode(s: String): MealRequest? = s.split(SEP).takeIf { it.size == 4 }?.let { (t, d, e, slugs) ->
            MealRequest(t, slugs.split(',').filter { it.isNotBlank() }, LocalDate.parse(d), e.ifBlank { null })
        }
    }
}

/** One recipe in the meal, as the screen shows it. */
data class MealRecipeView(val slug: String, val name: String, val detail: RecipeDetailDto?, val color: Color)

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
fun clockText(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(clockFormat)

/** Mealie-style meal names to a usual time to eat. */
private fun usualTime(entryType: String?): LocalTime = when (entryType?.lowercase()) {
    "breakfast" -> LocalTime.of(8, 30)
    "lunch" -> LocalTime.of(12, 30)
    else -> LocalTime.of(18, 30)
}

/** The planned meal's usual time on its day, or the soonest it can be ready if that has passed. */
fun defaultReadyAt(date: LocalDate, entryType: String?, leadSeconds: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val usual = ZonedDateTime.of(date, usualTime(entryType), zone).toInstant().toEpochMilli()
    return maxOf(usual, earliestReadyAt(leadSeconds, now))
}

/** Rounds a timestamp to the nearest 5-minute mark so the timeline avoids false minute precision. */
fun roundToFiveMin(epochMs: Long): Long {
    val five = 5 * 60_000L
    return ((epochMs + five / 2) / five) * five
}

/** Now plus the meal's lead time, rounded up to the next five minutes. */
fun earliestReadyAt(leadSeconds: Long, now: Long): Long {
    val five = 5 * 60_000L
    val t = now + leadSeconds * 1000
    return (t + five - 1) / five * five
}

fun mealInputs(recipes: List<MealRecipeView>): List<MealRecipeIn> = recipes.map { r ->
    val d = r.detail
    MealRecipeIn(
        r.name, d?.prepTime, d?.performTime, d?.totalTime,
        d?.recipeInstructions.orEmpty().map { MealStepIn(it.text, it.durationSeconds) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookMealScreen(container: AppContainer, request: MealRequest, onClose: () -> Unit) {
    val repo = container.repository
    val reminders = container.mealReminders
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loaded by remember(request.slugs) {
        combine(request.slugs.map { repo.recipe(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(request.slugs.map { null to null })
    LaunchedEffect(request.slugs) { request.slugs.forEach { repo.refreshRecipe(it) } }

    val recipes = remember(loaded) {
        loaded.mapIndexed { i, (entity, detail) ->
            MealRecipeView(request.slugs[i], detail?.name ?: entity?.name ?: request.slugs[i], detail, RecipeTagColors[i % RecipeTagColors.size])
        }
    }
    val inputs = remember(recipes) { mealInputs(recipes.filter { it.detail != null }) }
    val cookable = remember(recipes) { recipes.filter { it.detail != null } }
    val timeline = remember(inputs) { buildMealTimeline(inputs) }

    val session by reminders.session.collectAsStateWithLifecycle()
    val activeTimers by container.timers.timers.collectAsStateWithLifecycle()
    val started = session?.slugs == request.slugs
    var scales by remember(request.encode()) { mutableStateOf(mapOf<String, Double>()) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    var chosenReadyAt by rememberSaveable(request.encode()) { mutableStateOf<Long?>(null) }
    var startNowMode by rememberSaveable(request.encode()) { mutableStateOf(false) }
    val paused = started && session?.pausedAt != null
    // While paused the ready time keeps moving back, so the timeline stays where it stopped.
    val readyAt = when {
        started -> session?.readyAtNow(now) ?: defaultReadyAt(request.date, request.entryType, timeline.leadSeconds, now)
        startNowMode -> earliestReadyAt(timeline.leadSeconds, now)
        chosenReadyAt != null -> chosenReadyAt!!
        else -> defaultReadyAt(request.date, request.entryType, timeline.leadSeconds, now)
    }

    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun begin(at: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val plan = planReminders(timeline, inputs, at, System.currentTimeMillis(), { clockText(it) })
        reminders.start(MealSession(request.title, request.slugs, at, plan))
    }

    var keepOn by rememberSaveable { mutableStateOf(true) }
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        ReadyTimeDialog(readyAt, onDismiss = { picking = false }) { at ->
            picking = false
            startNowMode = false
            chosenReadyAt = at
            if (started) begin(at) // re-plan the reminders for the new time
        }
    }
    // Cooking notes, as in single-recipe cook mode: kept on the phone per recipe, then
    // saved to each recipe's timeline in Mealie when the meal is stopped.
    val noteIds = remember(cookable) { cookable.mapNotNull { it.detail?.id } }
    val notesByRecipe by remember(noteIds) {
        if (noteIds.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyMap<String, List<CookNote>>())
        } else {
            combine(noteIds.map { id -> container.settings.cookNotes(id) }) { lists -> noteIds.zip(lists.toList()).toMap() }
        }
    }.collectAsStateWithLifecycle(emptyMap())
    val noteCount = notesByRecipe.values.sumOf { it.size }
    val snackbar = remember { SnackbarHostState() }
    var noteFor by remember { mutableStateOf<MealNoteTarget?>(null) }
    var choosingNote by rememberSaveable { mutableStateOf(false) }
    var typingNote by rememberSaveable { mutableStateOf(false) }
    var editingMealNote by remember { mutableStateOf<Pair<String, CookNote>?>(null) }
    var reviewingNotes by rememberSaveable { mutableStateOf(false) }
    var markCookedOnReview by rememberSaveable { mutableStateOf(true) }
    fun addNote(text: String) {
        val target = noteFor ?: return
        val step = target.step?.let { target.recipe.detail?.recipeInstructions?.getOrNull(it) }
        val label = step?.let { CookNotes.stepLabel(it.summary?.takeIf { t -> t.isNotBlank() } ?: it.title, it.text) }
        val note = CookNote(at = System.currentTimeMillis(), text = text, step = target.step?.plus(1), stepLabel = label)
        val id = target.recipe.detail?.id ?: return
        scope.launch {
            container.settings.updateCookNotes(id) { it + note }
            val res = snackbar.showSnackbar(
                message = "Saved: \"${CookNotes.snippet(text)}\"",
                actionLabel = "Edit",
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) editingMealNote = id to note
        }
    }
    val dictate = rememberDictation(
        prompt = { noteFor?.let { t -> t.recipe.name + (t.step?.let { ", step ${it + 1}" } ?: "") } ?: "Cooking note" },
        onText = ::addNote,
        onFallbackToType = { typingNote = true },
    )
    fun startNote(target: MealNoteTarget) {
        noteFor = target
        if (!dictate()) typingNote = true
    }
    if (typingNote && noteFor != null) {
        CookNoteDialog(
            title = noteFor!!.recipe.name + (noteFor!!.step?.let { ", step ${it + 1}" } ?: ""),
            initial = "",
            onDismiss = { typingNote = false },
            onSave = { typingNote = false; addNote(it) },
        )
    }
    editingMealNote?.let { (recipeId, editing) ->
        CookNoteDialog(
            title = "Edit note",
            initial = editing.text,
            onDismiss = { editingMealNote = null },
            onSave = { text ->
                editingMealNote = null
                scope.launch {
                    container.settings.updateCookNotes(recipeId) { list ->
                        list.map { if (it.at == editing.at) it.copy(text = text) else it }
                    }
                }
            },
        )
    }
    if (choosingNote) {
        val targets = remember(timeline, readyAt, now, started) { noteTargets(cookable, timeline, readyAt, now, started) }
        AlertDialog(
            onDismissRequest = { choosingNote = false },
            title = { Text("Note for") },
            text = {
                Column {
                    targets.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choosingNote = false; startNote(t) }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).background(t.recipe.color, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(t.recipe.name + (t.step?.let { ", step ${it + 1}" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingNote = false }) { Text("Cancel") } },
        )
    }
    // Save each recipe's notes to Mealie (with scale & co-cooked dishes as context) and clear them from the phone.
    fun saveNotes() {
        val pending = notesByRecipe.filterValues { it.isNotEmpty() }
        if (pending.isEmpty()) return
        val recipesById = cookable.mapNotNull { r -> r.detail?.id?.let { it to r } }.toMap()
        scope.launch {
            pending.forEach { (id, notes) ->
                val self = recipesById[id]
                val scale = self?.let { scales[it.slug] } ?: 1.0
                val base = self?.detail?.let { baseServings(it.recipeServings, it.recipeYield) }
                val others = recipesById.filterKeys { it != id }.values.map { it.name }
                val contextLines = listOfNotNull(
                    CookNotes.scaleContext(scale, base),
                    CookNotes.mealContext(others),
                )
                repo.saveCookNotes(id, CookNotes.message(notes, { clockText(it) }, contextLines))
                container.settings.updateCookNotes(id) { emptyList() }
            }
            SyncScheduler.requestSync(context)
        }
    }

    var askEnd by rememberSaveable { mutableStateOf(false) }
    if (reviewingNotes && noteCount > 0) {
        val groups = cookable.mapNotNull { r ->
            val id = r.detail?.id ?: return@mapNotNull null
            Triple(id, r.name, notesByRecipe[id].orEmpty())
        }
        ModalBottomSheet(onDismissRequest = { reviewingNotes = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            MealCookNotesReviewContent(
                groups = groups,
                clock = { clockText(it) },
                markCooked = markCookedOnReview,
                onMarkCooked = { markCookedOnReview = it },
                onEdit = { id, note -> editingMealNote = id to note },
                onDelete = { id, gone ->
                    scope.launch {
                        container.settings.updateCookNotes(id) { list -> list.filterNot { it.at == gone.at } }
                    }
                },
                onKeep = {
                    reviewingNotes = false
                    reminders.end()
                    onClose()
                },
                onSave = {
                    reviewingNotes = false
                    saveNotes()
                    if (markCookedOnReview) {
                        scope.launch {
                            request.slugs.forEach { repo.markCooked(it) }
                            SyncScheduler.requestSync(context)
                        }
                    }
                    reminders.end()
                    onClose()
                },
            )
        }
    }
    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Stop this meal?") },
            text = {
                Text("Reminders for it stop. Mark every recipe in it as cooked today?")
            },
            confirmButton = {
                TextButton(onClick = {
                    askEnd = false
                    scope.launch {
                        request.slugs.forEach { repo.markCooked(it) }
                        SyncScheduler.requestSync(context)
                    }
                    reminders.end()
                    onClose()
                }) { Text("Yes, we ate") }
            },
            dismissButton = {
                TextButton(onClick = { askEnd = false; reminders.end(); onClose() }) { Text("Just stop") }
            },
        )
    }

    // The "Let's cook!" scene, once when the meal opens (not again on rotation).
    var intro by rememberSaveable(request.encode()) { mutableStateOf(true) }
    if (intro) CookIntroOverlay(onDone = { intro = false })

    val kitchenTools = rememberKitchenToolsState()
    KitchenToolsSheet(kitchenTools)

    val startTimer = rememberTimerStarter(container.timers)
    BackHandler(onBack = onClose)
    CookMealContent(
        title = request.title,
        recipes = recipes,
        cookable = cookable,
        timeline = timeline,
        readyAt = readyAt,
        now = now,
        started = started,
        nextReminder = if (started && !paused) session?.reminders?.firstOrNull { it.at > now }?.let { it.title } else null,
        paused = paused,
        onPause = { reminders.pause() },
        onResume = { reminders.resume() },
        onShift = { deltaMs ->
            if (started) reminders.shift(deltaMs) else chosenReadyAt = readyAt + deltaMs
        },
        keepScreenOn = keepOn,
        onToggleScreen = { keepOn = !keepOn },
        startNowMode = startNowMode,
        onStartNowMode = { nowMode ->
            startNowMode = nowMode
            if (nowMode) chosenReadyAt = null
        },
        onPickTime = { picking = true },
        onUseEarliest = {
            startNowMode = true
            chosenReadyAt = null
        },
        onStart = {
            val target = if (startNowMode) earliestReadyAt(timeline.leadSeconds, System.currentTimeMillis()) else readyAt
            begin(target)
        },
        onFinish = {
            if (noteCount > 0) {
                markCookedOnReview = true
                reviewingNotes = true
            } else {
                askEnd = true
            }
        },
        onBack = onClose,
        onStartTimer = { recipe, label, seconds -> startTimer(label, seconds, recipe.slug, recipe.name) },
        onKitchenTools = { kitchenTools.open = true },
        scales = scales,
        onScaleChange = { slug, s -> scales = scales + (slug to s) },
        activeTimers = activeTimers,
        noteCount = noteCount,
        onAddNote = if (noteIds.isNotEmpty()) ({
            val targets = noteTargets(cookable, timeline, readyAt, now, started)
            if (targets.size == 1) {
                startNote(targets.single())
            } else {
                choosingNote = true
            }
        }) else null,
        snackbarHostState = snackbar,
    )
}

/** What a cooking note is about: a dish, and the step of it when there is one. */
data class MealNoteTarget(val recipe: MealRecipeView, val step: Int?)

/** The choices for a new note: the steps happening now (or first, before starting), then each dish. */
fun noteTargets(cookable: List<MealRecipeView>, timeline: MealTimeline, readyAt: Long, now: Long, started: Boolean): List<MealNoteTarget> {
    val slots = timeline.steps.groupBy { roundToFiveMin(readyAt - it.startsBefore * 1000) }.toList()
    val slot = (if (started) slots.lastOrNull { it.first <= now } else null) ?: slots.firstOrNull()
    val steps = slot?.second.orEmpty().mapNotNull { s -> cookable.getOrNull(s.recipeIndex)?.let { MealNoteTarget(it, s.stepIndex) } }
    return steps + cookable.map { MealNoteTarget(it, null) }
}

/** Stateless meal cook mode, also used for screenshots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookMealContent(
    title: String,
    recipes: List<MealRecipeView>,
    cookable: List<MealRecipeView>,
    timeline: MealTimeline,
    readyAt: Long,
    now: Long,
    started: Boolean,
    nextReminder: String?,
    paused: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onShift: ((Long) -> Unit)? = null,
    keepScreenOn: Boolean,
    onToggleScreen: () -> Unit,
    startNowMode: Boolean = false,
    onStartNowMode: ((Boolean) -> Unit)? = null,
    onPickTime: () -> Unit,
    onUseEarliest: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onStartTimer: (MealRecipeView, String, Long) -> Unit,
    onKitchenTools: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    openDishes: Set<String> = emptySet(),
    scales: Map<String, Double> = emptyMap(),
    onScaleChange: ((slug: String, scale: Double) -> Unit)? = null,
    activeTimers: List<CookTimer> = emptyList(),
    noteCount: Int = 0,
    onAddNote: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    val wide = isWide()
    val startAt = readyAt - timeline.leadSeconds * 1000
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    // Each dish's ingredient list opens under its name. Ticks are shared with prep and step cards.
    var open by rememberSaveable { mutableStateOf(openDishes.toList()) }
    var checked by remember { mutableStateOf(mapOf<String, Set<Int>>()) }
    var doneSteps by remember { mutableStateOf(setOf<String>()) }
    var filterSlug by rememberSaveable { mutableStateOf<String?>(null) }
    var prepOpen by rememberSaveable { mutableStateOf(true) }
    fun toggle(slug: String, i: Int) {
        val ticked = checked[slug].orEmpty()
        checked = checked + (slug to if (i in ticked) ticked - i else ticked + i)
    }
    fun toggleStep(slug: String, stepIndex: Int) {
        val key = "$slug:$stepIndex"
        doneSteps = if (key in doneSteps) doneSteps - key else doneSteps + key
    }
    val visiblePrep = remember(timeline, cookable, filterSlug) {
        if (filterSlug == null) timeline.prepSteps
        else timeline.prepSteps.filter { cookable.getOrNull(it.recipeIndex)?.slug == filterSlug }
    }
    val showPrepSection = visiblePrep.isNotEmpty() || timeline.ovenRequirements.isNotEmpty() || timeline.boilWaterDishes.isNotEmpty()
    // Merged timed steps, optionally filtered to one dish, rounded to 5-minute marks.
    val visibleSteps = remember(timeline, cookable, filterSlug) {
        if (filterSlug == null) timeline.steps
        else timeline.steps.filter { cookable.getOrNull(it.recipeIndex)?.slug == filterSlug }
    }
    val slots = remember(visibleSteps, readyAt) {
        visibleSteps.groupBy { roundToFiveMin(readyAt - it.startsBefore * 1000) }.toList()
    }
    // The slot in progress: the latest one that has started.
    val current = if (started) slots.lastOrNull { it.first <= now }?.first else null
    val currentKey = current?.let { "t-$it" }
    val showJumpToNow by remember(currentKey, listState) {
        derivedStateOf {
            if (currentKey == null) false
            else {
                val info = listState.layoutInfo.visibleItemsInfo
                info.isNotEmpty() && info.none { it.key == currentKey }
            }
        }
    }

    fun LazyListScope.scheduleAndDishesItems() {
        item(key = "ready-card") {
            ReadyCard(
                readyAt = readyAt, startAt = startAt, leadSeconds = timeline.leadSeconds,
                now = now, started = started, nextReminder = nextReminder,
                zone = zone, startNowMode = startNowMode, onStartNowMode = onStartNowMode,
                onPickTime = onPickTime, onUseEarliest = onUseEarliest, onStart = onStart, onFinish = onFinish,
                paused = paused, onPause = onPause, onResume = onResume, onShift = onShift,
                canStart = cookable.isNotEmpty(),
            )
        }
        item(key = "dishes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recipes.forEach { r ->
                    val plan = cookable.indexOf(r).takeIf { it >= 0 }?.let { timeline.recipes.getOrNull(it) }
                    val isOpen = r.slug in open
                    val dishScale = scales[r.slug] ?: 1.0
                    RecipeLine(
                        r = r,
                        startAt = plan?.takeIf { it.totalSeconds > 0 }?.let { readyAt - it.startsBefore * 1000 },
                        totalSeconds = plan?.totalSeconds,
                        prepSeconds = plan?.prepSeconds,
                        timesKnown = plan?.timesKnown,
                        zone = zone,
                        open = isOpen,
                        onToggle = if (r.detail != null) ({ open = if (isOpen) open - r.slug else open + r.slug }) else null,
                    )
                    if (isOpen) {
                        DishIngredients(
                            recipe = r,
                            checked = checked[r.slug].orEmpty(),
                            scale = dishScale,
                            onScaleChange = onScaleChange?.let { cb -> { s -> cb(r.slug, s) } },
                            onToggle = { toggle(r.slug, it) },
                        )
                    }
                }
            }
        }
    }

    fun LazyListScope.prepAndTimelineItems() {
        if (cookable.size >= 2) {
            item(key = "dish-filter") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterSlug == null,
                        onClick = { filterSlug = null },
                        label = { Text("All dishes") },
                    )
                    cookable.forEach { r ->
                        FilterChip(
                            selected = filterSlug == r.slug,
                            onClick = { filterSlug = if (filterSlug == r.slug) null else r.slug },
                            label = { Text(r.name, maxLines = 1) },
                            leadingIcon = { Box(Modifier.size(8.dp).background(r.color, CircleShape)) },
                        )
                    }
                }
            }
        }
        if (showPrepSection) {
            item(key = "prep-header") {
                val donePrep = visiblePrep.count { p ->
                    val slug = cookable.getOrNull(p.recipeIndex)?.slug ?: return@count false
                    "$slug:${p.stepIndex}" in doneSteps
                }
                Row(
                    Modifier.fillMaxWidth().clickable { prepOpen = !prepOpen }.padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("PREP & SETUP", style = OverlineStyle, color = colors.tertiary)
                        Text(
                            when {
                                visiblePrep.isEmpty() -> "Before you start · no clock"
                                donePrep == visiblePrep.size -> "All ${visiblePrep.size} prep steps done"
                                donePrep > 0 -> "$donePrep of ${visiblePrep.size} done · untimed"
                                else -> "Do at your own pace before starting the clock"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (prepOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (prepOpen) "Collapse prep" else "Expand prep",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            if (prepOpen) {
                if (timeline.ovenRequirements.isNotEmpty() || timeline.boilWaterDishes.isNotEmpty()) {
                    item(key = "setup-cues") {
                        SetupCuesCard(timeline)
                    }
                }
                visiblePrep.forEach { p ->
                    val r = cookable.getOrNull(p.recipeIndex) ?: return@forEach
                    item(key = "prep-${r.slug}-${p.stepIndex}") {
                        val stepDone = "${r.slug}:${p.stepIndex}" in doneSteps
                        PrepStepCard(
                            recipe = r,
                            stepIndex = p.stepIndex,
                            checked = checked[r.slug].orEmpty(),
                            scale = scales[r.slug] ?: 1.0,
                            done = stepDone,
                            onToggleDone = { toggleStep(r.slug, p.stepIndex) },
                            onToggle = { toggle(r.slug, it) },
                        )
                    }
                }
            }
        }
        if (slots.isNotEmpty()) {
            item(key = "timeline-header") { SectionLabel("Timed cooking", Modifier.padding(top = 4.dp)) }
        }
        slots.forEach { (at, steps) ->
            item(key = "t-$at") {
                val past = current != null && at < current
                val allDone = steps.all { s -> "${cookable[s.recipeIndex].slug}:${s.stepIndex}" in doneSteps }
                val slotDimmed = allDone || (past && doneSteps.isEmpty())
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (slotDimmed) 0.55f else 1f)) {
                    Column(Modifier.width(88.dp).padding(top = 10.dp, end = 6.dp)) {
                        Text(
                            "~${clockText(at, zone)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (at == current) colors.primary else colors.onSurface,
                        )
                        if (at == current) Text("NOW", style = OverlineStyle, color = colors.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEach { s ->
                            val r = cookable[s.recipeIndex]
                            val stepDone = "${r.slug}:${s.stepIndex}" in doneSteps
                            StepCard(
                                recipe = r,
                                stepIndex = s.stepIndex,
                                timelineStep = s,
                                checked = checked[r.slug].orEmpty(),
                                scale = scales[r.slug] ?: 1.0,
                                done = stepDone,
                                onToggleDone = { toggleStep(r.slug, s.stepIndex) },
                                onToggle = { toggle(r.slug, it) },
                                activeTimers = activeTimers,
                                now = now,
                                onStartTimer = onStartTimer,
                            )
                        }
                    }
                }
            }
        }
        if (slots.isNotEmpty()) {
            item(key = "ready-footer") {
                Text(
                    "Ready around ${clockText(roundToFiveMin(readyAt), zone)}",
                    Modifier.padding(start = 88.dp, top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.tertiary,
                )
            }
        }
    }

    // Count items preceding the first timeline slot inside listState so "Jump to step" lands exactly.
    val timelineOffset = (if (!wide) 2 else 0) +
        (if (cookable.size >= 2) 1 else 0) +
        (if (showPrepSection) {
            1 + if (prepOpen) {
                (if (timeline.ovenRequirements.isNotEmpty() || timeline.boilWaterDishes.isNotEmpty()) 1 else 0) + visiblePrep.size
            } else 0
        } else 0) +
        (if (slots.isNotEmpty()) 1 else 0)

    Scaffold(
        topBar = {
            YcTopBar(
                title = title,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (onKitchenTools != null) {
                        IconButton(onClick = onKitchenTools) { Icon(Icons.Filled.Scale, "Kitchen tools: converter and reference") }
                    }
                    FilterChip(
                        selected = keepScreenOn,
                        onClick = onToggleScreen,
                        label = { Text("Screen on") },
                        leadingIcon = {
                            Icon(if (keepScreenOn) Icons.Filled.LightMode else Icons.Outlined.LightMode, null, Modifier.size(18.dp))
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
        floatingActionButton = { if (onAddNote != null) CookNoteButton(noteCount, onAddNote) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showJumpToNow && current != null) {
                Surface(
                    color = colors.primaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val idx = slots.indexOfFirst { it.first == current }
                        if (idx >= 0) {
                            scrollScope.launch { listState.animateScrollToItem(timelineOffset + idx) }
                        }
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "NOW · ~${clockText(current, zone)}",
                            style = OverlineStyle,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Jump to step", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
            }
            if (wide) {
                // Tablet two-pane layout: schedule + expandable dish ingredients on the left,
                // untimed prep + live timed timeline on the right.
                Row(Modifier.weight(1f).fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(0.4f).fillMaxHeight(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        scheduleAndDishesItems()
                    }
                    VerticalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    LazyColumn(
                        Modifier.weight(0.6f).fillMaxHeight(),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (onAddNote != null) 96.dp else 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        prepAndTimelineItems()
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 860.dp),
                    state = listState,
                    // Room under the last step for the note button.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (onAddNote != null) 96.dp else 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    scheduleAndDishesItems()
                    prepAndTimelineItems()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupCuesCard(timeline: MealTimeline) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            timeline.ovenRequirements.forEach { oven ->
                Surface(shape = CircleShape, color = colors.tertiaryContainer) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Whatshot, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Oven ${oven.tempLabel} · ${oven.recipeName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onTertiaryContainer,
                        )
                    }
                }
            }
            timeline.boilWaterDishes.forEach { dish ->
                Surface(shape = CircleShape, color = colors.secondaryContainer) {
                    Text(
                        "Boil water · $dish",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (timeline.hasOvenConflict) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Different oven temps (${timeline.ovenRequirements.joinToString(" vs ") { "${it.tempLabel} ${it.recipeName}" }}) — plan rack order or cook sequentially.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrepStepCard(
    recipe: MealRecipeView,
    stepIndex: Int,
    checked: Set<Int>,
    scale: Double,
    done: Boolean,
    onToggleDone: () -> Unit,
    onToggle: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val detail = recipe.detail ?: return
    val step = detail.recipeInstructions.getOrNull(stepIndex) ?: return
    val perStep = remember(detail) { stepIngredientsFor(detail) }
    val firstUse = remember(perStep) { firstSteps(perStep) }
    val used = perStep.getOrNull(stepIndex).orEmpty()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(recipe.color))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${recipe.name.uppercase()} · PREP",
                        style = OverlineStyle,
                        color = recipe.color,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onToggleDone, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (done) "Mark prep undone" else "Mark prep done",
                            tint = if (done) colors.tertiary else colors.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                step.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(top = 4.dp))
                }
                Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                if (used.isNotEmpty()) {
                    StepIngredients(
                        lines = stepIngredientRows(detail, used, stepIndex, firstUse) { scaleIngredient(it, scale) },
                        checked = checked,
                        onToggle = onToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyCard(
    readyAt: Long,
    startAt: Long,
    leadSeconds: Long,
    now: Long,
    started: Boolean,
    nextReminder: String?,
    zone: ZoneId,
    canStart: Boolean,
    startNowMode: Boolean = false,
    onStartNowMode: ((Boolean) -> Unit)? = null,
    onPickTime: () -> Unit,
    onUseEarliest: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    paused: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onShift: ((Long) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            if (!started && onStartNowMode != null && leadSeconds > 0L) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    SegmentedButton(
                        selected = startNowMode,
                        onClick = { onStartNowMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text("Start now") },
                    )
                    SegmentedButton(
                        selected = !startNowMode,
                        onClick = { onStartNowMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text("Ready at") },
                    )
                }
            }
            if (leadSeconds == 0L) {
                Text("NO-COOK MEAL", style = OverlineStyle, color = colors.tertiary)
                Text("Prep at your own pace", style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                Text(
                    "No timed cooking steps — work through the prep list whenever you're ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (!started && startNowMode) {
                Text("STARTING NOW", style = OverlineStyle, color = colors.tertiary)
                Text("Ready ~${clockText(roundToFiveMin(readyAt), zone)}", style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                Text(
                    "${formatShort(leadSeconds)} timed cooking after prep",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(onClick = onPickTime)) {
                        Text("READY AROUND", style = OverlineStyle, color = colors.tertiary)
                        Text(clockText(roundToFiveMin(readyAt), zone), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                        Text(
                            (if (startAt < now && !started) "Should have started ~" else "First timed step ~") +
                                clockText(roundToFiveMin(startAt), zone) + dayNote(startAt, now, zone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (startAt < now && !started) colors.error else colors.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onPickTime) { Text("Change") }
                }
                if (!started && startAt < now) {
                    TextButton(onClick = onUseEarliest, contentPadding = PaddingValues(0.dp)) { Text("Start now instead") }
                }
            }
            if (leadSeconds > 0L) {
                Spacer(Modifier.height(10.dp))
                if (started) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (paused) Icons.Filled.Pause else Icons.Filled.NotificationsActive, null,
                            tint = if (paused) colors.tertiary else colors.primary, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                paused -> "Paused. Reminders are off and the ready time moves back until you resume."
                                else -> nextReminder?.let { "Next reminder: $it" } ?: "Reminders are on"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (paused) {
                            Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Resume")
                            }
                        } else {
                            FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Pause, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pause")
                            }
                        }
                        OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                    if (!paused && onShift != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Nudge schedule",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onShift(-5 * 60_000L) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                Text("−5 min")
                            }
                            TextButton(onClick = { onShift(5 * 60_000L) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                                Text("+5 min")
                            }
                        }
                    }
                } else {
                    Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                        Text(if (startNowMode) "Start cooking now" else "Start timed cooking")
                    }
                    Text(
                        "Prep at your own pace first, then start the clock for reminders when heat goes on.",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun dayNote(at: Long, now: Long, zone: ZoneId): String {
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> ""
        today.plusDays(1) -> " tomorrow"
        else -> " on " + day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

@Composable
private fun RecipeLine(
    r: MealRecipeView,
    startAt: Long?,
    totalSeconds: Long?,
    prepSeconds: Long?,
    timesKnown: Boolean?,
    zone: ZoneId,
    open: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.size(10.dp).background(r.color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(r.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            when {
                r.detail == null -> "Not downloaded yet; connect the VPN"
                totalSeconds == 0L -> if (prepSeconds != null && prepSeconds > 0L) "Prep ahead · ~${formatShort(prepSeconds)}" else "Prep ahead"
                startAt != null && totalSeconds != null ->
                    "~${clockText(roundToFiveMin(startAt), zone)} · ${formatShort(totalSeconds)}" + if (timesKnown == false) " (est.)" else ""
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (r.detail == null || timesKnown == false) colors.tertiary else colors.onSurfaceVariant,
        )
        if (onToggle != null) {
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Hide ingredients" else "Show ingredients",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(22.dp),
            )
        }
    }
}

/** One dish's whole ingredient list, opened from its name at the top of the meal. */
@Composable
private fun DishIngredients(
    recipe: MealRecipeView,
    checked: Set<Int>,
    scale: Double = 1.0,
    onScaleChange: ((Double) -> Unit)? = null,
    onToggle: (Int) -> Unit,
) {
    val detail = recipe.detail ?: return
    val colors = MaterialTheme.colorScheme
    val base = remember(detail.id) { baseServings(detail.recipeServings, detail.recipeYield) }
    val factors = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(recipe.color))
            Column(Modifier.padding(vertical = 6.dp).weight(1f)) {
                if (onScaleChange != null) {
                    val currentServings = base?.let { it * scale }
                    val label = if (currentServings != null) {
                        val n = formatAmount(currentServings)
                        if (currentServings == 1.0) "Serves 1" else "Serves $n"
                    } else "×${formatAmount(scale)}"
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Ingredients", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = if (kotlin.math.abs(scale - 1.0) > 0.01) colors.tertiaryContainer else colors.surfaceContainer,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (base != null) {
                                            val next = ((base * scale) - 1.0).coerceAtLeast(1.0)
                                            onScaleChange(next / base)
                                        } else {
                                            onScaleChange(factors.lastOrNull { it < scale } ?: scale)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) { Icon(Icons.Filled.Remove, "Fewer servings", Modifier.size(16.dp)) }
                                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 2.dp))
                                IconButton(
                                    onClick = {
                                        if (base != null) onScaleChange(((base * scale) + 1.0) / base)
                                        else onScaleChange(factors.firstOrNull { it > scale } ?: scale)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) { Icon(Icons.Filled.Add, "More servings", Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                detail.recipeIngredient.forEachIndexed { i, ing ->
                    // Mealie's section headings ("For the glaze") sit on the line below them.
                    ing.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
                    }
                    if (isHeadingOnly(ing)) return@forEachIndexed
                    val text = scaleIngredient(ingredientText(ing), scale)
                    val done = i in checked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { onToggle(i) }.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = done, onCheckedChange = { onToggle(i) }, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            color = if (done) colors.outline else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    recipe: MealRecipeView,
    stepIndex: Int,
    timelineStep: MealTimelineStep,
    checked: Set<Int>,
    scale: Double,
    done: Boolean,
    onToggleDone: () -> Unit,
    onToggle: (Int) -> Unit,
    activeTimers: List<CookTimer>,
    now: Long,
    onStartTimer: (MealRecipeView, String, Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val detail = recipe.detail ?: return
    val step = detail.recipeInstructions.getOrNull(stepIndex) ?: return
    val perStep = remember(detail) { stepIngredientsFor(detail) }
    val firstUse = remember(perStep) { firstSteps(perStep) }
    val used = perStep.getOrNull(stepIndex).orEmpty()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().alpha(if (done) 0.55f else 1f),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(recipe.color))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${recipe.name.uppercase()} · STEP ${stepIndex + 1}",
                        style = OverlineStyle,
                        color = recipe.color,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                    if (timelineStep.passive && timelineStep.durationSeconds > 0) {
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceContainerHighest,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                "HANDS-OFF · ${formatShort(timelineStep.durationSeconds).uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    IconButton(onClick = onToggleDone, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = if (done) "Mark step undone" else "Mark step done",
                            tint = if (done) colors.tertiary else colors.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                step.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(top = 4.dp))
                }
                Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                if (used.isNotEmpty()) {
                    StepIngredients(
                        lines = stepIngredientRows(detail, used, stepIndex, firstUse) { scaleIngredient(it, scale) },
                        checked = checked,
                        onToggle = onToggle,
                    )
                }
                val found = remember(step.text, step.durationSeconds) { stepTimers(step.text, step.durationSeconds) }
                if (found.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        found.forEach { d ->
                            val label = timerStepLabel(stepIndex + 1, step.summary?.takeIf { it.isNotBlank() } ?: step.title, step.text, d.text)
                            val running = activeTimers.firstOrNull { it.recipeSlug == recipe.slug && it.label == label }
                            val chipText = when {
                                running == null -> formatShort(d.seconds)
                                running.done -> "Time's up!"
                                running.running -> formatClock((running.remainingMs(now) + 999) / 1000)
                                else -> "Paused · ${formatClock(running.pausedRemainingMs / 1000)}"
                            }
                            TimerChip(chipText) {
                                if (running == null) onStartTimer(recipe, label, d.seconds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyTimeDialog(readyAt: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(readyAt).atZone(zone)
    val state = rememberTimePickerState(current.hour, current.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ready at") },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(current.withHour(state.hour).withMinute(state.minute).withSecond(0).withNano(0).toInstant().toEpochMilli())
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A strip above the timers while a meal is on, so it is one tap away from anywhere. */
@Composable
fun MealInProgressBar(session: MealSession, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val next = if (session.pausedAt != null) null else session.reminders.firstOrNull { it.at > now }
    Surface(color = colors.primaryContainer, modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title + " · ready " + clockText(session.readyAtNow(now)), style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                if (session.pausedAt != null) Text("Paused", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
                next?.let { Text("Next: ${it.title}", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer) }
            }
            Text("Open", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
