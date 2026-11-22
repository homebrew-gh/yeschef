package app.yeschef.ui.recipes

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.cook.DetectedDuration
import app.yeschef.data.cook.detectDurations
import app.yeschef.data.cook.formatShort
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.isWide
import app.yeschef.ui.cook.CustomTimerDialog
import app.yeschef.ui.cook.TimerChip
import app.yeschef.ui.cook.rememberTimerStarter

@Composable
fun RecipeDetailScreen(container: AppContainer, slug: String, onBack: (() -> Unit)?) {
    val repo = container.repository
    val data by remember(slug) { repo.recipe(slug) }.collectAsStateWithLifecycle(null to null)
    val (entity, detail) = data
    val config by container.config.collectAsStateWithLifecycle()
    LaunchedEffect(slug) { repo.refreshRecipe(slug) }

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
                startTimer(label, seconds, slug)
                showCustomTimer = false
            },
        )
    }

    RecipeDetailContent(
        title = recipeName,
        detail = detail,
        notLoadedMessage = if (entity == null) "Recipe not found." else "This recipe hasn't been downloaded yet. Connect the VPN to load it.",
        serverUrl = config?.serverUrl,
        cookMode = cookMode,
        onToggleCookMode = { cookMode = !cookMode },
        onBack = onBack,
        onCustomTimer = { showCustomTimer = true },
        onStartTimer = { stepNumber, seconds -> startTimer("$recipeName · step $stepNumber", seconds, slug) },
    )
}

/** Stateless recipe page, also used for screenshots. */
@Composable
fun RecipeDetailContent(
    title: String,
    detail: RecipeDetailDto?,
    notLoadedMessage: String,
    serverUrl: String?,
    cookMode: Boolean,
    onToggleCookMode: () -> Unit,
    onBack: (() -> Unit)?,
    onCustomTimer: () -> Unit,
    onStartTimer: (stepNumber: Int, seconds: Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val wide = isWide()
    var checkedIngredients by remember(detail?.id) { mutableStateOf(setOf<Int>()) }
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
    ) { padding ->
        if (detail == null) {
            Text(notLoadedMessage, Modifier.padding(padding).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (!cookMode) {
                item {
                    RecipeImage(
                        serverUrl, detail.id,
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .then(if (wide) Modifier.height(300.dp) else Modifier.aspectRatio(16f / 10f))
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(detail.name, style = MaterialTheme.typography.headlineMedium)
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

            item { SectionLabel("Ingredients") }
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
                            IngredientRow(ingredientText(ingredient), done, bodyStyle) {
                                checkedIngredients = if (done) checkedIngredients - index else checkedIngredients + index
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Method") }
            itemsIndexed(detail.recipeInstructions) { index, step ->
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
                            val found = remember(step.text, step.durationSeconds) { stepTimers(step.text, step.durationSeconds) }
                            if (found.isNotEmpty()) {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    found.forEach { d -> TimerChip(formatShort(d.seconds)) { onStartTimer(index + 1, d.seconds) } }
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

private fun ingredientText(i: IngredientDto): String =
    i.display?.takeIf { it.isNotBlank() }
        ?: i.originalText?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(i.unit?.name, i.food?.name, i.note).joinToString(" ")

/** Times mentioned in the step text, plus the step's own duration from Mealie if it isn't already among them. */
private fun stepTimers(text: String, durationSeconds: Int?): List<DetectedDuration> {
    val found = detectDurations(text)
    val fromMealie = durationSeconds?.toLong()?.takeIf { s -> s > 0 && found.none { it.seconds == s } }
    return if (fromMealie != null) listOf(DetectedDuration(fromMealie, "")) + found else found
}
