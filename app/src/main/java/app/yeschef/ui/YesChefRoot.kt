package app.yeschef.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.yeschef.ui.cook.TimerTray
import app.yeschef.AppContainer
import app.yeschef.ui.mealplan.MealPlanScreen
import app.yeschef.ui.recipes.RecipesScreen
import app.yeschef.ui.settings.SettingsScreen
import app.yeschef.ui.shopping.ShoppingScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Shopping("Shopping", Icons.Filled.ShoppingCart),
    Recipes("Recipes", Icons.Filled.Restaurant),
    MealPlan("Meal plan", Icons.Filled.CalendarMonth),
    Settings("Settings", Icons.Filled.Settings),
}

/** Bottom bar on the phone, navigation rail on the tablet (NavigationSuiteScaffold picks). */
@Composable
fun YesChefRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(Tab.Shopping) }
    var openRecipe by rememberSaveable { mutableStateOf<String?>(null) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { tab = t },
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { TabContent(tab, container, openRecipe, { openRecipe = it }, { tab = it }) }
            // On the tablet the rail leaves the bottom edge to the system bar, so pad for it there.
            TimerTray(container.timers, if (isWide()) Modifier.navigationBarsPadding() else Modifier)
        }
    }
}

@Composable
private fun TabContent(
    tab: Tab,
    container: AppContainer,
    openRecipe: String?,
    onOpenRecipe: (String?) -> Unit,
    onSelectTab: (Tab) -> Unit,
) {
    when (tab) {
        Tab.Shopping -> ShoppingScreen(container)
        Tab.Recipes -> RecipesScreen(container, openSlug = openRecipe, onOpen = onOpenRecipe)
        Tab.MealPlan -> MealPlanScreen(container, onOpenRecipe = { slug ->
            onOpenRecipe(slug)
            onSelectTab(Tab.Recipes)
        })
        Tab.Settings -> SettingsScreen(container)
    }
}
