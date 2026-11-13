package nl.theeuwesit.stapje.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.Settings

/** Simpele sleutel/waarde-opslag; per platform geïmplementeerd. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class Repository(private val store: KeyValueStore) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val entryListSerializer = ListSerializer(DayEntry.serializer())

    fun loadEntries(): Map<String, DayEntry> {
        val raw = store.get(KEY_ENTRIES) ?: return emptyMap()
        return runCatching { json.decodeFromString(entryListSerializer, raw) }
            .getOrDefault(emptyList())
            .associateBy { it.date }
    }

    fun saveEntries(entries: Collection<DayEntry>) {
        store.put(KEY_ENTRIES, json.encodeToString(entryListSerializer, entries.sortedBy { it.date }))
    }

    fun loadSettings(): Settings {
        val raw = store.get(KEY_SETTINGS) ?: return Settings()
        return runCatching { json.decodeFromString(Settings.serializer(), raw) }.getOrDefault(Settings())
    }

    fun saveSettings(settings: Settings) {
        store.put(KEY_SETTINGS, json.encodeToString(Settings.serializer(), settings))
    }

    fun lastInAppReminderDate(): String? = store.get(KEY_LAST_IN_APP_REMINDER)

    fun setLastInAppReminderDate(date: String) = store.put(KEY_LAST_IN_APP_REMINDER, date)

    private companion object {
        const val KEY_ENTRIES = "entries_v1"
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_LAST_IN_APP_REMINDER = "last_in_app_reminder"
    }
}
