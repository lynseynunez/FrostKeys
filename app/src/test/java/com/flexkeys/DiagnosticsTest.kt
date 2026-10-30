package com.flexkeys

import com.flexkeys.diagnostics.Diagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun statsOfNothing() {
        val s = Diagnostics.stats(LongArray(0), 1.0)
        assertEquals(0, s.count)
        assertEquals(0.0, s.max, 0.0)
    }

    @Test
    fun medianAndPercentile() {
        // 1..100 microseconds, reported in milliseconds.
        val values = LongArray(100) { (it + 1).toLong() }
        val s = Diagnostics.stats(values, 1000.0)
        assertEquals(100, s.count)
        assertEquals(0.050, s.median, 0.002)
        assertEquals(0.095, s.p95, 0.002)
        assertEquals(0.100, s.max, 1e-9)
    }

    @Test
    fun ringKeepsTheLatestValues() {
        val ring = Diagnostics.Ring(3)
        for (i in 1L..5L) ring.add(i)
        assertEquals(5L, ring.total())
        assertEquals(listOf(3L, 4L, 5L), ring.snapshot().sorted())
        ring.clear()
        assertTrue(ring.snapshot().isEmpty())
    }

    @Test
    fun budgetVerdict() {
        assertEquals("OK", Diagnostics.verdict(true))
        assertEquals("OVER BUDGET", Diagnostics.verdict(false))
    }
}
