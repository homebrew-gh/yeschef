package app.yeschef.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.local.RecipeEntity
import app.yeschef.ui.HatLogo
import app.yeschef.ui.RecipeImage
import app.yeschef.ui.SyncAction
import app.yeschef.ui.YcTopBar
import app.yeschef.ui.isWide

/** Recipe grid; on the tablet the selected recipe opens beside it. */
@Composable
fun RecipesScreen(container: AppContainer, openSlug: String?, onOpen: (String?) -> Unit) {
    val recipes by remember { container.repository.recipes() }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    val list: @Composable (Modifier) -> Unit = { modifier ->
        RecipeGrid(recipes, config?.serverUrl, openSlug, onOpen, actions = { SyncAction(container) }, modifier = modifier)
    }
    if (isWide()) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(400.dp).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (openSlug != null) {
                    RecipeDetailScreen(container, openSlug, onBack = null)
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        HatLogo(120.dp)
                        Text("Pick a recipe", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else if (openSlug != null) {
        BackHandler { onOpen(null) }
        RecipeDetailScreen(container, openSlug, onBack = { onOpen(null) })
    } else {
        list(Modifier.fillMaxSize())
    }
}

/** Stateless recipe grid, also used for screenshots. */
@Composable
fun RecipeGrid(
    recipes: List<RecipeEntity>,
    serverUrl: String?,
    selectedSlug: String?,
    onOpen: (String) -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(recipes, query) {
        if (query.isBlank()) recipes else recipes.filter {
            it.name.contains(query, ignoreCase = true) || it.description.orEmpty().contains(query, ignoreCase = true)
        }
    }
    Scaffold(
        topBar = { YcTopBar("Recipes", actions = { actions() }) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search ${recipes.size} recipes") },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            items(filtered, key = { it.id }) { recipe ->
                RecipeCard(recipe, serverUrl, recipe.slug == selectedSlug) { onOpen(recipe.slug) }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, serverUrl: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow),
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    ) {
        RecipeImage(serverUrl, recipe.id, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                recipe.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            if (!recipe.totalTime.isNullOrBlank()) {
                Spacer(Modifier.size(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(recipe.totalTime, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
