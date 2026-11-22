package app.yeschef

import android.content.Context
import app.yeschef.data.Repository
import app.yeschef.data.ServerConfig
import app.yeschef.data.SettingsStore
import app.yeschef.data.cook.TimerManager
import app.yeschef.data.local.YesChefDb
import app.yeschef.data.remote.ApiFactory
import app.yeschef.data.remote.MealieApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Hand-rolled dependency wiring; the app is small enough not to need Hilt. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = YesChefDb.build(context)

    private val _config = MutableStateFlow(runBlocking { settings.config.first() })
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    val httpClient: OkHttpClient = ApiFactory.httpClient { _config.value?.token }

    @Volatile private var cachedApi: Pair<String, MealieApi>? = null

    fun api(): MealieApi? {
        val url = _config.value?.serverUrl ?: return null
        cachedApi?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        return ApiFactory.create(url, httpClient).also { cachedApi = url to it }
    }

    /** An API client for trying out a server before saving it. */
    fun probeApi(serverUrl: String, token: String?): MealieApi =
        ApiFactory.create(serverUrl, ApiFactory.httpClient { token })

    val repository = Repository(db, ::api)

    val timers = TimerManager(context)

    suspend fun saveConfig(config: ServerConfig) {
        settings.save(config)
        _config.value = config
    }

    suspend fun signOut() {
        settings.clear()
        _config.value = null
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
