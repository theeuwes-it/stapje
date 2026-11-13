package nl.theeuwesit.stapje.reminder

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.Settings

/** Platform-specifieke kant van herinneringen (Android: AlarmManager, web: browsermeldingen). */
interface ReminderController {
    /** Mag de app meldingen tonen? (State-backed, zodat Compose ververst.) */
    val permissionGranted: Boolean

    /** Kan de app (nog) om toestemming vragen? */
    val canRequestPermission: Boolean

    /** Uitleg voor de gebruiker als meldingen niet kunnen, of null. */
    val permissionNote: String?

    /** True op web: herinneringen werken alleen zolang de pagina open is. */
    val runsOnlyWhileOpen: Boolean

    fun requestPermission()

    /** Plan (of annuleer) de dagelijkse herinnering volgens de instellingen. */
    fun apply(settings: Settings)

    /** Toon direct een melding (alleen gebruikt als [runsOnlyWhileOpen]). */
    fun showNow(title: String, body: String)

    /** Toestemmingsstatus opnieuw uitlezen. */
    fun refresh()

    /** Vandaag is ingevuld: eventuele openstaande melding weghalen. */
    fun onTodayCompleted() {}
}

object ReminderLogic {
    const val TITLE = "Hoe was je werkdag?"
    const val BODY = "Je hebt vandaag nog niet ingevuld. Het kost minder dan een minuut."

    fun isWorkday(date: LocalDate, settings: Settings): Boolean =
        date.dayOfWeek.isoDayNumber in settings.workdays

    fun needsReminder(date: LocalDate, settings: Settings, entries: Map<String, DayEntry>): Boolean =
        settings.reminderEnabled && isWorkday(date, settings) && !entries.containsKey(date.toString())

    fun isPastReminderTime(now: LocalDateTime, settings: Settings): Boolean =
        now.hour * 60 + now.minute >= settings.reminderHour * 60 + settings.reminderMinute
}
