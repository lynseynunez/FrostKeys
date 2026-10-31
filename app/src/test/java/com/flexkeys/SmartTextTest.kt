package com.flexkeys

import com.flexkeys.suggest.Calc
import com.flexkeys.suggest.SmartClip
import com.flexkeys.suggest.SmartText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmartTextTest {

    @Test fun arithmetic() {
        assertEquals(96.0, Calc.evaluate("12*8")!!, 1e-9)
        assertEquals(14.0, Calc.evaluate("2 + 3 * 4")!!, 1e-9)
        assertEquals(20.0, Calc.evaluate("(2 + 3) * 4")!!, 1e-9)
        assertEquals(8.0, Calc.evaluate("2^3")!!, 1e-9)
        assertEquals(2.5, Calc.evaluate("5 / 2")!!, 1e-9)
        assertEquals(12.0, Calc.evaluate("3x4")!!, 1e-9)
        assertEquals(0.75, Calc.evaluate("1,5 / 2")!!, 1e-9)
        assertNull(Calc.evaluate("5 / 0"))
        assertNull(Calc.evaluate("5 +"))
        assertNull(Calc.evaluate("abc"))
    }

    @Test fun formatting() {
        assertEquals("96", Calc.format(96.0))
        assertEquals("3.1", Calc.format(3.1))
        assertEquals("0.33", Calc.format(1.0 / 3))
        assertEquals("-2.5", Calc.format(-2.5))
        assertEquals("1000000", Calc.format(1_000_000.0))
    }

    @Test fun sumsAtTheEndOfTheText() {
        val hit = SmartText.findCalc("so that is 12*8")!!
        assertEquals("= 96", hit.label)
        assertEquals(4, hit.replaceLength)
        assertEquals("96", hit.insert)

        val withEquals = SmartText.findCalc("12*8=")!!
        assertEquals(0, withEquals.replaceLength)
        assertEquals(" 96", withEquals.insert)

        assertEquals("= 20", SmartText.findCalc("(2 + 3) * 4")!!.label)
        assertEquals("= 6", SmartText.findCalc("10 - 4")!!.label)
    }

    @Test fun thingsThatAreNotSums() {
        assertNull(SmartText.findCalc("call 555-1234"))
        assertNull(SmartText.findCalc("on 2024-10-30"))
        assertNull(SmartText.findCalc("see you 10/30"))
        assertNull(SmartText.findCalc("I have 12"))
        assertNull(SmartText.findCalc("hello world"))
        assertNull(SmartText.findCalc("12*8 and then some"))
    }

    @Test fun unitConversions() {
        assertEquals("3.11 mi", SmartText.findCalc("it is 5 km")!!.label)
        assertEquals(" (3.11 mi)", SmartText.findCalc("it is 5km")!!.insert)
        assertEquals("(3.11 mi)", SmartText.findCalc("it is 5 km ")!!.insert)
        assertEquals("22.22 °C", SmartText.findCalc("72°F")!!.label)
        assertEquals("160.93 km", SmartText.findCalc("100 miles")!!.label)
        assertEquals("2.2 lb", SmartText.findCalc("1 kg")!!.label)
        assertNull(SmartText.findCalc("5 kilometers away"))
    }

    @Test fun copiedThingsWorthOffering() {
        assertEquals(SmartClip.Kind.EMAIL, SmartClip.find("someone@example.com")!!.kind)
        assertEquals(SmartClip.Kind.LINK, SmartClip.find("https://example.com/a?b=1")!!.kind)
        assertEquals(SmartClip.Kind.LINK, SmartClip.find("www.example.com")!!.kind)
        assertEquals(SmartClip.Kind.PHONE, SmartClip.find("(555) 123-4567")!!.kind)
        assertEquals(SmartClip.Kind.PHONE, SmartClip.find("+1 555 123 4567")!!.kind)
        assertEquals(SmartClip.Kind.CODE, SmartClip.find("Your code is 482913")!!.kind)
        assertNotNull(SmartClip.find("  someone@example.com  "))
    }

    @Test fun ordinaryTextIsNotOffered() {
        assertNull(SmartClip.find("Hi there! Quick update, mail me at someone@example.com tomorrow"))
        assertNull(SmartClip.find("see https://example.com for more"))
        assertNull(SmartClip.find("123"))
        assertNull(SmartClip.find("P.O. Box 20825"))
        assertNull(SmartClip.find(""))
    }
}

class LearnWordTest {

    @org.junit.Test fun protectedListGrowsOnce() {
        val engine = com.flexkeys.suggest.SuggestionEngine
        org.junit.Assert.assertEquals("brio", engine.addProtected("", "brio"))
        org.junit.Assert.assertEquals("gpu\nbrio", engine.addProtected("gpu", "brio"))
        // Already there in another capitalization: unchanged.
        org.junit.Assert.assertEquals("Brio", engine.addProtected("Brio", "brio"))
        org.junit.Assert.assertEquals("gpu", engine.addProtected("gpu", "  "))
    }

    @org.junit.Test fun learnOfferChip() {
        val s = com.flexkeys.suggest.Suggestions.EMPTY.withLearn("brio")
        org.junit.Assert.assertEquals(listOf("", "Add “brio”", ""), s.slots)
        org.junit.Assert.assertEquals(1, s.learnIndex)
    }
}

class LearnWordEndToEndTest {

    /** A word that is one slip from a common word is corrected, until it is added for good. */
    @org.junit.Test fun addedWordIsNoLongerCorrected() {
        val engine = TestData.engine()
        val word = "brio"
        org.junit.Assert.assertNotNull("expected the test word to be corrected at first", engine.suggest(word, null, true, false).correction)
        val raw = engine.addToVocabulary(word, "")
        engine.setRules(raw.lines().map { it.lowercase() }.toSet(), emptyMap())
        org.junit.Assert.assertNull(engine.suggest(word, null, true, false).correction)
        org.junit.Assert.assertTrue(engine.isKnown(word))
    }
}
