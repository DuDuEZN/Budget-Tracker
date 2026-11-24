package com.duduezn.budgettracker.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Bornes du « mois budgétaire » courant, fin exclusive.
 *
 * Le salaire n'arrive pas toujours le 1er : l'utilisateur peut choisir un jour de
 * début de cycle (1 à 28 pour éviter les mois trop courts). Par exemple avec un
 * début au 25, le cycle du 25 octobre au 24 novembre est un seul « mois ».
 */
object MonthRange {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28
    const val DEFAULT_START_DAY = 1

    fun current(
        now: Long = System.currentTimeMillis(),
        startDay: Int = DEFAULT_START_DAY
    ): LongRange {
        val day = startDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Début du cycle : ce mois-ci si le jour est déjà passé, sinon le mois précédent.
        val start = (today.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, day)
            if (today.get(Calendar.DAY_OF_MONTH) < day) {
                add(Calendar.MONTH, -1)
            }
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis until end.timeInMillis
    }

    /** Libellé court du cycle : « Novembre 2026 » ou « 25 oct. – 24 nov. 2026 ». */
    fun label(
        now: Long = System.currentTimeMillis(),
        startDay: Int = DEFAULT_START_DAY
    ): String {
        val day = startDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        if (day == DEFAULT_START_DAY) {
            return SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
                .format(Date(now))
                .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        }

        val range = current(now, day)
        val start = Date(range.first)
        val endInclusive = Date(range.last)
        val startYear = Calendar.getInstance().apply { timeInMillis = range.first }
            .get(Calendar.YEAR)
        val endYear = Calendar.getInstance().apply { timeInMillis = range.last }
            .get(Calendar.YEAR)

        return if (startYear == endYear) {
            val formatter = SimpleDateFormat("d MMM", Locale.FRENCH)
            "${formatter.format(start)} – ${formatter.format(endInclusive)} $endYear"
        } else {
            val formatter = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)
            "${formatter.format(start)} – ${formatter.format(endInclusive)}"
        }
    }
}
