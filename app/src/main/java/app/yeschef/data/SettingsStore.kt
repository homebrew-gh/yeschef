package app.yeschef.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(val serverUrl: String, val token: String)

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Server address and API token. Stored in app-private storage; the token is a
 * Mealie API token you can revoke from Mealie's user profile at any time.
 */
class SettingsStore(private val context: Context) {
    private val serverKey = stringPreferencesKey("server_url")
    private val tokenKey = stringPreferencesKey("api_token")

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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
