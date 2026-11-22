package app.yeschef.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import app.yeschef.ui.HatLogo
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

    SetupForm(
        server = server, onServer = { server = it },
        token = token, onToken = { token = it },
        usePassword = usePassword, onUsePassword = { usePassword = it },
        username = username, onUsername = { username = it },
        password = password, onPassword = { password = it },
        busy = busy, error = error, onConnect = ::connect,
    )
}

/** Stateless setup form, also used for screenshots. */
@Composable
fun SetupForm(
    server: String, onServer: (String) -> Unit,
    token: String, onToken: (String) -> Unit,
    usePassword: Boolean, onUsePassword: (Boolean) -> Unit,
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?, onConnect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(colors.background, colors.primaryContainer.copy(alpha = 0.6f), colors.background)),
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            HatLogo(132.dp)
            Text("YesChef", style = MaterialTheme.typography.displayMedium, color = colors.onBackground)
            Text(
                "Your Mealie kitchen, in your pocket",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.tertiary,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Turn on your WireGuard VPN, then connect to your Mealie server.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = server, onValueChange = onServer,
                        label = { Text("Server address") },
                        placeholder = { Text("https://mealie.example.local") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (usePassword) {
                        OutlinedTextField(
                            value = username, onValueChange = onUsername,
                            label = { Text("Username or email") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password, onValueChange = onPassword,
                            label = { Text("Password") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = token, onValueChange = onToken,
                            label = { Text("API token") },
                            supportingText = { Text("Create one in Mealie under your profile > API Tokens.") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = usePassword, onCheckedChange = onUsePassword)
                        Spacer(Modifier.width(12.dp))
                        Text("Use username and password instead", style = MaterialTheme.typography.bodyMedium)
                    }
                    error?.let {
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer) {
                            Text(it, Modifier.padding(12.dp), color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = onConnect,
                        enabled = !busy && server.isNotBlank() &&
                            (if (usePassword) username.isNotBlank() && password.isNotBlank() else token.isNotBlank()),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp), color = colors.onPrimary)
                        else Text("Connect", style = MaterialTheme.typography.titleMedium)
                    }
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
