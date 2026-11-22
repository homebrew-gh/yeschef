package app.yeschef.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.local.RecipeEntity
import app.yeschef.data.remote.recipeImageUrl
import app.yeschef.ui.SyncAction
import app.yeschef.ui.isWide
import coil.compose.AsyncImage

/** Recipe list; on the tablet the selected recipe opens beside the list. */
@Composable
fun RecipesScreen(container: AppContainer, openSlug: String?, onOpen: (String?) -> Unit) {
    val wide = isWide()
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(360.dp).fillMaxHeight()) {
                RecipeList(container, selectedSlug = openSlug, onOpen = onOpen)
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (openSlug != null) {
                    RecipeDetailScreen(container, openSlug, onBack = null)
                } else {
                    Text("Pick a recipe", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else if (openSlug != null) {
        BackHandler { onOpen(null) }
        RecipeDetailScreen(container, openSlug, onBack = { onOpen(null) })
    } else {
        RecipeList(container, selectedSlug = null, onOpen = onOpen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeList(container: AppContainer, selectedSlug: String?, onOpen: (String) -> Unit) {
    val recipes by remember { container.repository.recipes() }.collectAsStateWithLifecycle(emptyList())
    val config by container.config.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(recipes, query) {
        if (query.isBlank()) recipes else recipes.filter {
            it.name.contains(query, ignoreCase = true) || it.description.orEmpty().contains(query, ignoreCase = true)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Recipes") }, actions = { SyncAction(container) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search ${recipes.size} recipes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { recipe ->
                    RecipeRow(recipe, config?.serverUrl, recipe.slug == selectedSlug) { onOpen(recipe.slug) }
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: RecipeEntity, serverUrl: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(recipe.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (!recipe.totalTime.isNullOrBlank()) {
            { Text(recipe.totalTime) }
        } else {
            null
        },
        leadingContent = {
            if (serverUrl != null) {
                AsyncImage(
                    model = recipeImageUrl(serverUrl, recipe.id, "tiny-original"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Spacer(Modifier.size(56.dp))
            }
        },
        colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
