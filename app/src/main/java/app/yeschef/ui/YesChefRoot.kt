package app.yeschef.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
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
        when (tab) {
            Tab.Shopping -> ShoppingScreen(container)
            Tab.Recipes -> RecipesScreen(container, openSlug = openRecipe, onOpen = { openRecipe = it })
            Tab.MealPlan -> MealPlanScreen(container, onOpenRecipe = { slug ->
                openRecipe = slug
                tab = Tab.Recipes
            })
            Tab.Settings -> SettingsScreen(container)
        }
    }
}
