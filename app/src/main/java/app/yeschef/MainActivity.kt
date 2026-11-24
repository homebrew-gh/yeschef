package app.yeschef

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import app.yeschef.data.ThemeMode
import app.yeschef.data.RecipeImport
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.ui.YesChefRoot
import app.yeschef.ui.setup.SetupScreen
import app.yeschef.ui.theme.YesChefTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YesChefApp).container
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by container.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep the status bar icons readable when the app's theme differs from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            YesChefTheme(darkTheme = dark) {
                val config by container.config.collectAsStateWithLifecycle()
                if (config == null) SetupScreen(container) else YesChefRoot(container)
            }
        }
    }

    companion object {
        /** Set on a meal reminder's tap: open the meal being cooked. */
        const val EXTRA_OPEN_MEAL = "open_meal"
    }
}

private fun MainActivity.handleIntent(intent: Intent?) {
    val container = (application as YesChefApp).container
    if (intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_MEAL, false) == true) {
        container.mealReminders.requestOpen()
    }
    // "Share" a recipe page from the browser or another app: offer to import it.
    if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
        RecipeImport.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { container.sharedLink.value = it }
    }
}

// The same scrims Android's own enableEdgeToEdge() uses for 3-button navigation.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
