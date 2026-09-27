package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.applyContactMemory
import com.pesaflow.app.data.ledger.ContactMemory
import com.pesaflow.app.data.ledger.normalizeContact
import com.pesaflow.app.data.ledger.readContactMemories
import com.pesaflow.app.data.ledger.suggestMemory
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


class ContactMemoryTest {

    private fun tx(merchant: String, amount: Double, type: TransactionType, category: String = "Other") =
        PendingTransaction(
            amount = amount, type = type, category = category, merchant = merchant,
            dateTimestamp = 1_700_000_000_000L, paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.MPESA_SMS, sourceTransactionId = null, rawText = ""
        )

    @Test
    fun `normalize trims and title-cases`() {
        assertEquals("Nancy", normalizeContact("  nancy  "))
        assertEquals("Nancy Wanjiru", normalizeContact("NANCY   wanjiru"))
        assertEquals("Naivas Moi Avenue", normalizeContact("naivas  moi avenue"))
    }

    @Test
    fun `scope IN leaves money-out alone`() {
        val out = applyContactMemory(
            listOf(tx("Nancy", 200.0, TransactionType.EXPENSE, "Food")),
            mapOf("Nancy" to ContactMemory("Mother", "Upkeep", "IN"))
        ).first()
        assertEquals("Nancy · Mother", out.displayMerchant)
        assertEquals("Food", out.category)
    }

    @Test
    fun `scope OUT leaves money-in alone`() {
        val out = applyContactMemory(
            listOf(tx("Nancy", 1000.0, TransactionType.INCOME, "Salary")),
            mapOf("Nancy" to ContactMemory("Mother", "Food", "OUT"))
        ).first()
        assertEquals("Nancy · Mother", out.displayMerchant)
        assertEquals("Salary", out.category)
    }

    @Test
    fun `in-scope category stamps`() {
        val out = applyContactMemory(
            listOf(tx("Nancy", 1000.0, TransactionType.INCOME, "Salary")),
            mapOf("Nancy" to ContactMemory("Mother", "Upkeep", "IN"))
        ).first()
        assertEquals("Upkeep", out.category)
        assertEquals("Upkeep", out.displayCategory)
    }

    @Test
    fun `blank category means ask me`() {
        val out = applyContactMemory(
            listOf(tx("Kevin", 80.0, TransactionType.EXPENSE, "Transport")),
            mapOf("Kevin" to ContactMemory("Friend", "", "BOTH"))
        ).first()
        assertEquals("Kevin · Friend", out.displayMerchant)
        assertEquals("Transport", out.category)
    }

    @Test
    fun `existing face is never overwritten`() {
        val row = tx("Nancy", 200.0, TransactionType.EXPENSE).copy(displayMerchant = "Nancy · Mom")
        val out = applyContactMemory(
            listOf(row), mapOf("Nancy" to ContactMemory("Mother", "Upkeep", "BOTH"))
        ).first()
        assertEquals("Nancy · Mom", out.displayMerchant)
    }

    @Test
    fun `mother suggests upkeep in`() {
        assertEquals(ContactMemory("", "Upkeep", "IN"), suggestMemory("Mother"))
    }

    @Test
    fun `landlord suggests rent out`() {
        assertEquals(ContactMemory("", "Rent", "OUT"), suggestMemory("my landlord"))
    }

    @Test
    fun `vehicle words beat sacco`() {
        assertEquals(ContactMemory("", "Transport", "OUT"), suggestMemory("Matatu sacco"))
        assertEquals(ContactMemory("", "Savings", "OUT"), suggestMemory("my chama sacco"))
    }

    @Test
    fun `friends stay blank`() {
        assertEquals(ContactMemory("", "", "BOTH"), suggestMemory("Kevin roommate"))
    }

    @Test
    fun `memories read back sanitized`() {
        val all = mapOf(
            "contact_label_Nancy" to "Mother",
            "contact_cat_Nancy" to "Upkeep",
            "contact_scope_Nancy" to "in",
            "contact_scope_Lonely" to "OUT",
            "other_key" to "x"
        )
        val mem = readContactMemories(all)
        assertEquals(1, mem.size)
        assertEquals(ContactMemory("Mother", "Upkeep", "IN"), mem["Nancy"])
    }
}
