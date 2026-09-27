package com.pesaflow.app.parsers

import com.pesaflow.app.ui.onboarding.visibleBranches
import org.junit.Assert.*
import org.junit.Test


// Twenty branches exist; each case sees only its relevant subset.
class WelcomeBranchesTest {

    private fun answers(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @Test
    fun `parents walker skips rent fare times and helb`() {
        val seen = visibleBranches(
            answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF")
        )
        assertFalse(seen.contains("rent"))
        assertFalse(seen.contains("fare"))
        assertFalse(seen.contains("classTimes"))
        assertFalse(seen.contains("helb"))
        assertTrue(seen.contains("upkeep"))
        assertTrue(seen.size < 20)
    }

    @Test
    fun `far renter on helb sees the full run`() {
        val seen = visibleBranches(
            answers("home" to "Alone", "commute" to "Far", "fundSource" to "HELB")
        )
        assertTrue(seen.contains("rent"))
        assertTrue(seen.contains("fare"))
        assertTrue(seen.contains("classTimes"))
        assertTrue(seen.contains("helb"))
        assertEquals(20, seen.size)
    }

    @Test
    fun `hostel near keeps fare but the count still drops`() {
        val full = visibleBranches(answers("home" to "Alone", "commute" to "Far", "fundSource" to "HELB"))
        val slim = visibleBranches(answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF"))
        assertTrue(slim.size < full.size)
        assertTrue(full.size - slim.size >= 3)
    }

    @Test
    fun `identity branches always ask`() {
        val seen = visibleBranches(answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF"))
        listOf("name", "nickname", "university", "semester", "home", "commute", "pocket", "save")
            .forEach { assertTrue("$it should always ask", seen.contains(it)) }
    }
}
