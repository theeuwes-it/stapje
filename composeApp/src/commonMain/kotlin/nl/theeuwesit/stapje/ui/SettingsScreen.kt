package nl.theeuwesit.stapje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.theeuwesit.stapje.model.Settings
import nl.theeuwesit.stapje.reminder.ReminderController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    reminders: ReminderController,
    isWeb: Boolean,
    onChange: (Settings) -> Unit,
    onBack: () -> Unit,
    accountSection: (@Composable () -> Unit)? = null,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var newActivity by remember { mutableStateOf("") }

    fun addActivity() {
        val name = newActivity.trim()
        if (name.isNotEmpty() && name !in settings.activities) {
            onChange(settings.copy(activities = settings.activities + name))
        }
        newActivity = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Terug") } },
            )
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
                accountSection?.invoke()

                Section("Herinnering") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dagelijkse herinnering", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Alleen op werkdagen die je nog niet hebt ingevuld",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.reminderEnabled,
                            onCheckedChange = { onChange(settings.copy(reminderEnabled = it)) },
                        )
                    }

                    if (settings.reminderEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tijdstip", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { showTimePicker = true }) {
                                Text(formatTime(settings.reminderHour, settings.reminderMinute))
                            }
                        }

                        Text("Werkdagen", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (1..7).forEach { day ->
                                val selected = day in settings.workdays
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val days = if (selected) settings.workdays - day else (settings.workdays + day).sorted()
                                        onChange(settings.copy(workdays = days))
                                    },
                                    label = { Text(DAY_SHORT[day - 1]) },
                                )
                            }
                        }

                        if (!reminders.permissionGranted) {
                            Text(
                                reminders.permissionNote ?: "Meldingen zijn nog niet toegestaan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (reminders.canRequestPermission) {
                                OutlinedButton(onClick = reminders::requestPermission) { Text("Meldingen toestaan") }
                            }
                        }

                        if (isWeb) {
                            Text(
                                "Let op: in de browser komt de herinnering alleen als deze pagina open staat " +
                                    "(bijvoorbeeld in een vastgezet tabblad). De Android-app herinnert je altijd.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Section("Werkdag", subtitle = "Wordt ingevuld als je 'Hele dag' kiest; een dagdeel is de helft") {
                    HoursStepper(
                        value = settings.fullDayHours,
                        onChange = { onChange(settings.copy(fullDayHours = it)) },
                        min = 1.0,
                        max = 12.0,
                    )
                }

                Section("Activiteiten", subtitle = "Deze keuzes zie je bij 'Wat heb je gedaan?'") {
                    settings.activities.forEach { activity ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(activity, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                onChange(settings.copy(activities = settings.activities - activity))
                            }) { Text("Verwijderen") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newActivity,
                            onValueChange = { newActivity = it },
                            label = { Text("Nieuwe activiteit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = ::addActivity, enabled = newActivity.isNotBlank()) { Text("Toevoegen") }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = settings.reminderHour,
            initialMinute = settings.reminderMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Tijdstip herinnering") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(settings.copy(reminderHour = pickerState.hour, reminderMinute = pickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuleren") } },
        )
    }
}
