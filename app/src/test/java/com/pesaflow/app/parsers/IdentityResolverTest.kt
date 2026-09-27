package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.resolveIdentities
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


// First-scan namings resolve every later row: alias becomes the face,
// learned category becomes the truth, fee flags survive untouched.
class IdentityResolverTest {

    private fun tx(merchant: String, category: String = "Other", subcategory: String = "") =
        PendingTransaction(
            amount = 200.0, type = TransactionType.EXPENSE, category = category,
            subcategory = subcategory, merchant = merchant, dateTimestamp = 1_700_000_000_000L,
            paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MPESA_SMS,
            sourceTransactionId = null, rawText = ""
        )

    private val aliases = mapOf("nancy wanjiru" to "Mom")
    private val cats = mapOf("nancy wanjiru" to "Food")

    @Test
    fun `named sender arrives resolved`() {
        val out = resolveIdentities(
            listOf(tx("Nancy Wanjiru")),
            { aliases[it.trim().lowercase()] },
            { cats[it.trim().lowercase()] }
        ).first()
        assertEquals("Mom", out.displayMerchant)
        assertEquals("Food", out.category)
        assertEquals("Food", out.displayCategory)
        assertEquals("Nancy Wanjiru", out.merchant)
    }

    @Test
    fun `alias without category keeps parser category`() {
        val out = resolveIdentities(
            listOf(tx("Kevin", "Transport")),
            { "Bro" }, { null }
        ).first()
        assertEquals("Bro", out.displayMerchant)
        assertEquals("Transport", out.category)
    }

    @Test
    fun `stranger passes through untouched`() {
        val row = tx("Stranger")
        val out = resolveIdentities(listOf(row), { null }, { null }).first()
        assertEquals(row, out)
    }

    @Test
    fun `fee flag survives resolution`() {
        val out = resolveIdentities(
            listOf(tx("Nancy Wanjiru", subcategory = "Transaction Cost")),
            { aliases[it.trim().lowercase()] },
            { cats[it.trim().lowercase()] }
        ).first()
        assertEquals("Transaction Cost", out.subcategory)
        assertEquals("Mom", out.displayMerchant)
    }

    @Test
    fun `blank alias is ignored`() {
        val out = resolveIdentities(listOf(tx("Nancy Wanjiru")), { "  " }, { null }).first()
        assertEquals("", out.displayMerchant)
        assertEquals("Other", out.category)
    }
}
