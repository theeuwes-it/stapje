package nl.theeuwesit.stapje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.DayPart
import nl.theeuwesit.stapje.model.Scores
import nl.theeuwesit.stapje.model.Settings
import nl.theeuwesit.stapje.model.TimeOfDay
import kotlin.math.roundToInt

private fun defaultHoursFor(part: DayPart, settings: Settings): Double = when (part) {
    DayPart.HELE_DAG -> settings.fullDayHours
    DayPart.OCHTEND, DayPart.MIDDAG -> (settings.fullDayHours).roundToInt() / 2.0
    DayPart.NIET_GEWERKT -> 0.0
}

/** Scores van één dagdeel terwijl je ze invult; null = nog niet beantwoord. */
private data class ScoresDraft(
    val pain: Int? = null,
    val back: Int? = null,
    val energy: Int? = null,
    val mood: Int? = null,
) {
    val answered: Int get() = listOf(pain, back, energy, mood).count { it != null }
    val isEmpty: Boolean get() = answered == 0
    val isComplete: Boolean get() = answered == 4

    fun toScores(): Scores = Scores(painOverall = pain!!, backPain = back!!, energy = energy!!, mood = mood!!)
}

private fun Scores?.toDraft(): ScoresDraft =
    if (this == null) ScoresDraft() else ScoresDraft(painOverall, backPain, energy, mood)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    date: LocalDate,
    today: LocalDate,
    existing: DayEntry?,
    previous: DayEntry?,
    settings: Settings,
    onSave: (DayEntry) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    // Werkvelden worden voorgevuld met de vorige keer: meestal alleen bevestigen.
    var dayPart by remember(date) { mutableStateOf(existing?.dayPart ?: previous?.dayPart) }
    var hours by remember(date) {
        mutableStateOf(existing?.hours ?: previous?.hours ?: settings.fullDayHours)
    }
    var activities by remember(date) { mutableStateOf(existing?.activities?.toSet() ?: emptySet()) }
    var labWork by remember(date) { mutableStateOf(existing?.labWork ?: false) }
    var labHours by remember(date) { mutableStateOf(existing?.labHours?.takeIf { it > 0 } ?: 1.0) }
    var labDescription by remember(date) { mutableStateOf(existing?.labDescription ?: "") }
    var note by remember(date) { mutableStateOf(existing?.note ?: "") }
    var drafts by remember(date) {
        mutableStateOf(TimeOfDay.entries.associateWith { existing?.scores?.get(it).toDraft() })
    }
    // Begin bij het eerste dagdeel dat nog niet helemaal is ingevuld.
    var period by remember(date) {
        mutableStateOf(TimeOfDay.entries.firstOrNull { drafts.getValue(it).isComplete.not() } ?: TimeOfDay.OCHTEND)
    }
    val draft = drafts.getValue(period)
    fun updateDraft(change: (ScoresDraft) -> ScoresDraft) {
        drafts = drafts + (period to change(draft))
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val worked = dayPart != null && dayPart != DayPart.NIET_GEWERKT
    // Elk dagdeel is leeg of helemaal ingevuld; minstens één dagdeel moet ingevuld zijn.
    val partialMissing = drafts.values.filter { !it.isEmpty && !it.isComplete }.sumOf { 4 - it.answered }
    val noneStarted = drafts.values.all { it.isEmpty }
    val missing = (if (dayPart == null) 1 else 0) + partialMissing + (if (noneStarted) 4 else 0)

    fun buildEntry(): DayEntry {
        val chosen = settings.activities.filter { it in activities } + activities.filter { it !in settings.activities }
        return DayEntry(
            date = date.toString(),
            dayPart = dayPart!!,
            hours = if (worked) hours else 0.0,
            activities = if (worked) chosen else emptyList(),
            labWork = worked && labWork,
            labHours = if (worked && labWork) labHours.coerceAtMost(hours) else 0.0,
            labDescription = if (worked && labWork) labDescription.trim() else "",
            note = note.trim(),
            scores = drafts.filterValues { it.isComplete }.mapValues { it.value.toScores() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(relativeLabel(date, today))
                        if (date == today) {
                            Text(date.longLabel(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Terug") } },
                actions = {
                    if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Verwijderen") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { onSave(buildEntry()) },
                        enabled = missing == 0,
                        modifier = Modifier.widthIn(max = 608.dp).fillMaxWidth().height(56.dp),
                    ) {
                        Text(
                            when (missing) {
                                0 -> "Opslaan"
                                1 -> "Nog 1 vraag te beantwoorden"
                                else -> "Nog $missing vragen te beantwoorden"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Gewerkt
                Section("Hoeveel heb je gewerkt?") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DayPart.entries.forEach { part ->
                            FilterChip(
                                selected = dayPart == part,
                                onClick = {
                                    if (dayPart != part) {
                                        dayPart = part
                                        hours = defaultHoursFor(part, settings)
                                    }
                                },
                                label = { Text(part.label) },
                            )
                        }
                    }
                    if (worked) {
                        HoursStepper(value = hours, onChange = { hours = it }, min = 0.5)
                    }
                }

                // 2. Wat gedaan
                if (worked) {
                    Section("Wat heb je gedaan?", subtitle = "Kies wat van toepassing is") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (settings.activities + activities.filter { it !in settings.activities }).forEach { a ->
                                FilterChip(
                                    selected = a in activities,
                                    onClick = { activities = if (a in activities) activities - a else activities + a },
                                    label = { Text(a) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Labwerk", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Uren en wat je in het lab gedaan hebt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = labWork, onCheckedChange = { labWork = it })
                        }
                        if (labWork) {
                            HoursStepper(
                                value = labHours.coerceAtMost(hours),
                                onChange = { labHours = it },
                                min = 0.5,
                                max = hours.coerceAtLeast(0.5),
                            )
                            OutlinedTextField(
                                value = labDescription,
                                onValueChange = { labDescription = it },
                                label = { Text("Wat voor labwerk? (optioneel)") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // 3. Hoe gaat het
                Section(
                    "Hoe gaat het met je?",
                    subtitle = "Per dagdeel een cijfer van 0 tot 10. Later op de dag aanvullen kan ook.",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TimeOfDay.entries.forEach { p ->
                            val d = drafts.getValue(p)
                            FilterChip(
                                selected = period == p,
                                onClick = { period = p },
                                label = {
                                    Text(
                                        when {
                                            d.isComplete -> "${p.label} ✓"
                                            d.isEmpty -> p.label
                                            else -> "${p.label} ${d.answered}/4"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    ScoreQuestion("Pijn algemeen", "geen pijn", "ergst denkbaar", draft.pain, highIsGood = false) { v ->
                        updateDraft { it.copy(pain = v) }
                    }
                    ScoreQuestion("Rugpijn", "geen", "ergst denkbaar", draft.back, highIsGood = false) { v ->
                        updateDraft { it.copy(back = v) }
                    }
                    ScoreQuestion("Energie", "leeg", "vol energie", draft.energy, highIsGood = true) { v ->
                        updateDraft { it.copy(energy = v) }
                    }
                    ScoreQuestion("Gevoel", "slecht", "heel goed", draft.mood, highIsGood = true) { v ->
                        updateDraft { it.copy(mood = v) }
                    }
                    if (!draft.isEmpty) {
                        TextButton(onClick = { updateDraft { ScoresDraft() } }) { Text("${period.label} wissen") }
                    }
                }

                // 4. Notitie
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notitie (optioneel)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Dag verwijderen?") },
            text = { Text("Alles wat je voor ${date.longLabel()} hebt ingevuld wordt verwijderd.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}
