package nl.theeuwesit.stapje

import androidx.compose.runtime.Composable
import io.github.jan.supabase.SupabaseClient
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.KeyValueStore
import nl.theeuwesit.stapje.reminder.ReminderController

class Platform(
    val store: KeyValueStore,
    val reminders: ReminderController,
    val isWeb: Boolean,
    /** Huidige lokale datum en tijd van het apparaat. */
    val now: () -> LocalDateTime,
    /** Supabase-client, of null als synchronisatie niet is ingesteld. */
    val cloud: SupabaseClient? = null,
    /** Terugkeeradres na inloggen met Google; null = standaard (Android-deeplink). */
    val authRedirectUrl: String? = null,
    /** Eenmalige platformtaak bij het starten (web: inlogcode uit de URL verwerken). */
    val startup: suspend () -> Unit = {},
)

/** Systeem-terugknop (Android); doet niets op web. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
