package app.yeschef.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import app.yeschef.ui.SectionLabel
import app.yeschef.ui.YcTopBar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(container: AppContainer) {
    val config by container.config.collectAsStateWithLifecycle()
    val state by container.repository.syncState.collectAsStateWithLifecycle()
    val pending by remember { container.repository.pendingCount() }.collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { YcTopBar("Settings") }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Server")
            Card {
                Text(config?.serverUrl ?: "Not connected", style = MaterialTheme.typography.titleMedium)
            }

            SectionLabel("Sync")
            Card {
                Text(
                    when (val s = state) {
                        SyncState.Idle -> "Not synced yet this session."
                        SyncState.Syncing -> "Syncing…"
                        is SyncState.Synced -> "Last synced " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.at))
                        is SyncState.Offline -> "Offline: ${s.message}"
                        is SyncState.Failed -> s.message
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (pending == 0) "No unsent changes." else "$pending change(s) waiting to be sent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { SyncScheduler.requestSync(context) }, shape = CircleShape) { Text("Sync now") }
            }

            SectionLabel("Connecting over WireGuard")
            Card {
                Text(
                    "YesChef syncs whenever the VPN connects, when you open the app, and about every 30 minutes. " +
                        "Everything you've synced stays on the device, so the shopping list works in the store without signal; " +
                        "ticks and new items are sent next time the server is reachable.\n\n" +
                        "If the server uses HTTPS from StartOS, install your StartOS Root CA on this device: " +
                        "Settings > Security & privacy > More security settings > Encryption & credentials > Install a certificate > CA certificate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { scope.launch { container.signOut() } },
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            ) { Text("Sign out and clear local data") }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
