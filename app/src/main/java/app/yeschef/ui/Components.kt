package app.yeschef.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yeschef.R
import app.yeschef.data.remote.recipeImageUrl
import app.yeschef.ui.theme.OverlineStyle
import coil.compose.AsyncImage

/** The YesChef chef's hat, same artwork as the app icon. */
@Composable
fun HatLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = modifier.size(size))
}

/** Recipe photo, with a soft blue placeholder and hat while loading or when there is no photo. */
@Composable
fun RecipeImage(serverUrl: String?, recipeId: String, modifier: Modifier = Modifier, size: String = "min-original") {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            alpha = 0.55f,
            modifier = Modifier.fillMaxSize(0.7f),
        )
        if (serverUrl != null) {
            AsyncImage(
                model = recipeImageUrl(serverUrl, recipeId, size, ImageVersions[recipeId]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Clean white top bar with a serif title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YcTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        // A long recipe name scrolls by instead of being cut off.
        title = { Text(title, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 2_000)) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** Small spaced-out label above a group, e.g. "INGREDIENTS". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}
