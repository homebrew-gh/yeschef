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
 * and an ongoing notification with a live countdown. Optional mid-timer checkpoints
 * chime cleanly without stopping the main timer, and range timers (e.g. 40–48 min)
 * let you extend through the remaining window with one tap when the lower bound hits.
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
                t.running -> {
                    schedule(t)
                    if (t.hasPendingCheckpoint && t.checkpointAt != null) {
                        if (t.checkpointAt <= now) fireCheckpoint(t.id) else scheduleCheckpoint(t)
                    }
                    showCountdown(t)
                }
                t.done -> showAlarm(t)
                else -> showCountdown(t)
            }
        }
    }

    @Synchronized
    fun start(
        label: String,
        seconds: Long,
        recipeSlug: String? = null,
        recipeName: String? = null,
        maxSeconds: Long? = null,
        checkpoint: TimerCheckpoint? = null,
    ): CookTimer {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        val now = System.currentTimeMillis()
        val ms = seconds * 1000
        val maxMs = maxSeconds?.takeIf { it > seconds }?.times(1000L)
        val validCp = checkpoint?.takeIf { it.atSeconds in 1 until seconds }
        val timer = CookTimer(
            id = id,
            label = label,
            durationMs = ms,
            endAt = now + ms,
            recipeSlug = recipeSlug,
            recipeName = recipeName,
            maxDurationMs = maxMs,
            checkpointLabel = validCp?.label,
            checkpointAt = validCp?.let { now + it.atSeconds * 1000L },
        )
        update { it + timer }
        schedule(timer)
        if (timer.checkpointAt != null) scheduleCheckpoint(timer)
        showCountdown(timer)
        return timer
    }

    fun pause(id: Int) = modify(id) { t ->
        if (!t.running) return@modify t
        val now = System.currentTimeMillis()
        cancelAlarm(t.id)
        cancelCheckpointAlarm(t.id)
        t.copy(
            endAt = null,
            pausedRemainingMs = t.remainingMs(now),
            checkpointAt = null,
            checkpointPausedRemainingMs = if (t.hasPendingCheckpoint) t.checkpointRemainingMs(now) else 0L,
        )
    }?.let(::showCountdown)

    fun resume(id: Int) = modify(id) { t ->
        if (t.running || t.done) return@modify t
        val now = System.currentTimeMillis()
        val cpAt = if (t.hasPendingCheckpoint && t.checkpointPausedRemainingMs > 0L) {
            now + t.checkpointPausedRemainingMs
        } else null
        t.copy(
            endAt = now + t.pausedRemainingMs,
            checkpointAt = cpAt,
            checkpointPausedRemainingMs = 0L,
        ).also {
            schedule(it)
            if (it.checkpointAt != null) scheduleCheckpoint(it)
        }
    }?.let(::showCountdown)

    /** Adds a minute; on a finished timer this silences it and runs one more minute. */
    fun addMinute(id: Int) = addSeconds(id, 60L)

    /** Extends a range timer through its remaining window up to [CookTimer.maxDurationMs]. */
    fun extendToMax(id: Int) {
        val current = _timers.value.firstOrNull { it.id == id } ?: return
        val extraSec = (current.remainingRangeMs / 1000L).coerceAtLeast(60L)
        addSeconds(id, extraSec)
    }

    /** Adds [extraSeconds]; on a finished timer this silences the alarm and resumes counting down. */
    fun addSeconds(id: Int, extraSeconds: Long) {
        val extraMs = extraSeconds.coerceAtLeast(1L) * 1000L
        modify(id) { t ->
            val now = System.currentTimeMillis()
            when {
                t.done -> t.copy(done = false, endAt = now + extraMs, durationMs = t.durationMs + extraMs)
                t.running -> t.copy(endAt = t.endAt!! + extraMs, durationMs = t.durationMs + extraMs)
                else -> t.copy(pausedRemainingMs = t.pausedRemainingMs + extraMs, durationMs = t.durationMs + extraMs)
            }.also { if (it.running) schedule(it) }
        }?.let(::showCountdown)
    }

    /** Cancels a running timer or stops a ringing one. */
    fun dismiss(id: Int) {
        cancelAlarm(id)
        cancelCheckpointAlarm(id)
        notifications.cancel(notificationId(id))
        notifications.cancel(checkpointNotificationId(id))
        update { list -> list.filterNot { it.id == id } }
    }

    /** Called by the main timer alarm. */
    fun fire(id: Int) {
        cancelCheckpointAlarm(id)
        modify(id) {
            it.copy(
                done = true,
                endAt = null,
                pausedRemainingMs = 0,
                checkpointAt = null,
                checkpointPausedRemainingMs = 0,
            )
        }?.let(::showAlarm)
    }

    /** Called by a mid-timer checkpoint alarm; chimes without stopping the main countdown. */
    fun fireCheckpoint(id: Int) {
        val updated = modify(id) {
            it.copy(checkpointFired = true, checkpointAt = null, checkpointPausedRemainingMs = 0)
        } ?: return
        showCheckpointAlert(updated)
        if (!updated.done) showCountdown(updated)
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

    private fun scheduleCheckpoint(t: CookTimer) {
        val at = t.checkpointAt ?: return
        val intent = receiverIntent(TimerReceiver.ACTION_CHECKPOINT, t.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun cancelAlarm(id: Int) = alarms.cancel(receiverIntent(TimerReceiver.ACTION_FIRE, id))
    private fun cancelCheckpointAlarm(id: Int) = alarms.cancel(receiverIntent(TimerReceiver.ACTION_CHECKPOINT, id))

    private fun receiverIntent(action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id * 31 + action.hashCode().mod(31),
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
            NotificationChannel(CHANNEL_CHECKPOINT, "Mid-timer reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chimes for mid-cook steps like covering with foil or flipping halfway"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
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
            .setSubText(t.subtitle)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (t.running) {
            builder.setWhen(t.endAt!!).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .addAction(0, "Pause", receiverIntent(TimerReceiver.ACTION_PAUSE, t.id))
            if (t.hasPendingCheckpoint) {
                builder.setContentText("Next alert: ${t.checkpointLabel}")
            }
        } else {
            builder.setContentText("Paused, ${formatClock(t.pausedRemainingMs / 1000)} left").setShowWhen(false)
                .addAction(0, "Resume", receiverIntent(TimerReceiver.ACTION_RESUME, t.id))
        }
        builder.addAction(0, "Cancel", receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
        post(notificationId(t.id), builder.build())
    }

    private fun showCheckpointAlert(t: CookTimer) {
        if (!notifications.areNotificationsEnabled()) return
        val cue = t.checkpointLabel ?: "Mid-cook check"
        val left = formatShort((t.remainingMs(System.currentTimeMillis()) + 999) / 1000)
        val n = NotificationCompat.Builder(context, CHANNEL_CHECKPOINT)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("$cue · ${t.label}")
            .setContentText(listOfNotNull(t.subtitle, "$left left on timer").joinToString(" · "))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        post(checkpointNotificationId(t.id), n)
    }

    private fun showAlarm(t: CookTimer) {
        if (!notifications.areNotificationsEnabled()) return
        val extraRangeSec = t.remainingRangeMs / 1000L
        val title = if (extraRangeSec >= 120L) "Check now: ${t.label}" else "Time's up: ${t.label}"
        val body = buildList {
            t.subtitle?.let(::add)
            if (extraRangeSec >= 120L) {
                add("${formatShort(t.durationMs / 1000)} reached · up to +${formatShort(extraRangeSec)}")
            } else {
                add("${formatShort(t.durationMs / 1000)} timer finished")
            }
        }.joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
            .setOngoing(true)
            .addAction(0, "Stop", receiverIntent(TimerReceiver.ACTION_DISMISS, t.id))
            .addAction(0, "+1 min", receiverIntent(TimerReceiver.ACTION_ADD_MINUTE, t.id))
        if (extraRangeSec >= 120L) {
            builder.addAction(0, "+${formatShort(extraRangeSec)}", receiverIntent(TimerReceiver.ACTION_ADD_RANGE, t.id))
        }
        val n = builder.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until stopped
        post(notificationId(t.id), n)
    }

    @android.annotation.SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    private fun post(notifId: Int, notification: Notification) = notifications.notify(notifId, notification)

    private fun notificationId(id: Int) = 1000 + id
    private fun checkpointNotificationId(id: Int) = 2000 + id

    companion object {
        private const val KEY_TIMERS = "timers"
        private const val KEY_NEXT_ID = "next_id"
        const val CHANNEL_RUNNING = "timers_running"
        const val CHANNEL_CHECKPOINT = "timers_checkpoint"
        const val CHANNEL_ALARM = "timers_alarm"
    }
}
