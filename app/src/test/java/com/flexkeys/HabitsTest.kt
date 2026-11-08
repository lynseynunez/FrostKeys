package com.flexkeys

import com.flexkeys.suggest.HabitMemory
import com.flexkeys.suggest.SpaceSlips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitsTest {

    private val known = setOf("bro", "what", "so", "hello", "world", "the", "next", "that", "it", "ok", "text", "me", "later", "see", "you", "dr", "smith", "call", "com", "now")
    private fun slip(before: String, typed: String) = SpaceSlips.periodSlip(before, typed) { it in known }

    @Test fun aStrayPeriodBetweenWordsIsASlip() {
        assertNotNull(slip("so bro.", "what"))
        assertEquals("bro", slip("so bro.", "what")!!.previousWord)
        assertNotNull("capital from auto-capitalization", slip("so bro.", "What"))
        assertNotNull(slip("hello.", "world"))
        assertNotNull(slip("text me.", "later"))
    }

    @Test fun realPeriodsAreLeftAlone() {
        assertNull("web address", slip("see google.", "com"))
        assertNull("web address", slip("so bro.", "com"))
        assertNull("abbreviation", slip("see dr.", "smith"))
        assertNull("initials", slip("the u.s.", "next"))
        assertNull("a.m.", slip("at 5 a.", "mm"))
        assertNull("number", slip("it costs 3.", "50"))
        assertNull("one letter", slip("so bro.", "x"))
        assertNull("email", slip("me@so.", "what"))
        assertNull("not a known word", slip("so zzzz.", "what"))
        assertNull("no period", slip("so bro ", "what"))
        assertNull("a space after the period", slip("so bro. ", "what"))
    }

    @Test fun habitBonusGrowsWithSightingsAndForgets() {
        val habits = HabitMemory(null)
        assertEquals(0f, habits.bonus('m', 'n'), 0.001f)
        habits.recordConfusion('m', 'n')
        assertEquals("one sighting is not a habit", 0f, habits.bonus('m', 'n'), 0.001f)
        repeat(3) { habits.recordConfusion('m', 'n') }
        val four = habits.bonus('m', 'n')
        assertTrue(four > 0.3f)
        repeat(20) { habits.recordConfusion('m', 'n') }
        assertTrue("capped", habits.bonus('m', 'n') <= 0.75f)
        assertEquals("the other direction is a separate habit", 0f, habits.bonus('n', 'm'), 0.001f)
        habits.clear()
        assertEquals(0f, habits.bonus('m', 'n'), 0.001f)
    }

    @Test fun undoingTakesBackWhatWasLearned() {
        val habits = HabitMemory(null)
        repeat(4) { habits.recordConfusion('o', 'p') }
        val before = habits.bonus('o', 'p')
        habits.forgetConfusion('o', 'p')
        assertTrue(habits.bonus('o', 'p') < before)
        assertEquals(3, habits.confusionCount('o', 'p'))
    }

    @Test fun nonNeighborMixUpsNeedMoreSightings() {
        val habits = HabitMemory(null)
        repeat(3) { habits.recordConfusion('q', 'm') }
        assertEquals(0.12f, habits.bonus('q', 'm'), 0.001f)
        val neighbor = HabitMemory(null)
        repeat(3) { neighbor.recordConfusion('m', 'n') }
        assertTrue(neighbor.bonus('m', 'n') > habits.bonus('q', 'm'))
    }

    @Test fun periodFixPausesWhenYouKeepUndoingIt() {
        val habits = HabitMemory(null)
        repeat(10) { habits.add(HabitMemory.PERIOD_FIXED) }
        repeat(3) { habits.add(HabitMemory.PERIOD_UNDONE) }
        assertFalse("3 undone of 10 fixed is fine", habits.periodFixPaused())
        repeat(3) { habits.add(HabitMemory.PERIOD_UNDONE) }
        assertTrue("6 undone of 10 fixed pauses it", habits.periodFixPaused())
    }

    @Test fun insightsListTheBiggestHabits() {
        val habits = HabitMemory(null)
        repeat(5) { habits.recordConfusion('m', 'n') }
        repeat(3) { habits.recordConfusion('o', 'p') }
        habits.recordConfusion('a', 's')
        repeat(2) { habits.noteStrayPeriodAfter('n') }
        habits.noteStrayPeriodAfter('e')
        repeat(2) { habits.add(HabitMemory.PERIOD_FIXED) }
        val insights = habits.insights()
        assertEquals(listOf('m', 'o'), insights.mixUps.map { it.first })
        assertEquals('n', insights.periodLetters.first())
        assertEquals(2, insights.periodFixed)
    }

    @Test fun aLearnedHabitChangesWhichWordWins() {
        // Without any history "thinf" is "thing" (f sits next to g). Someone who keeps hitting f for k gets "think".
        assertEquals("thing", TestData.engine().suggest("thinf", null, true, false).correction)
        val habits = HabitMemory(null)
        repeat(30) { habits.recordConfusion('f', 'k') }
        assertEquals("think", TestData.engine(habits = habits).suggest("thinf", null, true, false).correction)
        // A habit nobody has, or one that points to the same word, changes nothing.
        val other = HabitMemory(null)
        repeat(30) { other.recordConfusion('f', 'g') }
        assertEquals("thing", TestData.engine(habits = other).suggest("thinf", null, true, false).correction)
    }

    @Test fun habitsStayOutOfTheWayOfCorrectWords() {
        val habits = HabitMemory(null)
        repeat(30) { habits.recordConfusion('n', 'm') }
        repeat(30) { habits.recordConfusion('o', 'p') }
        val engine = TestData.engine(habits = habits)
        val words = "the quick brown fox jumps over lazy dog hello world please thanks sorry okay yes maybe tonight tomorrow going coming sleep your because without through though thought soon message phone love miss home work food dinner were well think thing thank would should could".split(" ")
        val changed = words.filter { engine.suggest(it, null, true, false).correction != null }
        assertTrue("Correct words changed by a trained habit: $changed", changed.isEmpty())
    }
}
