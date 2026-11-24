package app.yeschef

import app.yeschef.data.AiStatus
import app.yeschef.data.RecipeImport
import app.yeschef.data.remote.MealieJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeImportTest {
    private fun group(json: String) = MealieJson.parseToJsonElement(json).jsonObject

    @Test fun aiStatusFromGroup() {
        assertEquals(
            AiStatus(enabled = true, images = true),
            AiStatus.from(group("""{"id":"g","aiProviderSettings":{"aiEnabled":true,"imageProviderEnabled":true,"providers":[]}}""")),
        )
        assertEquals(
            AiStatus(enabled = true, images = false),
            AiStatus.from(group("""{"aiProviderSettings":{"aiEnabled":true,"imageProviderEnabled":false}}""")),
        )
        // Images need AI on, whatever the image flag says.
        assertEquals(AiStatus.Off, AiStatus.from(group("""{"aiProviderSettings":{"aiEnabled":false,"imageProviderEnabled":true}}""")))
        assertEquals(AiStatus.Off, AiStatus.from(group("""{"aiProviderSettings":null}""")))
        assertEquals(AiStatus.Off, AiStatus.from(group("""{"name":"Home"}""")))
    }

    @Test fun firstUrlFromSharedText() {
        assertEquals("https://example.com/chili", RecipeImport.firstUrl("https://example.com/chili"))
        assertEquals(
            "https://www.seriouseats.com/the-best-chili-recipe?utm_source=share",
            RecipeImport.firstUrl("Try this! https://www.seriouseats.com/the-best-chili-recipe?utm_source=share."),
        )
        assertEquals("http://a.co/x", RecipeImport.firstUrl("Chili (http://a.co/x)"))
        assertNull(RecipeImport.firstUrl("no link here"))
        assertNull(RecipeImport.firstUrl(null))
    }

    @Test fun slugFromCreateReply() {
        assertEquals("white-chicken-chili", RecipeImport.slugOf("\"white-chicken-chili\"\n"))
        assertEquals("plain", RecipeImport.slugOf("plain"))
    }

    @Test fun mealieErrorMessages() {
        assertEquals("BAD_RECIPE_DATA", RecipeImport.errorMessage("""{"detail":{"message":"BAD_RECIPE_DATA","error":true,"exception":null}}"""))
        assertEquals("Not authenticated", RecipeImport.errorMessage("""{"detail":"Not authenticated"}"""))
        assertNull(RecipeImport.errorMessage("<html>Bad gateway</html>"))
        assertNull(RecipeImport.errorMessage(null))
        assertEquals("Mealie couldn't find a recipe on that page.", RecipeImport.friendlyError("NO_RECIPE_DATA", fromPhotos = false))
        assertEquals("AI is not enabled", RecipeImport.friendlyError("AI is not enabled", fromPhotos = true))
        assertEquals("Mealie couldn't make a recipe from those photos.", RecipeImport.friendlyError(null, fromPhotos = true))
    }
}
