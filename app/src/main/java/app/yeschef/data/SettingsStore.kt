package app.yeschef.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        }
    }
}
