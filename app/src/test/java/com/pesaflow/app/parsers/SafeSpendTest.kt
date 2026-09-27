package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.Commute
import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.ProfileSignals
import com.pesaflow.app.data.finance.SnapshotInput
import com.pesaflow.app.data.finance.buildSnapshot
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


// Safe-to-spend is personal: no fixed truths about what a day should cost.
// Habit (robust burn) vs statement (declared budgets) take the max; the
// buffer buys days of YOUR burn sized by income assurance + commute.
class SafeSpendTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private fun tx(
        amount: Double,
        type: TransactionType,
        category: String = "Food",
        merchant: String = "Test",
        daysAgo: Long = 0
    ) = Transaction(
        amount = amount, type = type, category = category, dateTimestamp = now - daysAgo * day,
        merchant = merchant, description = "", paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MANUAL
    )

    private fun budget(category: String, monthly: Double) = Budget(
        category = category, limitAmount = monthly, type = BudgetType.MONTHLY,
        startTimestamp = now - 30 * day, endTimestamp = now + 30 * day
    )

    private fun snap(
        txs: List<Transaction>,
        budgets: List<Budget> = emptyList(),
        sources: List<IncomeSource> = emptyList(),
        profile: ProfileSignals = ProfileSignals()
    ) = buildSnapshot(
        SnapshotInput(
            txs = txs, budgets = budgets, incomeSources = sources, profile = profile,
            nowMs = now
        )
    )

    // Declared word respected: stated Transport budget binds needs on thin history.
    @Test
    fun `stated budgets bind needs where habit is thin`() {
        val txs = listOf(tx(20000.0, TransactionType.INCOME, "Salary", "Employer"))
        val bare = snap(txs)
        val stated = snap(txs, budgets = listOf(budget("Transport", 9000.0)))
        assertEquals(Money.of(20000.0), bare.safeToday)
        assertTrue(stated.safeToday < bare.safeToday)
    }

    // Assurance priced: same ledger, confirmed salary vs nothing declared.
    @Test
    fun `confirmed income earns a smaller buffer than unassured`() {
        val cal = Calendar.getInstance()
        val paydays = (0..3).map { back ->
            val c = (cal.clone() as Calendar).apply {
                add(Calendar.MONTH, -back)
                set(Calendar.DAY_OF_MONTH, 5)
            }
            tx(20000.0, TransactionType.INCOME, "Salary", "Employer", daysAgo = (now - c.timeInMillis) / day)
        }
        val spends = (1..10).map { tx(400.0, TransactionType.EXPENSE, "Food", daysAgo = it.toLong()) }
        val txs = paydays + spends
        val assured = snap(
            txs,
            sources = listOf(IncomeSource(kind = "JOB", label = "Employer", expectedAmount = 20000.0, frequency = "MONTHLY"))
        )
        val unassured = snap(txs)
        assertTrue(assured.reliableIncomeAhead > Money.ZERO)
        assertTrue(assured.safeToday > unassured.safeToday)
    }

    // University used: long commutes hold a bigger fare buffer than short ones.
    @Test
    fun `long commute holds more buffer than short`() {
        val txs = listOf(tx(20000.0, TransactionType.INCOME, "Salary", "Employer")) +
            (1..10).map { tx(400.0, TransactionType.EXPENSE, "Food", daysAgo = it.toLong()) }
        val long = snap(txs, profile = ProfileSignals(commute = Commute.LONG))
        val short = snap(txs, profile = ProfileSignals(commute = Commute.SHORT))
        assertTrue(long.safeToday < short.safeToday)
        assertTrue(long.riskBuffer > short.riskBuffer)
    }

    // Cold start invents nothing: income-only ledger keeps every shilling.
    @Test
    fun `cold start keeps all cash with no burn and no buffer`() {
        val s = snap(listOf(tx(10000.0, TransactionType.INCOME, "Salary", "Employer")))
        assertEquals(Money.of(10000.0), s.liquid)
        assertEquals(Money.of(10000.0), s.safeToday)
        assertEquals(Money.ZERO, s.riskBuffer)
    }

    // 800/day vs 200/day on the same income: ordering sane, both non-negative.
    @Test
    fun `heavier burn means tighter safe to spend`() {
        val income = listOf(tx(20000.0, TransactionType.INCOME, "Salary", "Employer"))
        val heavy = snap(income + (1..10).map { tx(800.0, TransactionType.EXPENSE, "Food", daysAgo = it.toLong()) })
        val light = snap(income + (1..10).map { tx(200.0, TransactionType.EXPENSE, "Food", daysAgo = it.toLong()) })
        assertTrue(heavy.safeToday < light.safeToday)
        assertTrue(heavy.safeToday >= Money.ZERO)
        assertTrue(light.safeToday <= light.liquid)
    }
}
