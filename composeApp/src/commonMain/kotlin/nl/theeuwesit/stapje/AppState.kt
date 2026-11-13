package nl.theeuwesit.stapje

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.Repository
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.Settings
import nl.theeuwesit.stapje.reminder.ReminderController
import nl.theeuwesit.stapje.reminder.ReminderLogic

class AppState(
    private val repo: Repository,
    private val now: () -> LocalDateTime,
) {
    var entries by mutableStateOf(repo.loadEntries())
        private set

    var settings by mutableStateOf(repo.loadSettings())
        private set

    var today by mutableStateOf(now().date)
        private set

    fun refreshToday() {
        val d = now().date
        if (d != today) today = d
    }

    fun save(entry: DayEntry) {
        entries = entries + (entry.date to entry)
        repo.saveEntries(entries.values)
    }

    fun delete(date: LocalDate) {
        entries = entries - date.toString()
        repo.saveEntries(entries.values)
    }

    fun updateSettings(newSettings: Settings) {
        settings = newSettings
        repo.saveSettings(newSettings)
    }

    /** Laatste ingevulde dag vóór [date], om velden alvast voor te vullen. */
    fun lastEntryBefore(date: LocalDate): DayEntry? {
        val key = date.toString()
        return entries.values.filter { it.date < key }.maxByOrNull { it.date }
    }

    /** Voor web: melding tonen als het tijdstip voorbij is en vandaag nog leeg is. */
    fun maybeNotifyInApp(reminders: ReminderController) {
        if (!reminders.permissionGranted) return
        val current = now()
        val date = current.date
        if (!ReminderLogic.isPastReminderTime(current, settings)) return
        if (!ReminderLogic.needsReminder(date, settings, entries)) return
        if (repo.lastInAppReminderDate() == date.toString()) return
        reminders.showNow(ReminderLogic.TITLE, ReminderLogic.BODY)
        repo.setLastInAppReminderDate(date.toString())
    }
}
