package nl.theeuwesit.stapje

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.KeyValueStore
import nl.theeuwesit.stapje.model.Settings
import nl.theeuwesit.stapje.reminder.ReminderController
import nl.theeuwesit.stapje.ui.App

// --- Kleine JavaScript-bruggetjes -------------------------------------------------------------

private fun lsGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun lsSet(key: String, value: String): Unit = js("void window.localStorage.setItem(key, value)")

private fun jsNotificationPermission(): String =
    js("(typeof Notification === 'undefined') ? 'unsupported' : Notification.permission")

private fun jsRequestNotificationPermission(): Unit =
    js("void ((typeof Notification !== 'undefined') && Notification.requestPermission())")

private fun jsShowNotification(title: String, body: String): Unit =
    js("void (function () { var n = new Notification(title, { body: body }); n.onclick = function () { window.focus(); n.close(); }; })()")

/** "jaar,maand,dag,uur,minuut" in de lokale tijd van de browser. */
private fun jsNowParts(): String =
    js("(function () { var d = new Date(); return [d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes()].join(','); })()")

// --- Platformimplementaties ------------------------------------------------------------------

private class WebStore : KeyValueStore {
    override fun get(key: String): String? = lsGet(key)
    override fun put(key: String, value: String) = lsSet(key, value)
}

private class WebReminders : ReminderController {
    private var status by mutableStateOf(jsNotificationPermission())

    override val permissionGranted: Boolean get() = status == "granted"
    override val canRequestPermission: Boolean get() = status == "default"
    override val permissionNote: String?
        get() = when (status) {
            "denied" -> "Meldingen zijn geblokkeerd in je browser. Sta ze toe via het slotje naast het webadres en herlaad de pagina."
            "unsupported" -> "Deze browser ondersteunt geen meldingen."
            else -> null
        }
    override val runsOnlyWhileOpen: Boolean = true

    override fun requestPermission() = jsRequestNotificationPermission()
    override fun apply(settings: Settings) = Unit
    override fun showNow(title: String, body: String) = jsShowNotification(title, body)
    override fun refresh() {
        val current = jsNotificationPermission()
        if (current != status) status = current
    }
}

private fun webNow(): LocalDateTime {
    val p = jsNowParts().split(",").map { it.toInt() }
    return LocalDateTime(p[0], p[1], p[2], p[3], p[4])
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Geen systeem-terugknop in de browser.
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = Platform(
        store = WebStore(),
        reminders = WebReminders(),
        isWeb = true,
        now = ::webNow,
    )
    ComposeViewport(document.body!!) {
        App(platform)
    }
}
