package com.duduezn.budgettracker.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Bornes du mois courant, fin exclusive. */
object MonthRange {

    fun current(now: Long = System.currentTimeMillis()): LongRange {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 1)
        return start until calendar.timeInMillis
    }

    fun label(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("MMMM yyyy", Locale.FRENCH).format(Date(now))
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
}
