package com.pesaflow.app.parsers

import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test


// Transaction costs are xx.xx micro-amounts capped at KSh 60. They ride in
// displayCategory so budget math can skip them while the ledger keeps them.
class TransactionCostTest {

    @Test
    fun `single digit decimal flags transaction cost`() {
        val sms = "QWERTY1234 Confirmed. KSh7.00 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Transaction Cost", tx!!.subcategory)
    }

    @Test
    fun `two digit decimal under 60 flags transaction cost`() {
        val sms = "QWERTY1234 Confirmed. KSh28.00 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Transaction Cost", tx!!.subcategory)
    }

    @Test
    fun `zero point amount flags transaction cost`() {
        val sms = "QWERTY1234 Confirmed. KSh0.75 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("Transaction Cost", tx!!.subcategory)
    }

    @Test
    fun `hundreds with decimals are not transaction costs`() {
        val sms = "QWERTY1234 Confirmed. KSh500.00 paid to Naivas Supermarket. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("", tx!!.subcategory)
    }

    @Test
    fun `xxx point xx decimals are not transaction costs`() {
        val sms = "QWERTY1234 Confirmed. KSh123.45 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("", tx!!.subcategory)
    }

    @Test
    fun `decimal over 60 cap is not a transaction cost`() {
        val sms = "QWERTY1234 Confirmed. KSh61.00 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("", tx!!.subcategory)
    }

    @Test
    fun `whole small amount without decimal is not a transaction cost`() {
        val sms = "QWERTY1234 Confirmed. KSh7 paid to Shop. on 12/9/26 at 9:15 AM"
        val tx = MpesaParser.parseMessage(sms)
        assertNotNull(tx)
        assertEquals("", tx!!.subcategory)
    }
}
