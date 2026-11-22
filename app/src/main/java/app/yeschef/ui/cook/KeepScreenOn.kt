package app.yeschef.ui.cook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the display on while [enabled] and this composable is on screen.
 *
 * Uses the activity window's FLAG_KEEP_SCREEN_ON rather than View.keepScreenOn, and
 * counts holders so one screen leaving (or a navigation animation disposing the old
 * copy of a screen) can't switch the screen-on request off for another that still wants it.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        if (enabled) ScreenOnHolds.acquire(activity)
        onDispose { if (enabled) ScreenOnHolds.release(activity) }
    }
}

private object ScreenOnHolds {
    private var count = 0

    fun acquire(activity: Activity) {
        count++
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun release(activity: Activity) {
        count = (count - 1).coerceAtLeast(0)
        if (count == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
