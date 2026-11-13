package nl.theeuwesit.stapje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F6F62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE2),
    onPrimaryContainer = Color(0xFF0B2A23),
    secondary = Color(0xFF52635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8E3),
    onSecondaryContainer = Color(0xFF101F1B),
    tertiary = Color(0xFFA4552F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0D0),
    onTertiaryContainer = Color(0xFF3A1607),
    background = Color(0xFFF4F6F4),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF4F6F4),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE1E6E3),
    onSurfaceVariant = Color(0xFF424946),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}

val CardWhite = Color.White

private val Bad = Color(0xFFD0533F)
private val Middle = Color(0xFFE0A526)
private val Good = Color(0xFF3E9B6A)

/** Kleur voor een score 0..10. Bij pijn is hoog slecht, bij energie/gevoel is hoog goed. */
fun scoreColor(value: Int, highIsGood: Boolean): Color {
    val goodness = (if (highIsGood) value else 10 - value) / 10f
    return if (goodness < 0.5f) lerp(Bad, Middle, goodness * 2) else lerp(Middle, Good, (goodness - 0.5f) * 2)
}

@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

/** Grote - en + knoppen met stappen van een half uur. */
@Composable
fun HoursStepper(
    value: Double,
    onChange: (Double) -> Unit,
    min: Double = 0.0,
    max: Double = 16.0,
    step: Double = 0.5,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(52.dp),
        ) { Text("-", fontSize = 24.sp) }
        Text(
            "${formatHours(value)} uur",
            modifier = Modifier.widthIn(min = 104.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(52.dp),
        ) { Text("+", fontSize = 24.sp) }
    }
}

/** Vraag met een rij van 0 t/m 10: één tik om te antwoorden. */
@Composable
fun ScoreQuestion(
    title: String,
    lowLabel: String,
    highLabel: String,
    value: Int?,
    highIsGood: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(
                    "$value",
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(value, highIsGood),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0..10) {
                val selected = value == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) scoreColor(i, highIsGood) else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$i",
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Klein gekleurd label, bijv. "Pijn 5". */
@Composable
fun ScoreBadge(label: String, value: Int, highIsGood: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scoreColor(value, highIsGood).copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelMedium,
            color = scoreColor(value, highIsGood),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
