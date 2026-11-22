package app.yeschef

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.ui.YesChefRoot
import app.yeschef.ui.setup.SetupScreen
import app.yeschef.ui.theme.YesChefTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YesChefApp).container
        setContent {
            YesChefTheme {
                val config by container.config.collectAsStateWithLifecycle()
                if (config == null) SetupScreen(container) else YesChefRoot(container)
            }
        }
    }
}
