package nl.theeuwesit.stapje.ui

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import nl.theeuwesit.stapje.model.DayEntry
import nl.theeuwesit.stapje.model.DayPart
import kotlin.math.abs
import kotlin.math.roundToInt

val DAY_NAMES = listOf("maandag", "dinsdag", "woensdag", "donderdag", "vrijdag", "zaterdag", "zondag")
val DAY_SHORT = listOf("ma", "di", "wo", "do", "vr", "za", "zo")
private val MONTHS = listOf(
    "januari", "februari", "maart", "april", "mei", "juni",
    "juli", "augustus", "september", "oktober", "november", "december",
)

/** 8.0 -> "8", 7.5 -> "7,5" */
fun formatHours(hours: Double): String {
    val halves = (hours * 2).roundToInt()
    return if (halves % 2 == 0) "${halves / 2}" else "${halves / 2},5"
}

/** 4.25 -> "4,3" */
fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    val sign = if (tenths < 0) "-" else ""
    return "$sign${abs(tenths) / 10},${abs(tenths) % 10}"
}

fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** "vrijdag 13 november" */
fun LocalDate.longLabel(): String =
    "${DAY_NAMES[dayOfWeek.isoDayNumber - 1]} $dayOfMonth ${MONTHS[monthNumber - 1]}"

fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

/** "Vandaag", "Gisteren" of "Woensdag 11 november" */
fun relativeLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Vandaag"
    today.minus(1, DateTimeUnit.DAY) -> "Gisteren"
    else -> date.longLabel().capitalized()
}

fun DayEntry.workSummary(): String = buildList {
    add(dayPart.label)
    if (dayPart != DayPart.NIET_GEWERKT) add("${formatHours(hours)} uur")
    if (labWork) add("lab ${formatHours(labHours)} uur")
    if (activities.isNotEmpty()) add(activities.joinToString(", "))
}.joinToString(" · ")

fun DayEntry.scoreSummary(): String =
    "Pijn $painOverall · Rug $backPain · Energie $energy · Gevoel $mood"
