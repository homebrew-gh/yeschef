package app.yeschef.data.cook

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.yeschef.MainActivity
import app.yeschef.R
import app.yeschef.data.remote.MealieJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

/**
 * Holds the meal being cooked and rings a reminder at each step worth one, with exact
 * alarms so they arrive on time with the app closed. Alarms are re-armed when the app
 * starts, including after a reboot (see BootReceiver).
 */
class MealReminders(private val context: Context) {
    private val prefs = context.getSharedPreferences("meal_session", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<MealSession?> = _session.asStateFlow()

    /** Set when a reminder is tapped, so the app opens the meal; cleared once it has. */
    private val _openRequested = MutableStateFlow(false)
    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()
    fun requestOpen() { _openRequested.value = true }
    fun openHandled() { _openRequested.value = false }

    init {
        createChannel()
        val s = _session.value
        val now = System.currentTimeMillis()
        if (s != null && now > s.readyAt + STALE_AFTER_MS) {
            end()
        } else {
            s?.reminders?.filter { it.at > now }?.forEach(::schedule)
        }
    }

    /** After a reboot: alarms were lost, so show any reminder that came due while the phone was off. */
    fun catchUpAfterBoot() {
        val now = System.currentTimeMillis()
        _session.value?.reminders
            ?.filter { it.at in (now - MISSED_GRACE_MS)..now }
            ?.maxByOrNull { it.at }
            ?.let(::show)
    }

    fun start(session: MealSession) {
        _session.value?.reminders?.forEach { cancel(it.id) }
        save(session)
        session.reminders.forEach(::schedule)
    }

    fun end() {
        _session.value?.reminders?.forEach { r ->
            cancel(r.id)
            notifications.cancel(notificationId(r.id))
        }
        save(null)
    }

    /** Called by the alarm. */
    fun fire(id: Int) {
        _session.value?.reminders?.firstOrNull { it.id == id }?.let(::show)
    }

    private fun save(s: MealSession?) {
        _session.value = s
        prefs.edit().apply {
            if (s == null) remove(KEY) else putString(KEY, MealieJson.encodeToString(s))
        }.apply()
    }

    private fun load(): MealSession? =
        prefs.getString(KEY, null)?.let { runCatching { MealieJson.decodeFromString<MealSession>(it) }.getOrNull() }

    private fun schedule(r: MealReminder) {
        val fire = fireIntent(r.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, fire)
        } else {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(r.at, openMealIntent()), fire)
        }
    }

    private fun cancel(id: Int) = alarms.cancel(fireIntent(id))

    private fun fireIntent(id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + id,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_MEAL_STEP).putExtra(TimerReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openMealIntent(): PendingIntent =
        PendingIntent.getActivity(
            context, REQUEST_BASE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_MEAL, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Meal step reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When to start the next step of a meal you're cooking"
                enableVibration(true)
            },
        )
    }

    @android.annotation.SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    private fun show(r: MealReminder) {
        if (!notifications.areNotificationsEnabled()) return
        val meal = _session.value?.title
        val style = NotificationCompat.InboxStyle().also { s -> r.lines.forEach(s::addLine) }
        if (meal != null) style.setSummaryText(meal)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(r.title)
            .setContentText(r.lines.joinToString(" · "))
            .setStyle(style)
            .setSubText(meal)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openMealIntent())
            .setAutoCancel(true)
            .build()
        notifications.notify(notificationId(r.id), n)
    }

    private fun notificationId(id: Int) = 5000 + id

    companion object {
        private const val KEY = "session"
        private const val CHANNEL = "meal_steps"
        private const val REQUEST_BASE = 50_000
        private const val MISSED_GRACE_MS = 2 * 3600_000L
        /** A meal is cleared on the next start this long after it was due. */
        private const val STALE_AFTER_MS = 6 * 3600_000L
    }
}
