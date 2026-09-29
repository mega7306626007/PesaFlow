package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.budgetWindowRange
import com.pesaflow.app.data.models.BudgetType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Budget progress reads the current period — never stored windows. */
class BudgetWindowsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `monthly window excludes last month rows`() {
        // Sep 13: August spending must not pace September budgets.
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val w = budgetWindowRange(BudgetType.MONTHLY, now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 5) in w)
        assertFalse(at(2026, Calendar.AUGUST, 20) in w)
        assertFalse(at(2026, Calendar.OCTOBER, 2) in w)
    }

    @Test
    fun `weekly window is monday to monday`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0) // Wednesday
        val w = budgetWindowRange(BudgetType.WEEKLY, now)
        assertEquals(at(2026, Calendar.SEPTEMBER, 7, 0, 0), w.startInclusive)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 0, 0), w.endExclusive)
    }

    @Test
    fun `daily window is today only`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val w = budgetWindowRange(BudgetType.DAILY, now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 9, 23, 59) in w)
        assertFalse(at(2026, Calendar.SEPTEMBER, 8, 23, 59) in w)
    }

    @Test
    fun `semester uses the profile window when present`() {
        val now = at(2026, Calendar.OCTOBER, 10, 9, 0)
        val start = at(2026, Calendar.SEPTEMBER, 7, 0, 0)
        val end = at(2026, Calendar.DECEMBER, 20, 0, 0)
        val w = budgetWindowRange(BudgetType.SEMESTER, now, start, end)
        assertEquals(start, w.startInclusive)
        assertTrue(at(2026, Calendar.SEPTEMBER, 1) !in w)
        assertTrue(at(2026, Calendar.OCTOBER, 1) in w)
    }

    @Test
    fun `semester without profile falls back to rolling 120 days`() {
        val now = at(2026, Calendar.OCTOBER, 10, 9, 0)
        val w = budgetWindowRange(BudgetType.SEMESTER, now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 1) in w)
        assertFalse(at(2026, Calendar.MAY, 1) in w)
    }

    @Test
    fun `annual window is the calendar year`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val w = budgetWindowRange(BudgetType.ANNUAL, now)
        assertTrue(at(2026, Calendar.MARCH, 3) in w)
        assertFalse(at(2025, Calendar.DECEMBER, 31) in w)
    }
}
