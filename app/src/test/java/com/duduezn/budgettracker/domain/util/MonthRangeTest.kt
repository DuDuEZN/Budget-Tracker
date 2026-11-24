package com.duduezn.budgettracker.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthRangeTest {

    private fun date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private fun dayOfMonth(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(Calendar.DAY_OF_MONTH)

    private fun monthOf(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }
            .get(Calendar.MONTH)

    @Test
    fun `debut au premier couvre le mois calendaire`() {
        val range = MonthRange.current(now = date(2026, 11, 15), startDay = 1)

        assertEquals(2026, Calendar.getInstance().apply { timeInMillis = range.first }
            .get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, monthOf(range.first))
        assertEquals(1, dayOfMonth(range.first))
        assertEquals(Calendar.NOVEMBER, monthOf(range.last))
        assertEquals(30, dayOfMonth(range.last))
    }

    @Test
    fun `debut au 25 avant le 25 appartient au cycle precedent`() {
        val range = MonthRange.current(now = date(2026, 11, 20), startDay = 25)

        assertEquals(Calendar.OCTOBER, monthOf(range.first))
        assertEquals(25, dayOfMonth(range.first))
        assertEquals(Calendar.NOVEMBER, monthOf(range.last))
        assertEquals(24, dayOfMonth(range.last))
    }

    @Test
    fun `debut au 25 apres le 25 demarre le cycle courant`() {
        val range = MonthRange.current(now = date(2026, 11, 26), startDay = 25)

        assertEquals(Calendar.NOVEMBER, monthOf(range.first))
        assertEquals(25, dayOfMonth(range.first))
        assertEquals(Calendar.DECEMBER, monthOf(range.last))
        assertEquals(24, dayOfMonth(range.last))
    }

    @Test
    fun `le jour du salaire appartient au nouveau cycle`() {
        val range = MonthRange.current(now = date(2026, 11, 25), startDay = 25)

        assertEquals(Calendar.NOVEMBER, monthOf(range.first))
        assertEquals(25, dayOfMonth(range.first))
    }

    @Test
    fun `la date du jour est toujours incluse dans le cycle`() {
        val now = date(2026, 11, 20)
        val range = MonthRange.current(now = now, startDay = 25)

        assertTrue(now in range)
    }

    @Test
    fun `un jour de debut invalide est ramene dans les bornes`() {
        val range = MonthRange.current(now = date(2026, 11, 15), startDay = 40)

        assertEquals(28, dayOfMonth(range.first))
    }

    @Test
    fun `le libelle par defaut affiche le mois`() {
        val label = MonthRange.label(now = date(2026, 11, 15), startDay = 1)

        assertEquals("Novembre 2026", label)
    }

    @Test
    fun `le libelle d un cycle decale affiche les deux dates`() {
        val label = MonthRange.label(now = date(2026, 11, 20), startDay = 25)

        assertTrue(label.startsWith("25 oct."))
        assertTrue(label.contains("24 nov."))
        assertTrue(label.contains("2026"))
    }
}
