package app.yeschef.ui.cook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.data.cook.CookTimer
import app.yeschef.data.cook.TimerManager
import app.yeschef.data.cook.formatClock
import app.yeschef.data.cook.formatShort
import kotlinx.coroutines.delay

/**
 * Returns a function that starts a timer, asking for the notification permission the
 * first time (Android 13+) so the alarm can ring when the app is in the background.
 */
@Composable
fun rememberTimerStarter(timers: TimerManager): (label: String, seconds: Long, slug: String?) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(timers) {
        { label: String, seconds: Long, slug: String? ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            timers.start(label, seconds, slug)
        }
    }
}

/** Chip under a recipe step, e.g. "⏱ 25 min". Tap to start. */
@Composable
fun TimerChip(text: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/** Active timers, shown at the bottom of every screen while any exist. */
@Composable
fun TimerTray(timers: TimerManager, modifier: Modifier = Modifier) {
    val list by timers.timers.collectAsStateWithLifecycle()
    if (list.isEmpty()) return
    val now by produceState(System.currentTimeMillis(), list) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        ) {
            list.sortedWith(compareByDescending<CookTimer> { it.done }.thenBy { it.remainingMs(now) }).forEach { t ->
                TimerRow(t, now, timers)
            }
        }
    }
}

@Composable
private fun TimerRow(t: CookTimer, now: Long, timers: TimerManager) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (t.done) colors.errorContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        t.done -> "Time's up!"
                        t.running -> formatClock((t.remainingMs(now) + 999) / 1000)
                        else -> formatClock(t.pausedRemainingMs / 1000) + "  paused"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (t.done) colors.onErrorContainer else colors.onSurface,
                )
            }
            TextButton(onClick = { timers.addMinute(t.id) }) { Text("+1 min") }
            if (!t.done) {
                IconButton(onClick = { if (t.running) timers.pause(t.id) else timers.resume(t.id) }) {
                    Icon(if (t.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (t.running) "Pause" else "Resume")
                }
                IconButton(onClick = { timers.dismiss(t.id) }) { Icon(Icons.Filled.Close, "Cancel timer") }
            } else {
                Button(onClick = { timers.dismiss(t.id) }) { Text("Stop") }
            }
        }
    }
}

/** Dialog for a timer that isn't in the recipe text. */
@Composable
fun CustomTimerDialog(defaultLabel: String, onDismiss: () -> Unit, onStart: (label: String, seconds: Long) -> Unit) {
    var minutes by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(defaultLabel) }
    val parsed = minutes.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it },
                    label = { Text("Minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (parsed != null) {
                        { Text(formatShort((parsed * 60).toLong())) }
                    } else {
                        null
                    },
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { onStart(label.ifBlank { "Timer" }, (parsed!! * 60).toLong()) }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
