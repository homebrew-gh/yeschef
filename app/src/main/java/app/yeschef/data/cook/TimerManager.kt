package app.yeschef.data.cook

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
 * Runs any number of cook timers. Each running timer has an exact alarm
 * (AlarmManager.setAlarmClock, which fires through Doze and with the app closed)
 * and an ongoing notification with a live countdown. When one finishes, an alarm
 * notification rings until you stop it.
 */
class TimerManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("cook_timers", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)

    private val _timers = MutableStateFlow(load())
    val timers: StateFlow<List<CookTimer>> = _timers.asStateFlow()

    init {
        createChannels()
        // Alarms don't survive a reboot or force-stop; catch up on anything overdue and re-arm the rest.
        val now = System.currentTimeMillis()
        _timers.value.forEach { t ->
            when {
                t.running && t.endAt!! <= now -> fire(t.id)
                t.running -> { schedule(t); showCountdown(t) }
                t.done -> showAlarm(t)
                else -> showCountdown(t)
            }
        }
    }

    @Synchronized
    fun start(label: String, seconds: Long, recipeSlug: String? = null): CookTimer {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        val ms = seconds * 1000
        val timer = CookTimer(id, label, ms, endAt = System.currentTimeMillis() + ms, recipeSlug = recipeSlug)
        update { it + timer }
        schedule(timer)
        showCountdown(timer)
        return timer
    }

    fun pause(id: Int) = modify(id) { t ->
        if (!t.running) return@modify t
        cancelAlarm(t.id)
        t.copy(endAt = null, pausedRemainingMs = t.remainingMs(System.currentTimeMillis()))
    }?.let(::showCountdown)

    fun resume(id: Int) = modify(id) { t ->
        if (t.running || t.done) return@modify t
        t.copy(endAt = System.currentTimeMillis() + t.pausedRemainingMs).also(::schedule)
    }?.let(::showCountdown)

    /** Adds a minute; on a finished timer this silences it and runs one more minute. */
    fun addMinute(id: Int) = modify(id) { t ->
        val now = System.currentTimeMillis()
        when {
            t.done -> t.copy(done = false, endAt = now + 60_000, durationMs = t.durationMs + 60_000)
            t.running -> t.copy(endAt = t.endAt!! + 60_000, durationMs = t.durationMs + 60_000)
            else -> t.copy(pausedRemainingMs = t.pausedRemainingMs + 60_000, durationMs = t.durationMs + 60_000)
        }.also { if (it.running) schedule(it) }
    }?.let(::showCountdown)

    /** Cancels a running timer or stops a ringing one. */
    fun dismiss(id: Int) {
        cancelAlarm(id)
        notifications.cancel(notificationId(id))
        update { list -> list.filterNot { it.id == id } }
    }

    /** Called by the alarm. */
    fun fire(id: Int) {
        modify(id) { it.copy(done = true, endAt = null, pausedRemainingMs = 0) }?.let(::showAlarm)
    }

    // ---- internals ----

    @Synchronized
    private fun modify(id: Int, change: (CookTimer) -> CookTimer): CookTimer? {
        var result: CookTimer? = null
        update { list -> list.map { if (it.id == id) change(it).also { c -> result = c } else it } }
        return result
    }

    @Synchronized
    private fun update(change: (List<CookTimer>) -> List<CookTimer>) {
        val next = change(_timers.value)
        _timers.value = next
        prefs.edit().putString(KEY_TIMERS, MealieJson.encodeToString(next)).apply()
    }

    private fun load(): List<CookTimer> =
        prefs.getString(KEY_TIMERS, null)
            ?.let { runCatching { MealieJson.decodeFromString<List<CookTimer>>(it) }.getOrNull() }
            .orEmpty()

    private fun schedule(t: CookTimer) {
        val endAt = t.endAt ?: return
        val fire = receiverIntent(TimerReceiver.ACTION_FIRE, t.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, fire) // may be a little late
        } else {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, openAppIntent()), fire)
        }
    }

    private fun cancelAlarm(id: Int) = alarms.cancel(receiverIntent(TimerReceiver.ACTION_FIRE, id))

    private fun receiverIntent(action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id * 10 + action.hashCode().mod(10),
            Intent(context, TimerReceiver::class.java).setAction(action).putExtra(TimerReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Countdown for cook timers"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Timer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a cook timer finishes"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
            },
        )
    }

    private fun showCountdown(t: CookTimer) {
        if (!notifications.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(t.label)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (t.running) {
            builder.setWhen(t.endAt!!).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .addAction(0, "Pause", receiverIntent(TimerReceiver.ACTION_PAUSE, t.id))
        } else {
            builder.setContentText("Paused, ${formatClock(t.pausedRemainingMs / 1000)} left").setShowWhen(false)
                .addAction(0, "Resume", receiverIntent(TimerReceiver.ACTION_RESUME, t.id))
        }
        builder.addAction(0, "Cancel", receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
        post(t.id, builder.build())
    }

    private fun showAlarm(t: CookTimer) {
        if (!notifications.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Time's up: ${t.label}")
            .setContentText(formatShort(t.durationMs / 1000) + " timer finished")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
            .setOngoing(true)
            .addAction(0, "Stop", receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
            .addAction(0, "+1 min", receiverIntent(TimerReceiver.ACTION_ADD_MINUTE, t.id))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until stopped
        post(t.id, n)
    }

    @android.annotation.SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    private fun post(id: Int, notification: Notification) = notifications.notify(notificationId(id), notification)

    private fun notificationId(id: Int) = 1000 + id

    companion object {
        private const val KEY_TIMERS = "timers"
        private const val KEY_NEXT_ID = "next_id"
        const val CHANNEL_RUNNING = "timers_running"
        const val CHANNEL_ALARM = "timers_alarm"
    }
}
