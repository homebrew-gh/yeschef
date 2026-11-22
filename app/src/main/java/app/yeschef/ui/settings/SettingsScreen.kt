package app.yeschef.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.SyncState
import app.yeschef.data.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val config by container.config.collectAsStateWithLifecycle()
    val state by container.repository.syncState.collectAsStateWithLifecycle()
    val pending by remember { container.repository.pendingCount() }.collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Text(config?.serverUrl ?: "Not connected", style = MaterialTheme.typography.bodyLarge)

            Text("Sync", style = MaterialTheme.typography.titleMedium)
            Text(
                when (val s = state) {
                    SyncState.Idle -> "Not synced yet this session."
                    SyncState.Syncing -> "Syncing…"
                    is SyncState.Synced -> "Last synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.at))
                    is SyncState.Offline -> "Offline: ${s.message}"
                    is SyncState.Failed -> s.message
                },
            )
            Text(if (pending == 0) "No unsent changes." else "$pending change(s) waiting to be sent.")
            Button(onClick = { SyncScheduler.requestSync(context) }) { Text("Sync now") }

            Text("Connecting over WireGuard", style = MaterialTheme.typography.titleMedium)
            Text(
                "YesChef syncs whenever the VPN connects, when you open the app, and about every 30 minutes. " +
                    "Everything you've synced stays on the device, so the shopping list works in the store without signal; " +
                    "ticks and new items are sent next time the server is reachable.\n\n" +
                    "If the server uses HTTPS from StartOS, install your StartOS Root CA on this device: " +
                    "Settings > Security & privacy > More security settings > Encryption & credentials > Install a certificate > CA certificate.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(onClick = { scope.launch { container.signOut() } }) { Text("Sign out and clear local data") }
        }
    }
}
