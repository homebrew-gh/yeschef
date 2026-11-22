package app.yeschef.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
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

    @GET("households/shopping/lists")
    suspend fun shoppingLists(@Query("perPage") perPage: Int = -1): Page<ShoppingListSummaryDto>

    @GET("households/shopping/lists/{id}")
    suspend fun shoppingList(@Path("id") id: String): ShoppingListDto

    @POST("households/shopping/items")
    suspend fun createShoppingItem(@Body item: NewShoppingItem): ResponseBody

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
}
