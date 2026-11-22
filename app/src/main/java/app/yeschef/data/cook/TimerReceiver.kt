package app.yeschef.data.cook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yeschef.YesChefApp

/** Handles timer alarms and the buttons on timer notifications. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1).takeIf { it >= 0 } ?: return
        val timers = (context.applicationContext as YesChefApp).container.timers
        when (intent.action) {
            ACTION_FIRE -> timers.fire(id)
            ACTION_PAUSE -> timers.pause(id)
            ACTION_RESUME -> timers.resume(id)
            ACTION_ADD_MINUTE -> timers.addMinute(id)
            ACTION_DISMISS -> timers.dismiss(id)
        }
    }

    companion object {
        const val EXTRA_ID = "timer_id"
        const val ACTION_FIRE = "app.yeschef.timer.FIRE"
        const val ACTION_PAUSE = "app.yeschef.timer.PAUSE"
        const val ACTION_RESUME = "app.yeschef.timer.RESUME"
        const val ACTION_ADD_MINUTE = "app.yeschef.timer.ADD_MINUTE"
        const val ACTION_DISMISS = "app.yeschef.timer.DISMISS"
    }
}
