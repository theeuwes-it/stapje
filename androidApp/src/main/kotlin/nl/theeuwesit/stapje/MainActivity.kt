package nl.theeuwesit.stapje

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import nl.theeuwesit.stapje.sync.Cloud
import nl.theeuwesit.stapje.ui.App

class MainActivity : ComponentActivity() {

    private lateinit var reminders: AndroidReminders
    private var openTodaySignal by mutableIntStateOf(0)
    private var resumeSignal by mutableIntStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { reminders.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        reminders = AndroidReminders(applicationContext) {
            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val platform = Platform(
            store = SharedPrefsStore(applicationContext),
            reminders = reminders,
            isWeb = false,
            now = ::androidNow,
            cloud = Cloud.client,
        )

        // Na draaien e.d. komt hetzelfde intent opnieuw binnen; de inlogcode is dan al gebruikt.
        if (savedInstanceState == null) handleIntent(intent)
        reminders.requestOnFirstStart()

        setContent { App(platform, openTodaySignal, resumeSignal) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        reminders.refresh()
        resumeSignal++
    }

    private fun handleIntent(intent: Intent?) {
        handleLoginReturn(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            intent.removeExtra(EXTRA_OPEN_TODAY)
            openTodaySignal++
        }
    }

    /** Terugkeer na inloggen met Google: stapje://login?code=... of ?error=... */
    private fun handleLoginReturn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "stapje" || data.host != "login") return
        intent.data = null
        val params = buildMap {
            data.fragment?.split('&')?.filter { it.isNotEmpty() }?.forEach { part ->
                val key = Uri.decode(part.substringBefore('='))
                put(key, Uri.decode(part.substringAfter('=', "")))
            }
            data.queryParameterNames.forEach { key -> data.getQueryParameter(key)?.let { put(key, it) } }
        }
        Cloud.completeLogin(params)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
