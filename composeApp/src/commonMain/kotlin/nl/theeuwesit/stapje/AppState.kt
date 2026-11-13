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
import nl.theeuwesit.stapje.sync.nowMillis

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

    /** Wordt aangeroepen na elke lokale wijziging (start een synchronisatie). */
    var onLocalChange: () -> Unit = {}

    var syncPromptDismissed by mutableStateOf(repo.isSyncPromptDismissed())
        private set

    fun save(entry: DayEntry) {
        val stamped = entry.copy(updatedAt = nowMillis())
        entries = entries + (stamped.date to stamped)
        repo.saveEntries(entries.values)
        val tombstones = repo.loadTombstones()
        if (stamped.date in tombstones) repo.saveTombstones(tombstones - stamped.date)
        onLocalChange()
    }

    fun delete(date: LocalDate) {
        entries = entries - date.toString()
        repo.saveEntries(entries.values)
        repo.saveTombstones(repo.loadTombstones() + (date.toString() to nowMillis()))
        onLocalChange()
    }

    fun updateSettings(newSettings: Settings) {
        settings = newSettings.copy(updatedAt = nowMillis())
        repo.saveSettings(settings)
        onLocalChange()
    }

    /** Opnieuw inlezen na een synchronisatie. */
    fun reload() {
        entries = repo.loadEntries()
        settings = repo.loadSettings()
    }

    fun dismissSyncPrompt() {
        repo.dismissSyncPrompt()
        syncPromptDismissed = true
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
