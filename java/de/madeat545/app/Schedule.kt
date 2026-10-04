package de.madeat545.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class WeekType { A, B }
enum class DayKind { SCHOOL, WEEKEND, HOLIDAY }

/** Ein Eintrag im Tagesplan. [time] nur bei festen Zeiten (Morgen/Abend). */
data class PlanItem(
    val id: String,
    val title: String,
    val time: String? = null,
    val note: String? = null,
    val mandatory: Boolean = false,
    val isLunch: Boolean = false,
    val isBed: Boolean = false,
)

data class DayPlan(
    val date: LocalDate,
    val kind: DayKind,
    val week: WeekType?,
    val holidayName: String?,
    val items: List<PlanItem>,
)

data class Holiday(val name: String, val from: LocalDate, val to: LocalDate)

object Schedule {
    val WAKE_TIME: LocalTime = LocalTime.of(5, 45)

    /** Montag einer bekannten A-Woche (28.09.–02.10.2026). */
    private val REFERENCE_A_MONDAY: LocalDate = LocalDate.of(2026, 9, 28)

    /** Schulferien Sachsen 2026/27 (Quelle: schule.sachsen.de). */
    val holidays = listOf(
        Holiday("Herbstferien", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 24)),
        Holiday("Weihnachtsferien", LocalDate.of(2026, 12, 23), LocalDate.of(2027, 1, 2)),
        Holiday("Winterferien", LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 19)),
        Holiday("Osterferien", LocalDate.of(2027, 3, 26), LocalDate.of(2027, 4, 2)),
        Holiday("Unterrichtsfreier Tag", LocalDate.of(2027, 5, 7), LocalDate.of(2027, 5, 7)),
        Holiday("Pfingstferien", LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 18)),
        Holiday("Sommerferien", LocalDate.of(2027, 7, 10), LocalDate.of(2027, 8, 20)),
    )

    fun holidayOn(d: LocalDate): Holiday? =
        holidays.firstOrNull { !d.isBefore(it.from) && !d.isAfter(it.to) }

    fun dayKind(d: LocalDate): DayKind = when {
        holidayOn(d) != null -> DayKind.HOLIDAY
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY -> DayKind.WEEKEND
        else -> DayKind.SCHOOL
    }

    fun isSchoolDay(d: LocalDate): Boolean = dayKind(d) == DayKind.SCHOOL

    private fun weekHasSchool(monday: LocalDate): Boolean =
        (0L..4L).any { isSchoolDay(monday.plusDays(it)) }

    /** A/B wechselt nur in Wochen mit Schule – Ferienwochen zählen nicht mit. */
    fun weekType(d: LocalDate, offset: Int): WeekType {
        val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var count = 0
        var m = REFERENCE_A_MONDAY
        if (!monday.isBefore(REFERENCE_A_MONDAY)) {
            while (m.isBefore(monday)) {
                if (weekHasSchool(m)) count++
                m = m.plusWeeks(1)
            }
        } else {
            while (m.isAfter(monday)) {
                m = m.minusWeeks(1)
                if (weekHasSchool(m)) count--
            }
        }
        return if (Math.floorMod(count + offset, 2) == 0) WeekType.A else WeekType.B
    }

    // ---------- Bausteine ----------
    private fun bett() = PlanItem("bett", "Aufstehen & Bett machen", "05:45", "wird mit dem Bett-Foto abgehakt", mandatory = true, isBed = true)
    private fun morgensport() = PlanItem("morgensport", "Morgensport", "06:00–07:00", mandatory = true)
    private fun fruehstueck() = PlanItem("fruehstueck", "Duschen & Frühstück", "07:00")
    private fun schule() = PlanItem("schule", "Schule")
    private fun schlafen() = PlanItem("schlafen", "Schlafen gehen", "22:00")

    private fun mittag(inSchool: Boolean) = PlanItem(
        "mittag", "Mittagessen",
        note = if (inSchool) "in der Schule / mitgebracht" else "Rezeptvorschlag unten",
        mandatory = true, isLunch = true,
    )
    private fun snack() = PlanItem("snack", "Snack")
    private fun abendessen() = PlanItem("abendessen", "Abendessen")
    private fun lernen(n: Int, dauer: String) = PlanItem("lernen$n", "Lernen", note = dauer)
    private fun geigeUeben() = PlanItem("geige_ueben", "Geige üben", note = "30 Min.")
    private fun training() = PlanItem("abendsport", "Kurzes Training", note = "Abendsport", mandatory = true)
    private fun geigenunterricht() = PlanItem("geigenunterricht", "Geigenunterricht", note = "Erinnerung zum Losgehen kommt")
    private fun ballett() = PlanItem("abendsport", "Ballett", note = "Abendsport · Erinnerung zum Losgehen kommt", mandatory = true)
    private fun orchester() = PlanItem("orchester", "Orchester", note = "Erinnerung zum Losgehen kommt")
    private fun volleyball() = PlanItem("abendsport", "Volleyball", note = "Abendsport · Erinnerung zum Losgehen kommt", mandatory = true)

    private fun schoolMiddle(week: WeekType, dow: DayOfWeek): List<PlanItem> = when (week) {
        WeekType.A -> when (dow) {
            DayOfWeek.MONDAY -> listOf(mittag(false), lernen(1, "ca. 2 Std."), geigeUeben(), training(), abendessen(), geigenunterricht())
            DayOfWeek.TUESDAY -> listOf(mittag(true), snack(), lernen(1, "ca. 45 Min."), ballett(), abendessen(), lernen(2, "ca. 1 Std."), geigeUeben())
            DayOfWeek.WEDNESDAY -> listOf(mittag(false), lernen(1, "ca. 2 Std."), orchester(), abendessen())
            DayOfWeek.THURSDAY -> listOf(mittag(true), snack(), volleyball(), abendessen(), lernen(1, "ca. 1 Std."))
            else -> listOf(mittag(false), lernen(1, "ca. 45 Min."), volleyball(), geigeUeben(), abendessen(), lernen(2, "ca. 1 Std. 15 Min."))
        }
        WeekType.B -> when (dow) {
            DayOfWeek.MONDAY -> listOf(mittag(true), snack(), lernen(1, "ca. 1,5 Std."), abendessen(), geigenunterricht(), training(), lernen(2, "ca. 30 Min."))
            DayOfWeek.TUESDAY -> listOf(mittag(true), snack(), lernen(1, "ca. 45 Min."), ballett(), abendessen(), lernen(2, "ca. 1 Std."), geigeUeben())
            DayOfWeek.WEDNESDAY -> listOf(mittag(true), snack(), lernen(1, "ca. 40 Min."), orchester(), abendessen(), lernen(2, "ca. 30 Min."))
            DayOfWeek.THURSDAY -> listOf(mittag(false), geigeUeben(), lernen(1, "ca. 1 Std."), volleyball(), abendessen(), lernen(2, "ca. 30 Min."))
            else -> listOf(mittag(false), lernen(1, "ca. 45 Min."), volleyball(), geigeUeben(), abendessen(), lernen(2, "ca. 1 Std. 15 Min."))
        }
    }

    private fun freeDay() = listOf(
        PlanItem("frei_sport", "Sport", note = "freiwillig"),
        PlanItem("frei_lernen", "Lernen", note = "freiwillig"),
        PlanItem("frei_geige", "Geige üben", note = "freiwillig"),
    )

    fun dayPlan(d: LocalDate, offset: Int): DayPlan {
        val kind = dayKind(d)
        return when (kind) {
            DayKind.SCHOOL -> {
                val week = weekType(d, offset)
                val items = listOf(bett(), morgensport(), fruehstueck(), schule()) +
                    schoolMiddle(week, d.dayOfWeek) + schlafen()
                DayPlan(d, kind, week, null, items)
            }
            DayKind.WEEKEND -> DayPlan(d, kind, null, null, freeDay())
            DayKind.HOLIDAY -> DayPlan(d, kind, null, holidayOn(d)?.name, freeDay())
        }
    }

    // ---------- Losgehen-Erinnerungen ----------
    data class Leave(val time: LocalTime, val text: String)

    fun leaveFor(d: LocalDate): Leave? {
        if (!isSchoolDay(d)) return null
        return when (d.dayOfWeek) {
            DayOfWeek.MONDAY -> Leave(LocalTime.of(18, 30), "Zeit zum Losgehen: Geigenunterricht um 18:45")
            DayOfWeek.TUESDAY -> Leave(LocalTime.of(17, 0), "Zeit zum Losgehen: Ballett um 17:30")
            DayOfWeek.WEDNESDAY -> Leave(LocalTime.of(17, 0), "Zeit zum Losgehen: Orchester um 17:30")
            DayOfWeek.THURSDAY -> Leave(LocalTime.of(16, 30), "Zeit zum Losgehen: Volleyball um 17:00")
            DayOfWeek.FRIDAY -> Leave(LocalTime.of(15, 45), "Zeit zum Losgehen: Volleyball um 16:00")
            else -> null
        }
    }

    fun nextWake(now: LocalDateTime): LocalDateTime? {
        var d = now.toLocalDate()
        repeat(400) {
            if (isSchoolDay(d)) {
                val t = d.atTime(WAKE_TIME)
                if (t.isAfter(now)) return t
            }
            d = d.plusDays(1)
        }
        return null
    }

    fun nextLeave(now: LocalDateTime): Pair<LocalDateTime, String>? {
        var d = now.toLocalDate()
        repeat(30) {
            val l = leaveFor(d)
            if (l != null) {
                val t = d.atTime(l.time)
                if (t.isAfter(now)) return t to l.text
            }
            d = d.plusDays(1)
        }
        return null
    }
}
