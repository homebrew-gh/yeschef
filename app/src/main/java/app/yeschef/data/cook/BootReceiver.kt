package app.yeschef.data.cook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yeschef.YesChefApp

/**
 * Alarms are wiped by a reboot. Starting the app's container re-arms cook timers and
 * meal reminders; anything that came due while the phone was off is shown now.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as YesChefApp).container
        container.timers // re-arms in its init
        container.mealReminders.catchUpAfterBoot()
    }
}
