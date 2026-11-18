package nl.theeuwesit.stapje.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.theeuwesit.stapje.data.Repository
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.DayPart
import nl.theeuwesit.stapje.model.Settings

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Eén gedeelde Supabase-client per proces. */
object Cloud {
    val isConfigured: Boolean = SupabaseConfig.URL.isNotBlank() && SupabaseConfig.PUBLISHABLE_KEY.isNotBlank()

    var client: SupabaseClient? = null
        private set

    /** Eén keer aanroepen bij het starten; [platformAuth] zet platform-specifieke opties (bijv. deeplink). */
    fun init(platformAuth: AuthConfig.() -> Unit = {}): SupabaseClient? {
        if (!isConfigured) return null
        client?.let { return it }
        return createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY) {
            // encodeDefaults: altijd alle kolommen meesturen, zodat een upsert niets oud laat staan.
            defaultSerializer = KotlinXSerializer(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
            install(Auth) {
                flowType = FlowType.PKCE
                platformAuth()
            }
            install(Postgrest)
        }.also { client = it }
    }
}

@Serializable
data class EntryRow(
    val date: String,
    @SerialName("day_part") val dayPart: DayPart? = null,
    val hours: Double? = null,
    val activities: List<String> = emptyList(),
    @SerialName("lab_work") val labWork: Boolean = false,
    @SerialName("lab_hours") val labHours: Double = 0.0,
    @SerialName("lab_description") val labDescription: String = "",
    val note: String = "",
    @SerialName("pain_overall") val painOverall: Int? = null,
    @SerialName("back_pain") val backPain: Int? = null,
    val energy: Int? = null,
    val mood: Int? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class SettingsRow(
    val data: Settings,
    @SerialName("updated_at_ms") val updatedAtMs: Long,
)

private fun DayEntry.toRow() = EntryRow(
    date = date,
    dayPart = dayPart,
    hours = hours,
    activities = activities,
    labWork = labWork,
    labHours = labHours,
    labDescription = labDescription,
    note = note,
    painOverall = painOverall,
    backPain = backPain,
    energy = energy,
    mood = mood,
    updatedAtMs = updatedAt,
    deleted = false,
)

private fun EntryRow.toEntry(): DayEntry? {
    return DayEntry(
        date = date,
        dayPart = dayPart ?: return null,
        hours = hours ?: 0.0,
        activities = activities,
        labWork = labWork,
        labHours = labHours,
        labDescription = labDescription,
        note = note,
        painOverall = painOverall ?: return null,
        backPain = backPain ?: return null,
        energy = energy ?: return null,
        mood = mood ?: return null,
        updatedAt = updatedAtMs,
    )
}

/**
 * Tweerichtingssynchronisatie: per dag wint de nieuwste wijziging (op basis van updatedAt).
 * De dataset is klein (één rij per dag), dus we halen telkens alles op.
 */
object CloudSync {
    private const val ENTRIES = "day_entries"
    private const val SETTINGS = "user_settings"
    private const val ENTRY_COLUMNS =
        "date,day_part,hours,activities,lab_work,lab_hours,lab_description,note," +
            "pain_overall,back_pain,energy,mood,updated_at_ms,deleted"

    private fun localRow(date: String, entries: Map<String, DayEntry>, tombstones: Map<String, Long>): EntryRow? {
        val entry = entries[date]
        val deletedAt = tombstones[date]
        return when {
            entry != null && (deletedAt == null || entry.updatedAt >= deletedAt) -> entry.toRow()
            deletedAt != null -> EntryRow(date = date, updatedAtMs = deletedAt, deleted = true)
            else -> null
        }
    }

    private fun localTime(date: String, entries: Map<String, DayEntry>, tombstones: Map<String, Long>): Long =
        maxOf(entries[date]?.updatedAt ?: -1L, tombstones[date] ?: -1L)

    /** @return true als er lokaal iets veranderd is. Gooit een exception bij netwerk-/serverfouten. */
    suspend fun sync(client: SupabaseClient, repo: Repository): Boolean {
        // 1. Momentopname van lokaal en ophalen van de server.
        val entries = repo.loadEntries()
        val tombstones = repo.loadTombstones()
        val settings = repo.loadSettings()

        val remoteRows = client.from(ENTRIES).select(Columns.raw(ENTRY_COLUMNS)).decodeList<EntryRow>()
        val remoteByDate = remoteRows.associateBy { it.date }
        val remoteSettings = client.from(SETTINGS).select(Columns.raw("data,updated_at_ms"))
            .decodeList<SettingsRow>().firstOrNull()

        // 2. Lokale wijzigingen die nieuwer zijn dan de server: uploaden.
        val uploads = (entries.keys + tombstones.keys).mapNotNull { date ->
            val local = localRow(date, entries, tombstones) ?: return@mapNotNull null
            val remote = remoteByDate[date]
            local.takeIf { remote == null || it.updatedAtMs > remote.updatedAtMs }
        }
        if (uploads.isNotEmpty()) {
            client.from(ENTRIES).upsert(uploads) { onConflict = "user_id,date" }
        }
        if (remoteSettings == null || settings.updatedAt > remoteSettings.updatedAtMs) {
            client.from(SETTINGS).upsert(SettingsRow(settings, settings.updatedAt)) { onConflict = "user_id" }
        }

        // 3. Serverwijzigingen die nieuwer zijn: lokaal toepassen. Opnieuw inlezen, zodat een
        //    wijziging die tijdens het netwerkverkeer is opgeslagen niet verloren gaat.
        val freshEntries = repo.loadEntries().toMutableMap()
        val freshTombstones = repo.loadTombstones().toMutableMap()
        var changed = false
        for (row in remoteRows) {
            if (row.updatedAtMs <= localTime(row.date, freshEntries, freshTombstones)) continue
            if (row.deleted) {
                freshEntries.remove(row.date)
                freshTombstones[row.date] = row.updatedAtMs
                changed = true
            } else {
                val entry = row.toEntry() ?: continue
                freshEntries[row.date] = entry
                freshTombstones.remove(row.date)
                changed = true
            }
        }
        if (changed) {
            repo.saveEntries(freshEntries.values)
            repo.saveTombstones(freshTombstones)
        }

        val freshSettings = repo.loadSettings()
        if (remoteSettings != null && remoteSettings.updatedAtMs > freshSettings.updatedAt) {
            repo.saveSettings(remoteSettings.data.copy(updatedAt = remoteSettings.updatedAtMs))
            changed = true
        }
        return changed
    }
}
