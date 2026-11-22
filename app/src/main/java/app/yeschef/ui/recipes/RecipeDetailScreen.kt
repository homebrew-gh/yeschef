package app.yeschef.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.recipeImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
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
    val checkedIngredients = remember(slug) { mutableStateOf(setOf<Int>()) }
    val bodyStyle = if (cookMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: entity?.name ?: "", maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { cookMode = !cookMode }) {
                        Icon(
                            if (cookMode) Icons.Filled.LightMode else Icons.Outlined.LightMode,
                            contentDescription = if (cookMode) "Cook mode on: screen stays awake" else "Cook mode: keep screen awake",
                            tint = if (cookMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (detail == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(
                    if (entity == null) "Recipe not found." else "This recipe hasn't been downloaded yet. Connect the VPN to load it.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!cookMode && config != null) {
                item {
                    AsyncImage(
                        model = recipeImageUrl(config!!.serverUrl, detail.id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    detail.description?.takeIf { it.isNotBlank() && !cookMode }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val facts = listOfNotNull(
                        detail.recipeYield?.takeIf { it.isNotBlank() },
                        detail.prepTime?.takeIf { it.isNotBlank() }?.let { "Prep $it" },
                        detail.performTime?.takeIf { it.isNotBlank() }?.let { "Cook $it" },
                        detail.totalTime?.takeIf { it.isNotBlank() }?.let { "Total $it" },
                    )
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                }
            }
            item { SectionTitle("Ingredients") }
            itemsIndexed(detail.recipeIngredient) { index, ingredient ->
                ingredient.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
                }
                val done = index in checkedIngredients.value
                Row(
                    Modifier.fillMaxWidth().clickable {
                        checkedIngredients.value = if (done) checkedIngredients.value - index else checkedIngredients.value + index
                    }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = done, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(
                        ingredientText(ingredient),
                        style = bodyStyle,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                }
            }
            item { SectionTitle("Steps") }
            itemsIndexed(detail.recipeInstructions) { index, step ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    step.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Row {
                        Text("${index + 1}.", style = bodyStyle, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                        Text(step.text, style = bodyStyle)
                    }
                }
            }
            detail.orgURL?.takeIf { it.isNotBlank() }?.let { url ->
                item { Text("Source: $url", Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun ingredientText(i: IngredientDto): String =
    i.display?.takeIf { it.isNotBlank() }
        ?: i.originalText?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(i.unit?.name, i.food?.name, i.note).joinToString(" ")
