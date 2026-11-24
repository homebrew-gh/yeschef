package app.yeschef.ui.cook

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Scale
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import app.yeschef.data.cook.MealRecipeIn
import app.yeschef.data.cook.MealSession
import app.yeschef.data.cook.MealStepIn
import app.yeschef.data.cook.MealTimeline
import app.yeschef.data.cook.buildMealTimeline
import app.yeschef.data.cook.firstSteps
import app.yeschef.data.cook.formatShort
import app.yeschef.data.cook.planReminders
import app.yeschef.data.cook.timerStepLabel
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.sync.SyncScheduler
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar
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
    val started = session?.slugs == request.slugs
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    var chosenReadyAt by rememberSaveable(request.encode()) { mutableStateOf<Long?>(null) }
    val readyAt = (if (started) session?.readyAt else null) ?: chosenReadyAt
        ?: defaultReadyAt(request.date, request.entryType, timeline.leadSeconds, now)

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
            chosenReadyAt = at
            if (started) begin(at) // re-plan the reminders for the new time
        }
    }
    var askEnd by rememberSaveable { mutableStateOf(false) }
    if (askEnd) {
        AlertDialog(
            onDismissRequest = { askEnd = false },
            title = { Text("Finish this meal?") },
            text = { Text("Reminders for it stop. Mark every recipe in it as cooked today?") },
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
        nextReminder = if (started) session?.reminders?.firstOrNull { it.at > now }?.let { it.title } else null,
        keepScreenOn = keepOn,
        onToggleScreen = { keepOn = !keepOn },
        onPickTime = { picking = true },
        onUseEarliest = { chosenReadyAt = earliestReadyAt(timeline.leadSeconds, System.currentTimeMillis()) },
        onStart = { begin(readyAt) },
        onFinish = { askEnd = true },
        onBack = onClose,
        onStartTimer = { recipe, label, seconds -> startTimer(label, seconds, recipe.slug, recipe.name) },
        onKitchenTools = { kitchenTools.open = true },
    )
}

/** Stateless meal cook mode, also used for screenshots. */
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
    keepScreenOn: Boolean,
    onToggleScreen: () -> Unit,
    onPickTime: () -> Unit,
    onUseEarliest: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    onStartTimer: (MealRecipeView, String, Long) -> Unit,
    onKitchenTools: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MaterialTheme.colorScheme
    val startAt = readyAt - timeline.leadSeconds * 1000
    // Merged steps, bucketed by the minute they start.
    val slots = remember(timeline, readyAt) {
        timeline.steps.groupBy { (readyAt - it.startsBefore * 1000) / 60_000 * 60_000 }.toList()
    }
    // The slot in progress: the latest one that has started.
    val current = if (started) slots.lastOrNull { it.first <= now }?.first else null

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
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 860.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ReadyCard(
                    readyAt = readyAt, startAt = startAt, now = now, started = started, nextReminder = nextReminder,
                    zone = zone, onPickTime = onPickTime, onUseEarliest = onUseEarliest, onStart = onStart, onFinish = onFinish,
                    canStart = cookable.isNotEmpty(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipes.forEach { r ->
                        val plan = cookable.indexOf(r).takeIf { it >= 0 }?.let { timeline.recipes.getOrNull(it) }
                        RecipeLine(r, plan?.let { readyAt - it.startsBefore * 1000 }, plan?.totalSeconds, plan?.timesKnown, zone)
                    }
                }
            }
            if (slots.isNotEmpty()) item { SectionLabel("Timeline", Modifier.padding(top = 4.dp)) }
            slots.forEach { (at, steps) ->
                item(key = "t-$at") {
                    val past = current != null && at < current
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(if (past) 0.55f else 1f)) {
                        Column(Modifier.width(78.dp).padding(top = 10.dp)) {
                            Text(
                                clockText(at, zone),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (at == current) colors.primary else colors.onSurface,
                            )
                            if (at == current) Text("NOW", style = OverlineStyle, color = colors.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            steps.forEach { s -> StepCard(cookable[s.recipeIndex], s.stepIndex, onStartTimer) }
                        }
                    }
                }
            }
            item {
                Text(
                    "Ready at ${clockText(readyAt, zone)}",
                    Modifier.padding(start = 78.dp, top = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(
    readyAt: Long,
    startAt: Long,
    now: Long,
    started: Boolean,
    nextReminder: String?,
    zone: ZoneId,
    canStart: Boolean,
    onPickTime: () -> Unit,
    onUseEarliest: () -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onPickTime)) {
                    Text("READY AT", style = OverlineStyle, color = colors.tertiary)
                    Text(clockText(readyAt, zone), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                    Text(
                        (if (startAt < now && !started) "Should have started " else "First step at ") + clockText(startAt, zone) + dayNote(startAt, now, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (startAt < now && !started) colors.error else colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onPickTime) { Text("Change") }
            }
            if (!started && startAt < now) {
                TextButton(onClick = onUseEarliest, contentPadding = PaddingValues(0.dp)) { Text("Start now instead") }
            }
            Spacer(Modifier.height(10.dp))
            if (started) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        nextReminder?.let { "Next reminder: $it" } ?: "Reminders are on",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onFinish) { Text("Finish") }
                }
            } else {
                Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text("Start this meal") }
                Text(
                    "You'll get a reminder when each part is due, even with the app closed.",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
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
private fun RecipeLine(r: MealRecipeView, startAt: Long?, totalSeconds: Long?, timesKnown: Boolean?, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(Modifier.size(10.dp).background(r.color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(r.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            when {
                r.detail == null -> "Not downloaded yet; connect the VPN"
                startAt != null && totalSeconds != null ->
                    "Starts ${clockText(startAt, zone)} · ${formatShort(totalSeconds)}" + if (timesKnown == false) " (estimated)" else ""
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (r.detail == null || timesKnown == false) colors.tertiary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepCard(recipe: MealRecipeView, stepIndex: Int, onStartTimer: (MealRecipeView, String, Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val detail = recipe.detail ?: return
    val step = detail.recipeInstructions.getOrNull(stepIndex) ?: return
    val perStep = remember(detail) { stepIngredientsFor(detail) }
    val firstUse = remember(perStep) { firstSteps(perStep) }
    val used = perStep.getOrNull(stepIndex).orEmpty()
    var checked by remember(detail.id) { mutableStateOf(setOf<Int>()) }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(recipe.color))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).weight(1f)) {
                Text(
                    "${recipe.name.uppercase()} · STEP ${stepIndex + 1}",
                    style = OverlineStyle,
                    color = recipe.color,
                )
                step.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(top = 4.dp))
                }
                Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                if (used.isNotEmpty()) {
                    StepIngredients(
                        lines = stepIngredientRows(detail, used, stepIndex, firstUse),
                        checked = checked,
                        onToggle = { i -> checked = if (i in checked) checked - i else checked + i },
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
                                onStartTimer(
                                    recipe,
                                    timerStepLabel(stepIndex + 1, step.summary?.takeIf { it.isNotBlank() } ?: step.title, step.text, d.text),
                                    d.seconds,
                                )
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
    val next = session.reminders.firstOrNull { it.at > now }
    Surface(color = colors.primaryContainer, modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title + " · ready " + clockText(session.readyAt), style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                next?.let { Text("Next: ${it.title}", style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer) }
            }
            Text("Open", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
