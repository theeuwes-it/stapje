package nl.theeuwesit.stapje.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import nl.theeuwesit.stapje.AppState
import nl.theeuwesit.stapje.Platform
import nl.theeuwesit.stapje.PlatformBackHandler
import nl.theeuwesit.stapje.data.Repository

private sealed interface Screen {
    data object Home : Screen
    data object Options : Screen
    data class Day(val date: LocalDate) : Screen
}

/**
 * @param openTodaySignal verhoog dit getal om direct het formulier van vandaag te openen
 *   (bijv. na een tik op de herinnering).
 */
@Composable
fun App(platform: Platform, openTodaySignal: Int = 0) {
    val state = remember { AppState(Repository(platform.store), platform.now) }
    val reminders = platform.reminders
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(openTodaySignal) {
        if (openTodaySignal > 0) {
            state.refreshToday()
            screen = Screen.Day(state.today)
        }
    }

    // Herinnering (opnieuw) plannen zodra de instellingen veranderen.
    LaunchedEffect(state.settings) { reminders.apply(state.settings) }

    // Datum bijhouden, toestemming verversen en op web de melding zelf afvuren.
    LaunchedEffect(Unit) {
        while (true) {
            state.refreshToday()
            reminders.refresh()
            if (reminders.runsOnlyWhileOpen) state.maybeNotifyInApp(reminders)
            delay(5_000)
        }
    }

    PlatformBackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    AppTheme {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                state = state,
                reminders = reminders,
                onOpenDay = { screen = Screen.Day(it) },
                onOpenSettings = { screen = Screen.Options },
            )

            Screen.Options -> SettingsScreen(
                settings = state.settings,
                reminders = reminders,
                isWeb = platform.isWeb,
                onChange = state::updateSettings,
                onBack = { screen = Screen.Home },
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
