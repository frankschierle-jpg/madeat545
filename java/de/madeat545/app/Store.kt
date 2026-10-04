package de.madeat545.app

import android.content.Context
import java.time.LocalDate

/** Einfache lokale Speicherung (Sync mit Google-Konto kommt in einer späteren Version). */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("made545", Context.MODE_PRIVATE)

    init {
        if (!p.contains("start_date")) {
            p.edit().putString("start_date", LocalDate.now().toString()).apply()
        }
    }

    val startDate: LocalDate
        get() = LocalDate.parse(p.getString("start_date", LocalDate.now().toString()))

    fun done(date: LocalDate): Set<String> =
        p.getStringSet("done_$date", emptySet())?.toSet() ?: emptySet()

    fun isDone(date: LocalDate, id: String) = id in done(date)

    fun setDone(date: LocalDate, id: String, value: Boolean) {
        val s = done(date).toMutableSet()
        if (value) s.add(id) else s.remove(id)
        p.edit().putStringSet("done_$date", s).apply()
    }

    var weekOffset: Int
        get() = p.getInt("week_offset", 0)
        set(v) { p.edit().putInt("week_offset", v).apply() }

    fun recipeIndex(date: LocalDate): Int =
        p.getInt("recipe_$date", Math.floorMod(date.toEpochDay(), Recipes.all.size.toLong()).toInt())

    fun setRecipeIndex(date: LocalDate, i: Int) {
        p.edit().putInt("recipe_$date", Math.floorMod(i, Recipes.all.size)).apply()
    }

    fun plan(date: LocalDate): DayPlan = Schedule.dayPlan(date, weekOffset)

    /** Sind alle Pflicht-Aufgaben eines Schultags erledigt? */
    fun isComplete(date: LocalDate): Boolean {
        val plan = plan(date)
        val done = done(date)
        return plan.items.filter { it.mandatory }.all { it.id in done }
    }

    /** Fortschritt heute (0..1) über alle abhakbaren Einträge. */
    fun progress(date: LocalDate): Float {
        val items = plan(date).items
        if (items.isEmpty()) return 0f
        val done = done(date)
        return items.count { it.id in done }.toFloat() / items.size
    }

    /**
     * Streak = Schultage in Folge mit allen Pflicht-Aufgaben.
     * Wochenenden und Ferien pausieren den Streak (zählen nicht, brechen nicht).
     * Der heutige Tag zählt mit, sobald er komplett ist; ist er noch offen, bricht er nicht.
     */
    fun streak(today: LocalDate): Int {
        var count = 0
        var d = today
        if (Schedule.isSchoolDay(d)) {
            if (isComplete(d)) count++
        }
        d = d.minusDays(1)
        val start = startDate
        repeat(800) {
            if (d.isBefore(start)) return count
            if (Schedule.isSchoolDay(d)) {
                if (isComplete(d)) count++ else return count
            }
            d = d.minusDays(1)
        }
        return count
    }
}
