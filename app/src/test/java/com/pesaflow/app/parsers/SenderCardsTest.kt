package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.groupSenderCards
import com.pesaflow.app.data.parsers.isKnownEntity
import org.junit.Assert.*
import org.junit.Test


class SenderCardsTest {

    private val day = 24L * 60 * 60 * 1000
    private val base = 1_700_000_000_000L

    private fun tx(merchant: String, amount: Double, type: TransactionType, daysAgo: Long, category: String = "Food") =
        PendingTransaction(
            amount = amount, type = type, category = category, merchant = merchant,
            dateTimestamp = base - daysAgo * day, paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.MPESA_SMS, sourceTransactionId = null, rawText = ""
        )

    @Test
    fun `rows group into one card per sender busiest first`() {
        val rows = listOf(
            tx("Nancy", 200.0, TransactionType.EXPENSE, 1),
            tx("Nancy", 150.0, TransactionType.EXPENSE, 5),
            tx("Nancy", 1000.0, TransactionType.INCOME, 9),
            tx("Kevin", 50.0, TransactionType.EXPENSE, 2)
        )
        val cards = groupSenderCards(rows)
        assertEquals(2, cards.size)
        assertEquals("Nancy", cards[0].merchant)
        assertEquals(3, cards[0].count)
        assertEquals(350.0, cards[0].expenseTotal, 0.001)
        assertEquals(1000.0, cards[0].incomeTotal, 0.001)
        assertEquals(base - 9 * day, cards[0].firstSeen)
        assertEquals(base - 1 * day, cards[0].lastSeen)
        assertEquals("Kevin", cards[1].merchant)
    }

    @Test
    fun `suggested category is the sender majority`() {
        val rows = listOf(
            tx("Nancy", 200.0, TransactionType.EXPENSE, 1, "Food"),
            tx("Nancy", 150.0, TransactionType.EXPENSE, 2, "Food"),
            tx("Nancy", 80.0, TransactionType.EXPENSE, 3, "Airtime")
        )
        assertEquals("Food", groupSenderCards(rows)[0].suggestedCategory)
    }

    @Test
    fun `named senders never surface`() {
        val rows = listOf(tx("Nancy", 200.0, TransactionType.EXPENSE, 1))
        assertTrue(groupSenderCards(rows) { it.equals("nancy", true) }.isEmpty())
        assertEquals(1, groupSenderCards(rows).size)
    }

    @Test
    fun `system entities and unknown party never surface`() {
        assertTrue(isKnownEntity("Safaricom PLC"))
        assertTrue(isKnownEntity("KPLC Tokens"))
        assertTrue(isKnownEntity("HELB Disbursement"))
        assertTrue(isKnownEntity("Unknown Party"))
        assertTrue(isKnownEntity("123456"))
        assertTrue(isKnownEntity("Till 789012"))
        assertFalse(isKnownEntity("Nancy Wanjiru"))
        assertFalse(isKnownEntity("KIOSK 42"))
    }

    @Test
    fun `empty scan yields no cards`() {
        assertTrue(groupSenderCards(emptyList()).isEmpty())
    }
}
