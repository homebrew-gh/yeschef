package app.yeschef.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.yeschef.data.cook.ActiveCookSession
import app.yeschef.data.cook.CookNote
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ServerConfig(val serverUrl: String, val token: String)

/** Light, dark, or follow the phone's setting. */
enum class ThemeMode { System, Light, Dark }

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Server address and API token. Stored in app-private storage; the token is a
 * Mealie API token you can revoke from Mealie's user profile at any time.
 */
class SettingsStore(private val context: Context) {
    private val serverKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("api_token")
    private val themeKey = stringPreferencesKey("theme")
    private val dayListsKey = stringPreferencesKey("shopping_day_lists")
    private val dayAddToKey = stringPreferencesKey("shopping_day_add_to")
    private val diceKey = stringPreferencesKey("dice_chapter")
    private val lastListKey = stringPreferencesKey("last_shopping_list")
    private val activeCookKey = stringPreferencesKey("active_cook_session")

    /** The cookbook chapter the dice rolls from, last picked; null means any recipe. Mains until picked. */
    val diceChapter: Flow<String?> = context.dataStore.data.map { prefs ->
        when (val v = prefs[diceKey]) {
            null -> Cookbook.DICE_DEFAULT
            ANY_CHAPTER -> null
            else -> v
        }
    }

    suspend fun saveDiceChapter(chapter: String?) {
        context.dataStore.edit { it[diceKey] = chapter ?: ANY_CHAPTER }
    }

    /** The lists picked for Shopping Day, as Mealie list ids. Empty until picked. */
    val shoppingDayLists: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[dayListsKey]?.let(::parseRecipeIds).orEmpty()
    }

    /** The list that Shopping Day's add bar adds to, last picked. */
    val shoppingDayAddTo: Flow<String?> = context.dataStore.data.map { it[dayAddToKey] }

    suspend fun saveShoppingDayLists(ids: List<String>) {
        context.dataStore.edit { it[dayListsKey] = ids.joinToString(",") }
    }

    suspend fun saveShoppingDayAddTo(id: String) {
        context.dataStore.edit { it[dayAddToKey] = id }
    }

    /** The shopping list last open (a Mealie list id, or the Shopping Day view), so the screen reopens it. */
    val lastShoppingList: Flow<String?> = context.dataStore.data.map { it[lastListKey] }

    suspend fun saveLastShoppingList(id: String) {
        context.dataStore.edit { it[lastListKey] = id }
    }

    /** Active single-recipe cook session, preserved across tab switches and app restarts. */
    val activeCook: Flow<ActiveCookSession?> = context.dataStore.data.map { prefs ->
        prefs[activeCookKey]?.let { runCatching { Json.decodeFromString(ActiveCookSession.serializer(), it) }.getOrNull() }
    }

    suspend fun saveActiveCook(session: ActiveCookSession?) {
        context.dataStore.edit { prefs ->
            if (session == null) prefs.remove(activeCookKey)
            else prefs[activeCookKey] = Json.encodeToString(ActiveCookSession.serializer(), session)
        }
    }

    /** Cook-mode notes not yet saved to Mealie, per recipe id. Kept across app restarts. */
    fun cookNotes(recipeId: String): Flow<List<CookNote>> = context.dataStore.data.map { prefs ->
        prefs[cookNotesKey(recipeId)]?.let { runCatching { Json.decodeFromString(noteList, it) }.getOrNull() }.orEmpty()
    }

    /** Changes a recipe's unsaved cook notes in one step, so quick notes in a row all keep. */
    suspend fun updateCookNotes(recipeId: String, change: (List<CookNote>) -> List<CookNote>) {
        val key = cookNotesKey(recipeId)
        context.dataStore.edit { prefs ->
            val now = prefs[key]?.let { runCatching { Json.decodeFromString(noteList, it) }.getOrNull() }.orEmpty()
            val next = change(now)
            if (next.isEmpty()) prefs.remove(key) else prefs[key] = Json.encodeToString(noteList, next)
        }
    }

    private companion object { const val ANY_CHAPTER = "*" }

    private fun cookNotesKey(recipeId: String) = stringPreferencesKey("cook_notes_$recipeId")
    private val noteList = ListSerializer(CookNote.serializer())

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.System
    }

    suspend fun saveTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    val config: Flow<ServerConfig?> = context.dataStore.data.map { prefs ->
        val url = prefs[serverKey]
        val token = prefs[tokenKey]
        if (url.isNullOrBlank() || token.isNullOrBlank()) null else ServerConfig(url, token)
    }

    suspend fun save(config: ServerConfig) {
        context.dataStore.edit {
            it[serverKey] = config.serverUrl
            it[tokenKey] = config.token
        }
    }

    /** Forgets the server and token. The theme choice stays. */
    suspend fun clear() {
        context.dataStore.edit {
            it.remove(serverKey)
            it.remove(tokenKey)
            it.remove(activeCookKey)
        }
    }
}
