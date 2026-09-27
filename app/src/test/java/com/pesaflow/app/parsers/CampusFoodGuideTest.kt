package com.pesaflow.app.parsers

import com.pesaflow.app.data.meals.spotsFor
import org.junit.Assert.*
import org.junit.Test


class CampusFoodGuideTest {

    @Test
    fun `uon main resolves to uon pack with smocha seventy`() {
        val spots = spotsFor("UoN Main")
        assertTrue(spots.isNotEmpty())
        assertTrue(spots.all { it.university == "UoN" })
        val smocha = spots.firstOrNull { it.item == "Smocha" }
        assertNotNull(smocha)
        assertEquals(70.0, smocha!!.price, 0.001)
        assertTrue(smocha.spot.isNotBlank())
    }

    @Test
    fun `kenyatta resolves to ku before nairobi rule`() {
        val spots = spotsFor("Kenyatta University")
        assertTrue(spots.isNotEmpty())
        assertTrue(spots.all { it.university == "KU" })
    }

    @Test
    fun `jkuat maseno egerton resolve`() {
        assertTrue(spotsFor("JKUAT Juja").all { it.university == "JKUAT" })
        assertTrue(spotsFor("JKUAT Juja").isNotEmpty())
        assertTrue(spotsFor("Maseno").all { it.university == "Maseno" })
        assertTrue(spotsFor("Egerton Njoro").all { it.university == "Egerton" })
    }

    @Test
    fun `unknown or blank school yields no pack`() {
        assertTrue(spotsFor("Strathmore").isEmpty())
        assertTrue(spotsFor("").isEmpty())
        assertTrue(spotsFor("   ").isEmpty())
    }

    @Test
    fun `every spot has a priced plate and meal type`() {
        listOf("UoN", "KU", "JKUAT", "Maseno", "Egerton").forEach { uni ->
            val spots = spotsFor(uni)
            assertTrue(spots.isNotEmpty())
            spots.forEach {
                assertTrue(it.item.isNotBlank())
                assertTrue(it.price > 0)
                assertTrue(it.mealType in setOf("Breakfast", "Lunch", "Supper", "Snack"))
            }
        }
    }
}
