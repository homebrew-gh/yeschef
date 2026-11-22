package app.yeschef.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Mealie (v3.x) item models use camelCase JSON. The pagination wrapper does not:
// it is a plain pydantic model, so its fields are snake_case (per_page, total_pages).

@Serializable
data class Page<T>(
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val items: List<T> = emptyList(),
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class UserSelf(
    val id: String,
    val username: String? = null,
    val fullName: String? = null,
    val email: String? = null,
)

@Serializable
data class RecipeSummaryDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val performTime: String? = null,
    val recipeYield: String? = null,
    val rating: Double? = null,
    val updatedAt: String? = null,
)

@Serializable
data class RecipeDetailDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val performTime: String? = null,
    val recipeYield: String? = null,
    val recipeServings: Double? = null,
    val orgURL: String? = null,
    val recipeIngredient: List<IngredientDto> = emptyList(),
    val recipeInstructions: List<StepDto> = emptyList(),
)

@Serializable
data class IngredientDto(
    val quantity: Double? = null,
    val note: String? = null,
    val display: String? = null,
    val title: String? = null,
    val originalText: String? = null,
    val food: NamedDto? = null,
    val unit: NamedDto? = null,
)

@Serializable
data class NamedDto(val id: String? = null, val name: String? = null)

@Serializable
data class StepDto(
    val id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val text: String = "",
    /** Optional step time set in Mealie. */
    val durationSeconds: Int? = null,
)

@Serializable
data class LabelDto(val id: String, val name: String, val color: String? = null)

@Serializable
data class LabelSettingDto(
    val id: String? = null,
    val labelId: String,
    val position: Int = 0,
    val label: LabelDto? = null,
)

@Serializable
data class ShoppingListSummaryDto(
    val id: String,
    val name: String? = null,
    val labelSettings: List<LabelSettingDto> = emptyList(),
)

@Serializable
data class ShoppingListDto(
    val id: String,
    val name: String? = null,
    val labelSettings: List<LabelSettingDto> = emptyList(),
    val listItems: List<JsonObject> = emptyList(),
    val recipeReferences: List<ListRecipeRefDto> = emptyList(),
)

/** A recipe that was added to a shopping list. */
@Serializable
data class ListRecipeRefDto(
    val recipeId: String,
    val recipe: RecipeSummaryDto? = null,
)

/** Which recipe an item came from (an item merged from several recipes has several). */
@Serializable
data class ItemRecipeRefDto(val recipeId: String? = null)

/**
 * The fields of a shopping list item that the app reads. The full JSON object is
 * kept as well, because Mealie's update endpoint expects the whole item back and
 * we don't want to drop fields (recipe references, extras) we don't model.
 */
@Serializable
data class ShoppingItemDto(
    val id: String,
    val shoppingListId: String,
    val checked: Boolean = false,
    val position: Int = 0,
    val quantity: Double? = null,
    val note: String? = null,
    val display: String? = null,
    val food: FoodDto? = null,
    val label: LabelDto? = null,
    val recipeReferences: List<ItemRecipeRefDto> = emptyList(),
)

@Serializable
data class FoodDto(val id: String? = null, val name: String? = null, val label: LabelDto? = null)

@Serializable
data class NewShoppingItem(
    val shoppingListId: String,
    val note: String,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
    val position: Int = 0,
)

@Serializable
data class MealPlanEntryDto(
    val id: Int,
    val date: String,
    val entryType: String = "dinner",
    val title: String? = null,
    val text: String? = null,
    val recipeId: String? = null,
    val recipe: RecipeSummaryDto? = null,
)

@Serializable
data class UpdateImageResponse(val image: String? = null)
