package app.yeschef

import android.content.Context
import app.yeschef.data.Repository
import app.yeschef.data.ServerConfig
import app.yeschef.data.ThemeMode
import app.yeschef.data.SettingsStore
import app.yeschef.data.cook.ActiveCookSession
import app.yeschef.data.cook.MealReminders
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
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency wiring; the app is small enough not to need Hilt. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val db = YesChefDb.build(context)

    private val _config = MutableStateFlow(runBlocking { settings.config.first() })
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    private val _theme = MutableStateFlow(runBlocking { settings.themeMode.first() })
    val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Currently active single-recipe cook session, surviving tab switches and restarts. */
    private val _activeCook = MutableStateFlow(runBlocking { settings.activeCook.first() })
    val activeCook: StateFlow<ActiveCookSession?> = _activeCook.asStateFlow()

    suspend fun setActiveCook(session: ActiveCookSession?) {
        _activeCook.value = session
        settings.saveActiveCook(session)
    }

    suspend fun updateActiveCook(change: (ActiveCookSession) -> ActiveCookSession) {
        val current = _activeCook.value ?: return
        val next = change(current)
        if (next == current) return
        _activeCook.value = next
        settings.saveActiveCook(next)
    }

    suspend fun setTheme(mode: ThemeMode) {
        settings.saveTheme(mode)
        _theme.value = mode
    }

    val httpClient: OkHttpClient = ApiFactory.httpClient { _config.value?.token }

    @Volatile private var cachedApi: Pair<String, MealieApi>? = null

    fun api(): MealieApi? {
        val url = _config.value?.serverUrl ?: return null
        cachedApi?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        return ApiFactory.create(url, httpClient).also { cachedApi = url to it }
    }

    @Volatile private var cachedImportApi: Pair<String, MealieApi>? = null

    /** Like [api], but waits minutes for a reply: Mealie answers an import only once the recipe is made. */
    fun importApi(): MealieApi? {
        val url = _config.value?.serverUrl ?: return null
        cachedImportApi?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        val slow = httpClient.newBuilder().readTimeout(6, TimeUnit.MINUTES).build()
        return ApiFactory.create(url, slow).also { cachedImportApi = url to it }
    }

    /** A link shared to YesChef from another app, waiting to be imported. */
    val sharedLink = MutableStateFlow<String?>(null)

    /** An API client for trying out a server before saving it. */
    fun probeApi(serverUrl: String, token: String?): MealieApi =
        ApiFactory.create(serverUrl, ApiFactory.httpClient { token })

    val repository = Repository(db, ::api, ::importApi)

    val timers = TimerManager(context)

    val mealReminders = MealReminders(context)

    suspend fun saveConfig(config: ServerConfig) {
        settings.save(config)
        _config.value = config
    }

    suspend fun signOut() {
        settings.clear()
        _config.value = null
        _activeCook.value = null
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
