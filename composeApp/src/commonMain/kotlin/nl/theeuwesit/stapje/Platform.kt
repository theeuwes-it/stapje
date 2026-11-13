package nl.theeuwesit.stapje

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.KeyValueStore
import nl.theeuwesit.stapje.reminder.ReminderController

class Platform(
    val store: KeyValueStore,
    val reminders: ReminderController,
    val isWeb: Boolean,
    /** Huidige lokale datum en tijd van het apparaat. */
    val now: () -> LocalDateTime,
)

/** Systeem-terugknop (Android); doet niets op web. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
