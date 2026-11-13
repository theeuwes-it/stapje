package nl.theeuwesit.stapje

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import nl.theeuwesit.stapje.data.KeyValueStore

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("stapje", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

fun androidNow(): LocalDateTime {
    val n = java.time.LocalDateTime.now()
    return LocalDateTime(n.year, n.monthValue, n.dayOfMonth, n.hour, n.minute)
}

fun androidToday(): LocalDate = androidNow().date

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
