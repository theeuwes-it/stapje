package nl.theeuwesit.stapje

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.theeuwesit.stapje.data.Repository
import nl.theeuwesit.stapje.model.Settings
import nl.theeuwesit.stapje.reminder.ReminderController
import nl.theeuwesit.stapje.reminder.ReminderLogic
import java.time.ZonedDateTime

class AndroidReminders(
    private val context: Context,
    private val launchPermissionRequest: () -> Unit,
) : ReminderController {

    private val meta = context.getSharedPreferences("stapje_meta", Context.MODE_PRIVATE)
    private var granted by mutableStateOf(isGranted())

    override val permissionGranted: Boolean get() = granted
    override val canRequestPermission: Boolean = true
    override val permissionNote: String? = null
    override val runsOnlyWhileOpen: Boolean = false

    override fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !meta.getBoolean(KEY_ASKED, false)) {
            meta.edit().putBoolean(KEY_ASKED, true).apply()
            launchPermissionRequest()
        } else {
            // Eerder geweigerd (of Android < 13): open de meldingsinstellingen van de app.
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Vraag bij de eerste start één keer om toestemming (Android 13+). */
    fun requestOnFirstStart() {
        if (Build.VERSION.SDK_INT >= 33 && !granted && !meta.getBoolean(KEY_ASKED, false)) requestPermission()
    }

    override fun apply(settings: Settings) = ReminderScheduler.schedule(context, settings)

    override fun showNow(title: String, body: String) = Unit

    override fun refresh() {
        granted = isGranted()
    }

    override fun onTodayCompleted() {
        NotificationManagerCompat.from(context).cancel(Notifications.ID)
    }

    private fun isGranted() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private companion object {
        const val KEY_ASKED = "notification_permission_asked"
    }
}

object ReminderScheduler {
    private const val RC_DAILY = 1
    private const val RC_SNOOZE = 2
    const val SNOOZE_MINUTES = 60

    /** Plant de eerstvolgende dagelijkse herinnering. De receiver plant daarna de volgende. */
    fun schedule(context: Context, settings: Settings) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, ReminderReceiver.ACTION_DAILY, RC_DAILY)
        alarmManager.cancel(pending)
        if (!settings.reminderEnabled) return

        val now = ZonedDateTime.now()
        var next = now.withHour(settings.reminderHour).withMinute(settings.reminderMinute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }

    fun snooze(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L,
            pendingIntent(context, ReminderReceiver.ACTION_SNOOZED, RC_SNOOZE),
        )
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository(SharedPrefsStore(context))
        val settings = repo.loadSettings()
        when (intent.action) {
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(Notifications.ID)
                ReminderScheduler.snooze(context)
            }

            ACTION_DAILY, ACTION_SNOOZED -> {
                if (ReminderLogic.needsReminder(androidToday(), settings, repo.loadEntries())) {
                    Notifications.showReminder(context)
                }
                if (intent.action == ACTION_DAILY) ReminderScheduler.schedule(context, settings)
            }
        }
    }

    companion object {
        const val ACTION_DAILY = "nl.theeuwesit.stapje.REMINDER_DAILY"
        const val ACTION_SNOOZE = "nl.theeuwesit.stapje.REMINDER_SNOOZE"
        const val ACTION_SNOOZED = "nl.theeuwesit.stapje.REMINDER_SNOOZED"
    }
}

/** Plant de herinnering opnieuw na een herstart of app-update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.schedule(context, Repository(SharedPrefsStore(context)).loadSettings())
    }
}

object Notifications {
    const val ID = 1001
    private const val CHANNEL_ID = "dagelijkse_herinnering"

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dagelijkse herinnering",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Herinnering als je een werkdag nog niet hebt ingevuld" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val openToday = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ReminderLogic.TITLE)
            .setContentText(ReminderLogic.BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ReminderLogic.BODY))
            .setContentIntent(openToday)
            .setAutoCancel(true)
            .addAction(0, "Nu invullen", openToday)
            .addAction(0, "Over 1 uur", snooze)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID, notification)
    }
}
