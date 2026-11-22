package app.yeschef.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yeschef.AppContainer
import app.yeschef.data.SyncState
import app.yeschef.data.sync.SyncScheduler

/** Two panes side by side on the Pixel Tablet (and unfolded/landscape phones). */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 700

/** Top-bar action: shows sync state and queued edits; tap to sync now. */
@Composable
fun SyncAction(container: AppContainer) {
    val state by container.repository.syncState.collectAsStateWithLifecycle()
    val pending by remember { container.repository.pendingCount() }.collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (pending > 0) {
            Text("$pending unsent", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { SyncScheduler.requestSync(context) }) {
            when (state) {
                SyncState.Syncing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is SyncState.Offline -> Icon(Icons.Filled.CloudOff, "Offline. Tap to retry")
                is SyncState.Failed -> Icon(Icons.Filled.ErrorOutline, "Sync failed. Tap to retry", tint = MaterialTheme.colorScheme.error)
                is SyncState.Synced -> Icon(Icons.Filled.CloudDone, "Synced. Tap to sync again")
                SyncState.Idle -> Icon(Icons.Filled.Sync, "Sync now")
            }
        }
    }
}
