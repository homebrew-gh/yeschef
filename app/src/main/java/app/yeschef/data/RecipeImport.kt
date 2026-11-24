package app.yeschef.data

import app.yeschef.data.remote.MealieJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** What Mealie's AI can do for this group, from its AI provider settings. */
data class AiStatus(val enabled: Boolean, val images: Boolean) {
    companion object {
        val Off = AiStatus(enabled = false, images = false)

        /** Reads `aiProviderSettings` from GET /groups/self (Mealie 3.31+); missing means off. */
        fun from(group: JsonObject): AiStatus {
            val settings = (group["aiProviderSettings"] ?: group["ai_provider_settings"]) as? JsonObject ?: return Off
            fun flag(camel: String, snake: String) =
                ((settings[camel] ?: settings[snake]) as? JsonPrimitive)?.booleanOrNull == true
            val enabled = flag("aiEnabled", "ai_enabled")
            return AiStatus(enabled, enabled && flag("imageProviderEnabled", "image_provider_enabled"))
        }
    }
}

enum class ImportStage { ReadingPage, AiReadingPage, AiReadingPhotos }

object RecipeImport {
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** The first web link in shared text ("Look at this! https://…"), without trailing punctuation. */
    fun firstUrl(text: String?): String? =
        text?.let { urlPattern.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }

    /** Body for POST /recipes/create/url. Tags and categories stay ours, so the site's aren't copied in. */
    fun urlBody(url: String): JsonObject = JsonObject(
        mapOf(
            "url" to JsonPrimitive(url.trim()),
            "includeTags" to JsonPrimitive(false),
            "includeCategories" to JsonPrimitive(false),
        ),
    )

    /** Mealie answers a create with the new slug as a JSON string. */
    fun slugOf(body: String): String {
        val text = body.trim()
        val slug = if (text.startsWith("\"")) MealieJson.decodeFromString<String>(text) else text
        require(slug.isNotBlank()) { "Mealie didn't say which recipe it made" }
        return slug
    }

    /** The message in one of Mealie's error replies: {"detail": {"message": "…"}}. */
    fun errorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val detail = runCatching { MealieJson.parseToJsonElement(body).jsonObject["detail"] }.getOrNull() ?: return null
        val message = when (detail) {
            is JsonPrimitive -> detail.contentOrNull
            is JsonObject -> (detail["message"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
        return message?.takeIf { it.isNotBlank() }
    }

    /** Words for the person, from Mealie's message (which is a code for scraper failures). */
    fun friendlyError(message: String?, fromPhotos: Boolean): String = when (message) {
        "BAD_RECIPE_DATA", "NO_RECIPE_DATA" -> "Mealie couldn't find a recipe on that page."
        "CONNECTION_ERROR" -> "Mealie couldn't open that page. Check the link."
        null -> if (fromPhotos) "Mealie couldn't make a recipe from those photos." else "Mealie couldn't import that page."
        else -> message
    }
}
