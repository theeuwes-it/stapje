package nl.theeuwesit.stapje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.theeuwesit.stapje.sync.SyncController

/** Kaart op het startscherm zolang je niet bent ingelogd. */
@Composable
fun SyncPromptCard(onSignIn: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Telefoon en website gelijk houden?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Log in met Google, dan zie je overal dezelfde dagen. Je gegevens zijn alleen voor jou zichtbaar.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSignIn) { Text("Inloggen met Google") }
                TextButton(onClick = onDismiss) { Text("Niet nu") }
            }
        }
    }
}

/** Account-blok in de instellingen. */
@Composable
fun AccountSection(
    sync: SyncController,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
) {
    Section("Account en synchronisatie") {
        if (!sync.signedIn) {
            Text(
                "Log in met Google, dan blijven je telefoon en de website gelijk.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSignIn) { Text("Inloggen met Google") }
        } else {
            Text(
                "Ingelogd als ${sync.email ?: "Google-account"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val last = sync.lastSynced
            Text(
                when {
                    sync.syncing -> "Bezig met synchroniseren..."
                    last != null -> "Laatst gesynchroniseerd om ${formatTime(last.hour, last.minute)}"
                    else -> "Nog niet gesynchroniseerd"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSyncNow, enabled = !sync.syncing) { Text("Nu synchroniseren") }
                TextButton(onClick = onSignOut) { Text("Uitloggen") }
            }
        }
        sync.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
