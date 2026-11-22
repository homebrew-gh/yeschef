package app.yeschef.ui.cook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = null,
    )
}

/** Active timers, shown at the bottom of every screen while any exist. */
@Composable
fun TimerTray(timers: TimerManager, modifier: Modifier = Modifier) {
    val list by timers.timers.collectAsStateWithLifecycle()
    // A running or ringing timer also keeps the screen awake, on any screen.
    KeepScreenOn(list.any { it.running || it.done })
    if (list.isEmpty()) return
    val now by produceState(System.currentTimeMillis(), list) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    TimerTrayContent(
        list, now, modifier,
        onAddMinute = { timers.addMinute(it) },
        onPauseResume = { t -> if (t.running) timers.pause(t.id) else timers.resume(t.id) },
        onDismiss = { timers.dismiss(it) },
    )
}

/** Stateless timer tray, also used for screenshots. */
@Composable
fun TimerTrayContent(
    list: List<CookTimer>,
    now: Long,
    modifier: Modifier = Modifier,
    onAddMinute: (Int) -> Unit = {},
    onPauseResume: (CookTimer) -> Unit = {},
    onDismiss: (Int) -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.sortedWith(compareByDescending<CookTimer> { it.done }.thenBy { it.remainingMs(now) }).forEach { t ->
                TimerRow(t, now, onAddMinute, onPauseResume, onDismiss)
            }
        }
    }
}

@Composable
private fun TimerRow(
    t: CookTimer,
    now: Long,
    onAddMinute: (Int) -> Unit,
    onPauseResume: (CookTimer) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val progress = if (t.durationMs <= 0) 0f else 1f - t.remainingMs(now).toFloat() / t.durationMs
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (t.done) colors.tertiaryContainer else colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = { if (t.done) 1f else progress },
                    color = if (t.done) colors.tertiary else colors.primary,
                    trackColor = colors.primaryContainer,
                    strokeWidth = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(Icons.Filled.Timer, contentDescription = null, tint = if (t.done) colors.tertiary else colors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        t.done -> "Time's up!"
                        t.running -> formatClock((t.remainingMs(now) + 999) / 1000)
                        else -> formatClock(t.pausedRemainingMs / 1000)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        t.done -> colors.onTertiaryContainer
                        t.running -> colors.onSurface
                        else -> colors.outline
                    },
                )
            }
            TextButton(onClick = { onAddMinute(t.id) }) { Text("+1 min") }
            if (!t.done) {
                IconButton(onClick = { onPauseResume(t) }) {
                    Icon(if (t.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (t.running) "Pause" else "Resume")
                }
                IconButton(onClick = { onDismiss(t.id) }) { Icon(Icons.Filled.Close, "Cancel timer", tint = colors.outline) }
            } else {
                Button(
                    onClick = { onDismiss(t.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary),
                ) { Text("Stop") }
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
