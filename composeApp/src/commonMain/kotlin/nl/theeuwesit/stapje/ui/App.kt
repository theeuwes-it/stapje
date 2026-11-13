package nl.theeuwesit.stapje.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import nl.theeuwesit.stapje.AppState
import nl.theeuwesit.stapje.Platform
import nl.theeuwesit.stapje.PlatformBackHandler
import nl.theeuwesit.stapje.data.Repository
import nl.theeuwesit.stapje.sync.SyncController

private sealed interface Screen {
    data object Home : Screen
    data object Options : Screen
    data class Day(val date: LocalDate) : Screen
}

private const val SYNC_INTERVAL_TICKS = 60 // 60 x 5 s = elke 5 minuten

/**
 * @param openTodaySignal verhoog dit getal om direct het formulier van vandaag te openen
 *   (bijv. na een tik op de herinnering).
 * @param resumeSignal verhoog dit getal als de app weer op de voorgrond komt (dan wordt er gesynchroniseerd).
 */
@Composable
fun App(platform: Platform, openTodaySignal: Int = 0, resumeSignal: Int = 0) {
    val repo = remember { Repository(platform.store) }
    val state = remember { AppState(repo, platform.now) }
    val sync = remember { platform.cloud?.let { SyncController(it, repo, platform.authRedirectUrl, platform.now) } }
    val reminders = platform.reminders
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    fun syncInBackground() {
        if (sync == null) return
        scope.launch { if (sync.syncNow()) state.reload() }
    }

    state.onLocalChange = ::syncInBackground

    LaunchedEffect(Unit) { platform.startup() }

    LaunchedEffect(sync) {
        sync?.observeSession { if (sync.syncNow()) state.reload() }
    }

    LaunchedEffect(resumeSignal) { if (resumeSignal > 0) syncInBackground() }

    LaunchedEffect(openTodaySignal) {
        if (openTodaySignal > 0) {
            state.refreshToday()
            screen = Screen.Day(state.today)
        }
    }

    // Herinnering (opnieuw) plannen zodra de instellingen veranderen.
    LaunchedEffect(state.settings) { reminders.apply(state.settings) }

    // Datum bijhouden, toestemming verversen, af en toe synchroniseren en op web de melding zelf afvuren.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            state.refreshToday()
            reminders.refresh()
            if (reminders.runsOnlyWhileOpen) state.maybeNotifyInApp(reminders)
            if (++ticks % SYNC_INTERVAL_TICKS == 0) syncInBackground()
            delay(5_000)
        }
    }

    PlatformBackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    val promptSlot: (@Composable () -> Unit)? =
        if (sync != null && !sync.signedIn && !state.syncPromptDismissed) {
            @Composable {
                SyncPromptCard(
                    onSignIn = { scope.launch { sync.signIn() } },
                    onDismiss = state::dismissSyncPrompt,
                )
            }
        } else {
            null
        }

    val accountSlot: (@Composable () -> Unit)? =
        if (sync != null) {
            @Composable {
                AccountSection(
                    sync = sync,
                    onSignIn = { scope.launch { sync.signIn() } },
                    onSignOut = { scope.launch { sync.signOut() } },
                    onSyncNow = ::syncInBackground,
                )
            }
        } else {
            null
        }

    AppTheme {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                state = state,
                reminders = reminders,
                onOpenDay = { screen = Screen.Day(it) },
                onOpenSettings = { screen = Screen.Options },
                topSlot = promptSlot,
            )

            Screen.Options -> SettingsScreen(
                settings = state.settings,
                reminders = reminders,
                isWeb = platform.isWeb,
                onChange = state::updateSettings,
                onBack = { screen = Screen.Home },
                accountSection = accountSlot,
            )

            is Screen.Day -> EntryScreen(
                date = s.date,
                today = state.today,
                existing = state.entries[s.date.toString()],
                previous = state.lastEntryBefore(s.date),
                settings = state.settings,
                onSave = { entry ->
                    state.save(entry)
                    if (entry.date == state.today.toString()) reminders.onTodayCompleted()
                    screen = Screen.Home
                },
                onDelete = {
                    state.delete(s.date)
                    screen = Screen.Home
                },
                onBack = { screen = Screen.Home },
            )
        }
    }
}
