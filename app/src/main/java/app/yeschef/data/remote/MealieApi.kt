package app.yeschef.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The parts of Mealie's REST API (v3.x, base path /api/) that YesChef uses. */
interface MealieApi {

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("remember_me") rememberMe: Boolean = true,
    ): TokenResponse

    @GET("users/self")
    suspend fun self(): UserSelf

    // perPage=-1 returns every row. Fine for one household's recipe box.
    @GET("recipes")
    suspend fun recipes(
        @Query("perPage") perPage: Int = -1,
        @Query("orderBy") orderBy: String = "name",
        @Query("orderDirection") orderDirection: String = "asc",
    ): Page<RecipeSummaryDto>

    @GET("recipes/{slug}")
    suspend fun recipe(@Path("slug") slug: String): RecipeDetailDto

    /** The recipe exactly as Mealie returns it (by slug or id), so an edit can keep what it doesn't touch. */
    @GET("recipes/{slugOrId}")
    suspend fun recipeRaw(@Path("slugOrId") slugOrId: String): JsonObject

    /** Partial recipe update by slug or id; returns the saved recipe (its slug follows its name). */
    @PATCH("recipes/{slugOrId}")
    suspend fun patchRecipeJson(@Path("slugOrId") slugOrId: String, @Body body: JsonObject): JsonObject

    /** Mealie's ingredient parser: quantity, unit, food and note from each line of text. */
    @POST("parser/ingredients")
    suspend fun parseIngredients(@Body body: ParseIngredientsBody): List<JsonObject>

    @GET("foods")
    suspend fun foods(@Query("perPage") perPage: Int = -1): Page<JsonObject>

    @POST("foods")
    suspend fun createFood(@Body body: JsonObject): JsonObject

    @GET("units")
    suspend fun units(@Query("perPage") perPage: Int = -1): Page<JsonObject>

    @POST("units")
    suspend fun createUnit(@Body body: JsonObject): JsonObject

    @GET("organizers/categories")
    suspend fun categories(@Query("perPage") perPage: Int = -1): Page<OrganizerDto>

    @GET("organizers/tags")
    suspend fun tags(@Query("perPage") perPage: Int = -1): Page<OrganizerDto>

    @POST("organizers/categories")
    suspend fun createCategory(@Body body: JsonObject): OrganizerDto

    @POST("organizers/tags")
    suspend fun createTag(@Body body: JsonObject): OrganizerDto

    /** Partial recipe update; used to set its categories and tags. */
    @PATCH("recipes/{slug}")
    suspend fun patchRecipe(@Path("slug") slug: String, @Body body: JsonObject): ResponseBody

    /** Records that the recipe was cooked ("I made this" in Mealie). */
    @PATCH("recipes/{slug}/last-made")
    suspend fun setLastMade(@Path("slug") slug: String, @Body body: LastMadeBody): ResponseBody

    /** Replaces a recipe's photo. Mealie makes the smaller sizes itself. */
    @Multipart
    @PUT("recipes/{slug}/image")
    suspend fun uploadRecipeImage(
        @Path("slug") slug: String,
        @Part image: MultipartBody.Part,
        @Part("extension") extension: RequestBody,
    ): UpdateImageResponse

    /** The user's group; its aiProviderSettings say whether Mealie has AI (and AI for images) set up. */
    @GET("groups/self")
    suspend fun groupSelf(): JsonObject

    /** Scrapes a recipe page into a new recipe; returns its slug as a JSON string. */
    @POST("recipes/create/url")
    suspend fun createRecipeFromUrl(@Body body: JsonObject): ResponseBody

    /**
     * Makes a new recipe with the group's AI from a URL and/or photos (form fields "url",
     * "images"); returns its slug as a JSON string. Mealie 3.31+.
     */
    @Multipart
    @POST("recipes/create/ai")
    suspend fun createRecipeWithAi(@Part parts: List<MultipartBody.Part>): ResponseBody

    @GET("households/shopping/lists")
    suspend fun shoppingLists(@Query("perPage") perPage: Int = -1): Page<ShoppingListSummaryDto>

    @GET("households/shopping/lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto

    @POST("households/shopping/items")
    suspend fun createShoppingItem(@Body item: NewShoppingItem): ResponseBody

    @DELETE("households/shopping/items/{id}")
    suspend fun deleteShoppingItem(@Path("id") id: String): ResponseBody

    @POST("households/shopping/lists")
    suspend fun createShoppingList(@Body body: JsonObject): JsonObject

    /** Adds recipes' ingredients to a list, scaled, merging with what's there. */
    @POST("households/shopping/lists/{id}/recipe")
    suspend fun addRecipesToList(@Path("id") listId: String, @Body body: JsonArray): ResponseBody

    /** The list exactly as Mealie returns it, so a rename can send it back whole. */
    @GET("households/shopping/lists/{id}")
    suspend fun shoppingListRaw(@Path("id") id: String): JsonObject

    @PUT("households/shopping/lists/{id}")
    suspend fun updateShoppingList(@Path("id") id: String, @Body body: JsonObject): ResponseBody

    @DELETE("households/shopping/lists/{id}")
    suspend fun deleteShoppingList(@Path("id") id: String): ResponseBody

    @PUT("households/shopping/items/{id}")
    suspend fun updateShoppingItem(@Path("id") id: String, @Body item: JsonObject): ResponseBody

    @GET("households/mealplans")
    suspend fun mealPlans(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("perPage") perPage: Int = -1,
        @Query("orderBy") orderBy: String = "date",
        @Query("orderDirection") orderDirection: String = "asc",
    ): Page<MealPlanEntryDto>

    @GET("households/mealplans/{id}")
    suspend fun mealPlanRaw(@Path("id") id: Int): JsonObject

    @PUT("households/mealplans/{id}")
    suspend fun updateMealPlan(@Path("id") id: Int, @Body body: JsonObject): ResponseBody

    @POST("households/mealplans")
    suspend fun createMealPlan(@Body body: JsonObject): ResponseBody

    @DELETE("households/mealplans/{id}")
    suspend fun deleteMealPlan(@Path("id") id: Int): ResponseBody
}
