package app.yeschef.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.yeschef.data.ServerConfig
import app.yeschef.data.ThemeMode
import app.yeschef.ui.setup.describe
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
    val theme by container.themeMode.collectAsStateWithLifecycle()
    val shakeToRoll by container.settings.shakeToRoll.collectAsStateWithLifecycle(true)
    var editServer by rememberSaveable { mutableStateOf(false) }
    if (editServer) {
        config?.let { current -> ServerDialog(container, current) { editServer = false } }
    }

    Scaffold(topBar = { YcTopBar("Settings") }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Server")
            Card {
                Text(config?.serverUrl ?: "Not connected", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { editServer = true }, shape = CircleShape) { Text("Change server or token") }
            }

            SectionLabel("Appearance")
            Card {
                ThemePicker(theme) { mode -> scope.launch { container.setTheme(mode) } }
            }

            SectionLabel("Cookbook")
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Shake to roll the dice", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Shake on the Cookbook page to pick a chapter, then shake again to roll a random recipe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = shakeToRoll,
                        onCheckedChange = { on -> scope.launch { container.settings.saveShakeToRoll(on) } },
                    )
                }
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

/** Light / Dark / System, as one segmented control. */
@Composable
fun ThemePicker(mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.Light, "Light", Icons.Filled.LightMode),
        Triple(ThemeMode.Dark, "Dark", Icons.Filled.DarkMode),
        Triple(ThemeMode.System, "System", Icons.Filled.BrightnessAuto),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label, icon) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = { SegmentedButtonDefaults.Icon(mode == value) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) } },
                label = { Text(label) },
            )
        }
    }
}

/**
 * Change the server address and/or API token. The new details are tried against
 * Mealie before they're saved, so a typo can't lock the app out of a working server.
 */
@Composable
private fun ServerDialog(container: AppContainer, current: ServerConfig, onDone: () -> Unit) {
    var server by rememberSaveable { mutableStateOf(current.serverUrl) }
    var token by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text("Change server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = server, onValueChange = { server = it; error = null },
                    label = { Text("Mealie address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it; error = null },
                    label = { Text("New API token (optional)") },
                    placeholder = { Text("Leave blank to keep the current one") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && server.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val newConfig = ServerConfig(server.trim(), token.trim().ifEmpty { current.token })
                            container.probeApi(newConfig.serverUrl, newConfig.token).self() // proves it works
                            container.saveConfig(newConfig)
                            SyncScheduler.requestSync(context)
                            onDone()
                        } catch (e: Exception) {
                            error = describe(e)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Checking…" else "Test and save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDone) { Text("Cancel") } },
    )
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
