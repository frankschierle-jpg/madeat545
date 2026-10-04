package de.madeat545.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val ACTION_WAKE = "de.madeat545.app.WAKE"
const val ACTION_TEST = "de.madeat545.app.TEST"
const val ACTION_LEAVE = "de.madeat545.app.LEAVE"
const val ACTION_STOP_ALARM = "de.madeat545.app.STOP_ALARM"
const val EXTRA_TEXT = "text"

object Notifications {
    const val CH_ALARM = "alarm"
    const val CH_REMINDER = "reminder"
    const val ID_ALARM = 545
    const val ID_LEAVE = 546

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Der 5:45-Wecker"
            setSound(null, null) // Ton spielt der AlarmService selbst
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val reminder = NotificationChannel(CH_REMINDER, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Losgehen, To-Dos und Termine"
        }
        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(reminder)
    }

    fun alarmNotification(ctx: Context): Notification {
        val open = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Guten Morgen! Es ist 5:45")
            .setContentText("Mach dein Bett und fotografier es, um den Wecker auszuschalten.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showReminder(ctx: Context, text: String) {
        val open = PendingIntent.getActivity(
            ctx, 11, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Made at 5:45")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_LEAVE, n)
        } catch (_: SecurityException) {
            // Benachrichtigungen nicht erlaubt
        }
    }
}

object AlarmScheduler {
    private const val REQ_WAKE = 1001
    private const val REQ_LEAVE = 1002
    private const val REQ_TEST = 1003

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am(ctx).canScheduleExactAlarms() else true

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun receiverIntent(ctx: Context, action: String, req: Int, text: String? = null): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        if (text != null) i.putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarmClock(ctx: Context, t: LocalDateTime, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            ctx, 12, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canExact(ctx)) {
                am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(millis(t), show), pi)
            } else {
                am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(t), pi)
            }
        } catch (_: SecurityException) {
            am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(t), pi)
        }
    }

    fun scheduleAll(ctx: Context) {
        scheduleWake(ctx)
        scheduleLeave(ctx)
    }

    fun scheduleWake(ctx: Context) {
        val pi = receiverIntent(ctx, ACTION_WAKE, REQ_WAKE)
        val t = Schedule.nextWake(LocalDateTime.now())
        if (t == null) {
            am(ctx).cancel(pi)
            return
        }
        setAlarmClock(ctx, t, pi)
    }

    fun scheduleTest(ctx: Context, seconds: Long = 60) {
        setAlarmClock(ctx, LocalDateTime.now().plusSeconds(seconds), receiverIntent(ctx, ACTION_TEST, REQ_TEST))
    }

    fun scheduleLeave(ctx: Context) {
        val next = Schedule.nextLeave(LocalDateTime.now().plusMinutes(1)) ?: return
        val pi = receiverIntent(ctx, ACTION_LEAVE, REQ_LEAVE, next.second)
        try {
            if (canExact(ctx)) {
                am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(next.first), pi)
            } else {
                am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(next.first), pi)
            }
        } catch (_: SecurityException) {
            am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(next.first), pi)
        }
    }

    fun nextWakeText(): String {
        val t = Schedule.nextWake(LocalDateTime.now()) ?: return "Kein Wecker geplant"
        val f = DateTimeFormatter.ofPattern("EEEE, d. MMMM 'um' HH:mm", Locale.GERMAN)
        return t.format(f)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WAKE, ACTION_TEST -> {
                try {
                    ContextCompat.startForegroundService(ctx, Intent(ctx, AlarmService::class.java))
                } catch (_: Exception) {
                    // z. B. ForegroundServiceStartNotAllowedException: wenigstens die Vollbild-Benachrichtigung zeigen
                    Notifications.createChannels(ctx)
                    try {
                        ctx.getSystemService(NotificationManager::class.java)
                            .notify(Notifications.ID_ALARM, Notifications.alarmNotification(ctx))
                    } catch (_: Exception) { }
                }
                if (intent.action == ACTION_WAKE) AlarmScheduler.scheduleWake(ctx)
            }
            ACTION_LEAVE -> {
                Notifications.showReminder(ctx, intent.getStringExtra(EXTRA_TEXT) ?: "Zeit zum Losgehen!")
                AlarmScheduler.scheduleLeave(ctx)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notifications.createChannels(ctx)
        AlarmScheduler.scheduleAll(ctx)
    }
}

/** Spielt den Weckton, bis das Bett-Foto gemacht wurde. */
class AlarmService : Service() {
    companion object {
        @Volatile var isRinging = false
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }
        Notifications.createChannels(this)
        isRinging = true // vor startForeground setzen, damit AlarmActivity den Status sofort sieht
        val notification = Notifications.alarmNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_ALARM, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifications.ID_ALARM, notification)
        }
        if (wakeLock == null) acquireWakeLock()
        if (player == null) startSound()
        if (vibrator == null) startVibration()
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "madeat545:alarm").apply {
            acquire(30 * 60 * 1000L)
        }
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        vibrator = getSystemService(Vibrator::class.java)
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        } catch (_: Exception) { }
    }

    private fun stopAlarm() {
        isRinging = false
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (isRinging) {
            isRinging = false
            try { player?.stop() } catch (_: Exception) { }
            player?.release()
            vibrator?.cancel()
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        super.onDestroy()
    }
}
