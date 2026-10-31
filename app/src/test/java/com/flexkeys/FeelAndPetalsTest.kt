package com.flexkeys

import com.flexkeys.layout.KeyType
import com.flexkeys.service.KeyFeel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeelAndPetalsTest {

    @Test fun classicKeepsTheOlderStyle() {
        for (type in KeyType.values()) assertTrue(KeyFeel.steps(KeyFeel.CLASSIC, type, false).isEmpty())
    }

    @Test fun everyFeelHasAPatternForEveryKey() {
        for (feel in KeyFeel.SOFT..KeyFeel.SILKY) {
            for (type in KeyType.values()) {
                for (long in listOf(false, true)) {
                    val steps = KeyFeel.steps(feel, type, long)
                    assertTrue("feel $feel $type long=$long", steps.isNotEmpty())
                    assertEquals("first pulse has no delay", 0, steps.first().delayMs)
                    for (s in steps) assertTrue(s.scale in 0.05f..1f)
                }
            }
        }
    }

    @Test fun mechanicalHasTwoPulsesForALetter() {
        assertEquals(2, KeyFeel.steps(KeyFeel.MECHANICAL, KeyType.CHAR, false).size)
        assertEquals(1, KeyFeel.steps(KeyFeel.SILKY, KeyType.CHAR, false).size)
    }

    @Test fun feelNamesMatchThePresets() {
        assertEquals(5, KeyFeel.names.size)
    }
}
