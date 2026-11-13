package nl.theeuwesit.stapje.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
    private val tombstoneSerializer = MapSerializer(String.serializer(), Long.serializer())

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

    /** Verwijderde dagen: datum -> tijdstip van verwijderen (ms). */
    fun loadTombstones(): Map<String, Long> {
        val raw = store.get(KEY_TOMBSTONES) ?: return emptyMap()
        return runCatching { json.decodeFromString(tombstoneSerializer, raw) }.getOrDefault(emptyMap())
    }

    fun saveTombstones(tombstones: Map<String, Long>) {
        store.put(KEY_TOMBSTONES, json.encodeToString(tombstoneSerializer, tombstones))
    }

    fun isSyncPromptDismissed(): Boolean = store.get(KEY_SYNC_PROMPT_DISMISSED) == "1"

    fun dismissSyncPrompt() = store.put(KEY_SYNC_PROMPT_DISMISSED, "1")

    fun lastInAppReminderDate(): String? = store.get(KEY_LAST_IN_APP_REMINDER)

    fun setLastInAppReminderDate(date: String) = store.put(KEY_LAST_IN_APP_REMINDER, date)

    private companion object {
        const val KEY_ENTRIES = "entries_v1"
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_LAST_IN_APP_REMINDER = "last_in_app_reminder"
        const val KEY_TOMBSTONES = "tombstones_v1"
        const val KEY_SYNC_PROMPT_DISMISSED = "sync_prompt_dismissed"
    }
}
