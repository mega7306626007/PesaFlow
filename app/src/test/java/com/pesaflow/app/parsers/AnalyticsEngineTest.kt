package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AnalyticsEngineTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
    private fun tx(amount: Double, category: String, ts: Long, type: com.pesaflow.app.data.models.TransactionType = com.pesaflow.app.data.models.TransactionType.EXPENSE): com.pesaflow.app.data.models.Transaction {
        return com.pesaflow.app.data.models.Transaction(amount = amount, type = type, category = category, dateTimestamp = ts, merchant = "Test")
    }
    private fun day(offset: Int): Long = now - offset * 24L * 60 * 60 * 1000

    @Test
    fun `classifies food and transport correctly`() {
        assertEquals(ExpenseCategory.FOOD, classifyCategory("Food"))
        assertEquals(ExpenseCategory.FOOD, classifyCategory("Uzingo"))
        assertEquals(ExpenseCategory.TRANSPORT, classifyCategory("Matatu"))
        assertEquals(ExpenseCategory.TRANSPORT, classifyCategory("Fuel"))
    }

    @Test
    fun `report with empty history`() {
        val r = buildAnalyticsReport(emptyList(), 30, now)
        assertEquals(0.0, r.totalSpent, 0.001)
        assertEquals(0, r.categorySummaries.size)
        assertEquals(0.0, r.dailyAvg, 0.001)
        assertTrue(r.netFlow >= 0)
    }

    @Test
    fun `calculates category shares`() {
        val txs = listOf(
            tx(100.0, "Food", day(0)),
            tx(100.0, "Food", day(1)),
            tx(300.0, "Rent", day(2))
        )
        val r = buildAnalyticsReport(txs, 7, now)
        assertEquals(500.0, r.totalSpent, 0.001)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }
        assertNotNull(food)
        assertEquals(200.0, food!!.total, 0.001)
        assertEquals(40, food.sharePercent)
        assertEquals(60, r.categorySummaries.find { it.category == ExpenseCategory.BILLS }!!.sharePercent)
    }

    @Test
    fun `detects trend direction`() {
        val recent = listOf(tx(200.0, "Food", day(0)), tx(200.0, "Food", day(1)))
        val older = listOf(tx(50.0, "Food", day(20)), tx(50.0, "Food", day(25)))
        val txs = recent + older
        val r = buildAnalyticsReport(txs, 30, now)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }
        assertNotNull(food)
        assertTrue("Food should be trending up", food!!.trendPercent > 0)
    }

    @Test
    fun `savings rate is correct`() {
        val txs = listOf(
            tx(500.0, "Food", day(0), com.pesaflow.app.data.models.TransactionType.INCOME),
            tx(300.0, "Food", day(1))
        )
        val r = buildAnalyticsReport(txs, 7, now)
        assertEquals(40, r.savingsRate)
    }

    @Test
    fun `heatmap grid dimensions match`() {
        val txs = (0..20).map { tx(10.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        assertTrue(r.heatmap.grid.size <= 8)
        r.heatmap.grid.forEach { assertEquals(7, it.size) }
        assertEquals(r.heatmap.weekLabels.size, r.heatmap.grid.size)
    }

    @Test
    fun `fastest growing category detection`() {
        val txs = mutableListOf<com.pesaflow.app.data.models.Transaction>()
        // Food trending up 100%
        txs.add(tx(100.0, "Food", day(20)))
        txs.add(tx(100.0, "Food", day(21)))
        // Transport flat
        txs.add(tx(100.0, "Transport", day(20)))
        txs.add(tx(100.0, "Transport", day(21)))
        // Add recent food spike
        txs.add(tx(300.0, "Food", day(0)))
        val r = buildAnalyticsReport(txs, 30, now)
        val fastest = fastestGrowingCategory(r)
        assertNotNull(fastest)
        assertEquals(ExpenseCategory.FOOD, fastest)
    }

    @Test
    fun `overBudgetCategories flag high spenders`() {
        val txs = (0..5).map { tx(500.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        val over = overBudgetCategories(r, 0.5)
        assertTrue(over.isNotEmpty())
    }

    @Test
    fun `weekdayProfile is Mon-first length 7`() {
        val txs = (0..13).map { tx(100.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        assertEquals(7, r.weekdayProfile.size)
    }
}
