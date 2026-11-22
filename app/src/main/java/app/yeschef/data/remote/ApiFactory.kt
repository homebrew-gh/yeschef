package app.yeschef.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val MealieJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Normalises what the user typed into Mealie's API base, e.g. "https://mealie.local:9000/api/". */
fun apiBaseUrl(serverUrl: String): String {
    var url = serverUrl.trim().trimEnd('/')
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
    if (!url.endsWith("/api")) url = "$url/api"
    return "$url/"
}

/** The server root without /api, for media URLs. */
fun serverRoot(serverUrl: String): String = apiBaseUrl(serverUrl).removeSuffix("api/").trimEnd('/')

fun recipeImageUrl(serverUrl: String, recipeId: String, size: String = "min-original", version: String? = null): String =
    "${serverRoot(serverUrl)}/api/media/recipes/$recipeId/images/$size.webp" + (version?.let { "?v=$it" } ?: "")

object ApiFactory {
    fun httpClient(tokenProvider: () -> String?): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = tokenProvider()
                val request = if (token.isNullOrBlank()) chain.request() else
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                chain.proceed(request)
            }
            .build()

    fun create(serverUrl: String, client: OkHttpClient): MealieApi =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl(serverUrl))
            .client(client)
            .addConverterFactory(MealieJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MealieApi::class.java)
}
