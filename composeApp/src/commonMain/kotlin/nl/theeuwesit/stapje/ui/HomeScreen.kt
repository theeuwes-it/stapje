package nl.theeuwesit.stapje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import nl.theeuwesit.stapje.AppState
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.DayPart
import nl.theeuwesit.stapje.reminder.ReminderController
import nl.theeuwesit.stapje.reminder.ReminderLogic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    reminders: ReminderController,
    onOpenDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    topSlot: (@Composable () -> Unit)? = null,
) {
    val today = state.today
    val settings = state.settings
    val entries = state.entries
    val pastDays = (1 until 14).map { today.minus(it, DateTimeUnit.DAY) }
    val missing = pastDays.filter { ReminderLogic.isWorkday(it, settings) && it.toString() !in entries }

    val weekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).toString()
    val weekEntries = entries.values.filter { it.date >= weekStart && it.date <= today.toString() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stapje") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Instellingen") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (topSlot != null) {
                    item { topSlot() }
                }
                if (settings.reminderEnabled && !reminders.permissionGranted) {
                    item { PermissionBanner(reminders) }
                }
                item {
                    TodayCard(
                        today = today,
                        entry = entries[today.toString()],
                        isWorkday = ReminderLogic.isWorkday(today, settings),
                        onClick = { onOpenDay(today) },
                    )
                }
                if (missing.isNotEmpty()) {
                    item { MissingCard(missing, today, onOpenDay) }
                }
                if (weekEntries.isNotEmpty()) {
                    item { WeekCard(weekEntries) }
                }
                item {
                    Text(
                        "Afgelopen twee weken",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(pastDays, key = { it.toString() }) { day ->
                    DayRow(
                        date = day,
                        today = today,
                        entry = entries[day.toString()],
                        isWorkday = ReminderLogic.isWorkday(day, settings),
                        onClick = { onOpenDay(day) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(reminders: ReminderController) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Herinneringen staan nog uit", style = MaterialTheme.typography.titleSmall)
            Text(
                reminders.permissionNote
                    ?: "Sta meldingen toe, dan krijg je een seintje als je een werkdag nog niet hebt ingevuld.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (reminders.canRequestPermission) {
                Button(onClick = reminders::requestPermission) { Text("Meldingen toestaan") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(today: LocalDate, entry: DayEntry?, isWorkday: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry == null) MaterialTheme.colorScheme.primaryContainer else CardWhite,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Vandaag", style = MaterialTheme.typography.labelLarge)
                Text(
                    today.longLabel().capitalized(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (entry == null) {
                Text(
                    if (isWorkday) "Nog niet ingevuld. Het duurt minder dan een minuut."
                    else "Geen werkdag, maar je kunt de dag wel invullen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Vandaag invullen", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(entry.workSummary(), style = MaterialTheme.typography.bodyMedium)
                ScoreBadges(entry)
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Aanpassen") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreBadges(entry: DayEntry) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScoreBadge("Pijn", entry.painOverall, highIsGood = false)
        ScoreBadge("Rug", entry.backPain, highIsGood = false)
        ScoreBadge("Energie", entry.energy, highIsGood = true)
        ScoreBadge("Gevoel", entry.mood, highIsGood = true)
    }
}

@Composable
private fun MissingCard(missing: List<LocalDate>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                if (missing.size == 1) "1 werkdag nog in te vullen" else "${missing.size} werkdagen nog in te vullen",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            missing.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        relativeLabel(day, today),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    TextButton(onClick = { onOpenDay(day) }) { Text("Invullen") }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(weekEntries: List<DayEntry>) {
    val hours = weekEntries.sumOf { it.hours }
    val worked = weekEntries.count { it.dayPart != DayPart.NIET_GEWERKT }
    fun avg(selector: (DayEntry) -> Int) = formatOneDecimal(weekEntries.map(selector).average())
    Section("Deze week", subtitle = "$worked ${if (worked == 1) "dag" else "dagen"} gewerkt, ${formatHours(hours)} uur") {
        Text(
            "Gemiddeld: pijn ${avg { it.painOverall }} · rug ${avg { it.backPain }} · " +
                "energie ${avg { it.energy }} · gevoel ${avg { it.mood }}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayRow(date: LocalDate, today: LocalDate, entry: DayEntry?, isWorkday: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(relativeLabel(date, today), style = MaterialTheme.typography.titleSmall)
            when {
                entry != null -> {
                    Text(entry.workSummary(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        entry.scoreSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                isWorkday -> Text(
                    "Nog niet ingevuld",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                else -> Text(
                    "Geen werkdag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
