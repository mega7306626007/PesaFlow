package com.pesaflow.app.parsers

import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.IncomeSourceStore
import org.junit.Assert.*
import org.junit.Test


// Income math: monthly equivalents and budget inclusion are pure and pinned.
class IncomeSourceTest {

    @Test
    fun `monthly equivalents scale daily and weekly`() {
        assertEquals(3000.0, IncomeSource(kind = "HUSTLE", expectedAmount = 100.0, frequency = "DAILY").monthlyEquivalent(), 0.001)
        assertEquals(2000.0, IncomeSource(kind = "JOB", expectedAmount = 500.0, frequency = "WEEKLY").monthlyEquivalent(), 0.001)
        assertEquals(15000.0, IncomeSource(kind = "JOB", expectedAmount = 15000.0, frequency = "MONTHLY").monthlyEquivalent(), 0.001)
        assertEquals(40000.0, IncomeSource(kind = "HELB_MPESA", expectedAmount = 40000.0, frequency = "ONCE").monthlyEquivalent(), 0.001)
    }

    @Test
    fun `fuliza excluded unless explicitly opted in`() {
        assertEquals(0.0, IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "FULIZA", expectedAmount = 2000.0)), 0.001)
        assertEquals(
            2000.0,
            IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "FULIZA", expectedAmount = 2000.0, useInBudget = true)),
            0.001
        )
    }

    @Test
    fun `ordinary sources count at monthly equivalent`() {
        assertEquals(
            3000.0,
            IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "HUSTLE", expectedAmount = 100.0, frequency = "DAILY")),
            0.001
        )
    }
}
