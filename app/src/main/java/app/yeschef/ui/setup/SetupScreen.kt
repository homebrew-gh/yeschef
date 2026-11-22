package app.yeschef.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yeschef.AppContainer
import app.yeschef.data.ServerConfig
import app.yeschef.data.sync.SyncScheduler
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

@Composable
fun SetupScreen(container: AppContainer) {
    var server by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun connect() {
        busy = true
        error = null
        scope.launch {
            try {
                val finalToken = if (usePassword) {
                    container.probeApi(server, null).login(username.trim(), password).accessToken
                } else {
                    token.trim()
                }
                container.probeApi(server, finalToken).self() // proves URL + token work
                container.saveConfig(ServerConfig(server.trim(), finalToken))
                SyncScheduler.requestSync(context)
            } catch (e: Exception) {
                error = describe(e)
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YesChef", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Connect to your Mealie server. Turn on your WireGuard VPN first so the phone can reach it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("Server address") },
                    placeholder = { Text("https://mealie.example.local") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = usePassword, onCheckedChange = { usePassword = it })
                    Text("  Sign in with username and password", style = MaterialTheme.typography.bodyMedium)
                }
                if (usePassword) {
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text("Username or email") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = token, onValueChange = { token = it },
                        label = { Text("API token") },
                        supportingText = { Text("Create one in Mealie under your profile > API Tokens.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = ::connect,
                    enabled = !busy && server.isNotBlank() && (if (usePassword) username.isNotBlank() && password.isNotBlank() else token.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Connect")
                }
            }
        }
    }
}

private fun describe(e: Exception): String = when (e) {
    is SSLException ->
        "The server's certificate isn't trusted. Install your StartOS Root CA on this device " +
            "(Settings > Security & privacy > More security settings > Encryption & credentials > Install a certificate > CA certificate)."
    is UnknownHostException -> "Can't find that server. Is the VPN connected and the address right?"
    is IOException -> "Can't reach the server (${e.message}). Is the VPN connected?"
    is HttpException -> when (e.code()) {
        401 -> "Mealie rejected those credentials."
        404 -> "That address doesn't look like Mealie. Check the port (Mealie's default is 9000)."
        else -> "Mealie returned HTTP ${e.code()}."
    }
    is IllegalArgumentException -> "That server address isn't valid."
    else -> e.message ?: e.toString()
}
