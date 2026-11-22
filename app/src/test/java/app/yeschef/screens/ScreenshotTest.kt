package app.yeschef.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.yeschef.data.ShoppingRow
import app.yeschef.data.cook.CookTimer
import app.yeschef.data.local.MealPlanEntity
import app.yeschef.data.local.RecipeEntity
import app.yeschef.data.remote.IngredientDto
import app.yeschef.data.remote.RecipeDetailDto
import app.yeschef.data.remote.StepDto
import app.yeschef.ui.cook.TimerTrayContent
import app.yeschef.ui.mealplan.MealPlanContent
import app.yeschef.ui.recipes.RecipeDetailContent
import app.yeschef.ui.recipes.RecipeGrid
import app.yeschef.ui.setup.SetupForm
import app.yeschef.ui.shopping.ShoppingContent
import app.yeschef.ui.theme.YesChefTheme
import kotlinx.serialization.json.JsonPrimitive
import app.yeschef.data.ThemeMode
import app.yeschef.ui.settings.ThemePicker
import androidx.compose.foundation.layout.padding
import app.yeschef.data.Cookbook
import app.yeschef.ui.recipes.TagEditorContent
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Renders the main screens with sample data. CI records these images into
 * app/src/test/snapshots/images so the look can be reviewed on the pull request.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.5)

    @Test fun shopping() = phone(tab = 0) { Shopping() }

    @Test fun shoppingDark() = phone(tab = 0, dark = true) { Shopping() }

    /** Scrolled to the bottom: the opened cart with Uncheck all and Clear cart. */
    @Test fun shoppingCart() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 4200))
        phone(tab = 0) { Shopping(showChecked = true) }
    }

    @Test fun shoppingWithTimers() = phone(tab = 0) {
        Column {
            Box(Modifier.weight(1f)) { Shopping() }
            TimerTrayContent(Sample.timers, now = Sample.NOW)
        }
    }

    @Test fun recipes() = phone(tab = 1) { RecipeGrid(Sample.recipes, null, null, {}, actions = { SyncIcon() }, today = Sample.today) }

    @Test fun recipesSides() = phone(tab = 1) {
        RecipeGrid(Sample.recipes, null, null, {}, actions = { SyncIcon() }, today = Sample.today, initialChapter = "Sides")
    }

    @Test fun recipesMainsSeafood() = phone(tab = 1, dark = true) {
        RecipeGrid(Sample.recipes, null, null, {}, actions = { SyncIcon() }, today = Sample.today, initialChapter = "Mains", initialProtein = "Seafood")
    }

    @Test fun recipeDetail() = phone(tab = 1) { Detail(cookMode = false) }

    @Test fun recipeCookMode() = phone(tab = 1, dark = true) { Detail(cookMode = true) }

    /** The whole cook-mode page, so the ingredients under each step and the timer labels show. */
    @Test fun recipeCookModeFull() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5600))
        phone(tab = 1) {
            Column {
                Box(Modifier.weight(1f)) { Detail(cookMode = true) }
                TimerTrayContent(Sample.timers, now = Sample.NOW)
            }
        }
    }

    /** Lasagna scaled from 8 to 12 servings, with the nutrition summary below the ingredients. */
    @Test fun recipeScaled() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 3600))
        phone(tab = 1) { Detail(cookMode = false, servings = 12.0) }
    }

    @Test fun tabletCookMode() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_C.copy(screenHeight = 3200))
        paparazzi.snapshot {
            YesChefTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.weight(1f)) { Detail(cookMode = true, withBack = false) }
                    TimerTrayContent(Sample.timers, now = Sample.NOW)
                }
            }
        }
    }

    @Test fun mealPlan() = phone(tab = 2) {
        MealPlanContent(Sample.mealPlan, Sample.today, null, {}, actions = { SyncIcon() })
    }

    @Test fun tagEditor() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 2800))
        paparazzi.snapshot {
            YesChefTheme {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(top = 16.dp)) {
                    TagEditorContent(
                        selection = Cookbook.TagSelection("Mains", setOf("Beef"), setOf("Make ahead", "Sunday dinner")),
                        chapters = Cookbook.chapterChoices(Sample.recipes),
                        tags = Cookbook.tagChoices(Sample.recipes),
                        onChange = {}, onSave = {}, onCancel = {},
                    )
                }
            }
        }
    }

    @Test fun themePicker() = paparazzi.snapshot {
        YesChefTheme {
            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                ThemePicker(ThemeMode.System) {}
            }
        }
    }

    @Test fun setup() = paparazzi.snapshot {
        YesChefTheme {
            SetupForm("https://mealie.home.lan", {}, "", {}, false, {}, "", {}, "", {}, busy = false, error = null, onConnect = {})
        }
    }

    @Test fun tabletRecipes() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_C)
        paparazzi.snapshot {
            YesChefTheme {
                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavigationRail {
                        tabs.forEachIndexed { i, (label, icon) ->
                            NavigationRailItem(selected = i == 1, onClick = {}, icon = { Icon(icon, null) }, label = { Text(label) })
                        }
                    }
                    RecipeGrid(Sample.recipes, null, "classic-lasagna", {}, actions = { SyncIcon() }, today = Sample.today, modifier = Modifier.width(400.dp).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f)) { Detail(cookMode = false, withBack = false) }
                }
            }
        }
    }

    private fun phone(tab: Int, dark: Boolean = false, content: @Composable () -> Unit) = paparazzi.snapshot {
        YesChefTheme(darkTheme = dark) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.weight(1f)) { content() }
                NavigationBar {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = i == tab, onClick = {}, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        }
    }

    private val tabs = listOf(
        "Shopping" to Icons.Filled.ShoppingCart,
        "Recipes" to Icons.Filled.Restaurant,
        "Meal plan" to Icons.Filled.CalendarMonth,
        "Settings" to Icons.Filled.Settings,
    )
}

@Composable
private fun SyncIcon() {
    IconButton(onClick = {}) { Icon(Icons.Filled.CloudDone, "Synced") }
}

@Composable
private fun Shopping(showChecked: Boolean = false) = ShoppingContent(
    title = "Weekly groceries",
    lists = listOf("1" to "Weekly groceries", "2" to "Costco"),
    onPickList = {},
    hasList = true,
    rows = Sample.shopping,
    labelOrder = mapOf("produce" to 0, "dairy" to 1, "bakery" to 2, "meat" to 3, "pantry" to 4),
    recipeNames = Sample.listRecipes,
    emptyMessage = "",
    refreshing = false,
    onRefresh = {},
    onToggle = {},
    onAdd = {},
    actions = { SyncIcon() },
    initialShowChecked = showChecked,
)

@Composable
private fun Detail(cookMode: Boolean, withBack: Boolean = true, servings: Double? = null) = RecipeDetailContent(
    title = Sample.lasagna.name,
    detail = Sample.lasagna,
    notLoadedMessage = "",
    serverUrl = null,
    cookMode = cookMode,
    onToggleCookMode = {},
    onBack = if (withBack) ({}) else null,
    lastCooked = "Cooked 7 weeks ago",
    tagLine = listOf("Mains", "Beef", "Make ahead"),
    onEditTags = {},
    initialServings = servings,
    onCustomTimer = {},
    onStartTimer = { _, _ -> },
    onTakePhoto = {},
    onPickPhoto = {},
)

private object Sample {
    const val NOW = 1_700_000_000_000L
    val today: LocalDate = LocalDate.of(2026, 11, 23)

    val listRecipes = mapOf("r-lasagna" to "Classic lasagna", "r-tacos" to "Chicken tacos", "r-bread" to "Banana bread")

    private fun row(id: String, text: String, label: String?, checked: Boolean = false, vararg recipes: String) = ShoppingRow(
        id = id, text = text, checked = checked, position = 0,
        labelId = label?.lowercase(), labelName = label,
        labelColor = mapOf("Produce" to "#6FAE4F", "Dairy" to "#4A90D9", "Bakery" to "#D4A53C", "Meat" to "#D9736A", "Pantry" to "#8C7AD6")[label],
        pending = false, recipeIds = recipes.toList(),
    )

    val shopping = listOf(
        row("1", "2 yellow onions", "Produce", false, "r-lasagna", "r-tacos"),
        row("2", "3 ripe bananas", "Produce", false, "r-bread"),
        row("3", "1 bunch cilantro", "Produce", false, "r-tacos"),
        row("4", "2 limes", "Produce", true, "r-tacos"),
        row("4b", "Paper towels", null, true),
        row("5", "16 oz ricotta", "Dairy", false, "r-lasagna"),
        row("6", "2 cups shredded mozzarella", "Dairy", false, "r-lasagna", "r-tacos"),
        row("7", "Butter", "Dairy", true, "r-bread"),
        row("8", "Corn tortillas", "Bakery", false, "r-tacos"),
        row("9", "1 lb ground beef", "Meat", false, "r-lasagna"),
        row("10", "1.5 lb chicken thighs", "Meat", false, "r-tacos"),
        row("11", "Lasagna noodles", "Pantry", false, "r-lasagna"),
        row("12", "Coffee beans", null),
    )

    private fun recipe(id: String, name: String, time: String?, cats: String, tags: String, lastMade: String? = null, rating: Double? = null) =
        RecipeEntity(id, name.lowercase().replace(' ', '-'), name, null, time, null, null, cats, tags, rating, lastMade)

    val recipes = listOf(
        recipe("r1", "Banana bread", "1 hour 10 minutes", "Baking", "Vegetarian", "2026-09-02T10:00:00Z", 5.0),
        recipe("r2", "Chicken tacos with lime crema", "35 minutes", "Mains", "Chicken\nQuick", "2026-11-18T19:00:00Z", 4.5),
        recipe("r3", "Classic lasagna", "1 hour 30 minutes", "Mains", "Beef\nMake ahead", "2026-10-04T19:00:00Z", 5.0),
        recipe("r4", "Miso glazed salmon", "25 minutes", "Mains", "Seafood\nQuick", null, 4.0),
        recipe("r5", "Shakshuka", "40 minutes", "Breakfast", "Vegetarian", "2026-11-21T09:00:00Z"),
        recipe("r6", "Roasted tomato soup", "50 minutes", "Soups & Salads", "Vegetarian", "2026-02-11T18:00:00Z"),
        recipe("r7", "Garlic green beans", "15 minutes", "Sides", "Vegetarian\nQuick", "2026-11-09T18:00:00Z", 4.0),
        recipe("r8", "Crispy smashed potatoes", "50 minutes", "Sides", "Vegetarian", null, 5.0),
        recipe("r9", "Herb rice pilaf", "30 minutes", "Sides", "Vegan"),
    )

    val lasagna = RecipeDetailDto(
        id = "r3", slug = "lasagna", name = "Classic lasagna",
        description = "Layers of rich meat sauce, creamy ricotta and bubbling mozzarella. Worth every minute.",
        recipeYield = "8 servings",
        nutrition = mapOf(
            "calories" to JsonPrimitive("520"), "proteinContent" to JsonPrimitive("31"),
            "carbohydrateContent" to JsonPrimitive("38"), "fatContent" to JsonPrimitive("26"),
            "fiberContent" to JsonPrimitive("4"), "sodiumContent" to JsonPrimitive("910"),
        ), prepTime = "30 min", performTime = "1 hr", totalTime = "1 hr 30 min",
        recipeIngredient = listOf(
            IngredientDto(display = "1 1/2 lb ground beef"),
            IngredientDto(display = "1 yellow onion, diced"),
            IngredientDto(display = "24 oz marinara sauce"),
            IngredientDto(display = "16 oz ricotta"),
            IngredientDto(display = "2 cups shredded mozzarella"),
            IngredientDto(display = "12 lasagna noodles"),
        ),
        recipeInstructions = listOf(
            StepDto(text = "Brown the beef and onion in a large pan, about 8 minutes. Stir in the marinara and simmer for 15 minutes."),
            StepDto(text = "Meanwhile, boil the noodles for 10-12 minutes until al dente, then drain."),
            StepDto(text = "Layer sauce, noodles, ricotta and mozzarella in a 9x13 dish, finishing with sauce and cheese."),
            StepDto(text = "Bake covered at 375°F for 25 minutes, then uncovered for 20 minutes more. Rest for 15 minutes before slicing."),
        ),
    )

    val mealPlan = listOf(
        MealPlanEntity(1, "2026-11-23", "breakfast", null, null, "r1", "banana-bread", "Banana bread"),
        MealPlanEntity(2, "2026-11-23", "dinner", null, null, "r3", "lasagna", "Classic lasagna"),
        MealPlanEntity(3, "2026-11-24", "dinner", null, null, "r2", "chicken-tacos", "Chicken tacos with lime crema"),
        MealPlanEntity(4, "2026-11-25", "lunch", "Leftover lasagna", "Pack for work", null, null, null),
        MealPlanEntity(5, "2026-11-25", "dinner", null, null, "r4", "miso-salmon", "Miso glazed salmon"),
    )

    val timers = listOf(
        CookTimer(1, "Step 4 · Bake covered at 375°F", 25 * 60_000L, endAt = NOW + 14 * 60_000L + 32_000L, recipeName = "Classic lasagna"),
        CookTimer(3, "Step 1 · Stir in the marinara and simmer", 15 * 60_000L, endAt = NOW + 6 * 60_000L + 5_000L, recipeName = "Classic lasagna"),
        CookTimer(2, "Step 2 · Boil the noodles", 10 * 60_000L, done = true, recipeName = "Classic lasagna"),
    )
}
