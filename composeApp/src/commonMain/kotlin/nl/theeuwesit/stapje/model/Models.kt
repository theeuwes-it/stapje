package nl.theeuwesit.stapje.model

import kotlinx.serialization.Serializable

@Serializable
enum class DayPart(val label: String) {
    HELE_DAG("Hele dag"),
    OCHTEND("Ochtend"),
    MIDDAG("Middag"),
    NIET_GEWERKT("Niet gewerkt"),
}

/** Eén ingevulde dag. Scores lopen van 0 t/m 10. */
@Serializable
data class DayEntry(
    /** ISO-datum, bijv. 2026-11-13 */
    val date: String,
    val dayPart: DayPart,
    val hours: Double,
    val activities: List<String> = emptyList(),
    val labWork: Boolean = false,
    val labHours: Double = 0.0,
    val labDescription: String = "",
    val note: String = "",
    /** 0 = geen pijn, 10 = ergst denkbaar */
    val painOverall: Int,
    /** 0 = geen rugpijn, 10 = ergst denkbaar */
    val backPain: Int,
    /** 0 = leeg, 10 = vol energie */
    val energy: Int,
    /** 0 = slecht, 10 = heel goed */
    val mood: Int,
)

val DEFAULT_ACTIVITIES = listOf(
    "Bureauwerk",
    "Overleg",
    "Schrijven",
    "Data-analyse",
    "Lezen",
    "Onderwijs",
    "Anders",
)

@Serializable
data class Settings(
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 17,
    val reminderMinute: Int = 0,
    /** ISO-dagnummers: 1 = maandag ... 7 = zondag */
    val workdays: List<Int> = listOf(1, 2, 3, 4, 5),
    val fullDayHours: Double = 8.0,
    val activities: List<String> = DEFAULT_ACTIVITIES,
)
