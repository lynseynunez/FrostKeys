package com.flexkeys

import com.flexkeys.suggest.FixMemory
import com.flexkeys.suggest.KeyGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchAndFixesTest {

    @Test fun leanPointsTowardTheNeighbor() {
        assertTrue(KeyGeometry.lean('o', 'p', 0.4f, 0f)!! > 0.3f)
        assertTrue(KeyGeometry.lean('o', 'p', -0.4f, 0f)!! < -0.3f)
        assertTrue(KeyGeometry.lean('o', 'i', -0.4f, 0f)!! > 0.3f)
        // A tap in the middle of the key leans nowhere, and an unknown tap is not a lean.
        assertEquals(0f, KeyGeometry.lean('o', 'p', 0f, 0f)!!, 0.001f)
        assertNull(KeyGeometry.lean('o', 'p', Float.NaN, 0f))
        assertNull(KeyGeometry.lean('1', 'p', 0.4f, 0f))
    }

    /** "thinf": the finger sat toward the g, a neighbor of f, so "thing" is likelier than "think". */
    @Test fun tapPositionBreaksATieBetweenNeighbors() {
        val engine = TestData.engine()
        fun hint(fx: Float) = FloatArray(10) { Float.NaN }.also { it[8] = fx; it[9] = 0f }
        val towardG = engine.suggest("thinf", null, true, false, hint(0.45f)).correction
        val awayFromG = engine.suggest("thinf", null, true, false, hint(-0.45f)).correction
        assertEquals("thing", towardG)
        assertNotEquals("thing", awayFromG)
    }

    @Test fun noTapPositionChangesNothing() {
        val engine = TestData.engine()
        assertEquals(engine.suggest("thinf", null, true, false).correction, engine.suggest("thinf", null, true, false, null).correction)
        // A hint of the wrong size is ignored.
        assertEquals(engine.suggest("thinf", null, true, false).correction, engine.suggest("thinf", null, true, false, FloatArray(2)).correction)
    }

    @Test fun fixMemoryCountsAndForgets() {
        val fixes = FixMemory(null)
        fixes.record("fro", "for")
        assertEquals("for" to 1, fixes.preferred("FRO"))
        fixes.record("fro", "for")
        assertEquals(2, fixes.count("fro", "for"))
        fixes.record("fro", "from")
        assertEquals("for", fixes.preferred("fro")!!.first)
        fixes.reject("fro", "for")
        assertEquals("from" to 1, fixes.preferred("fro"))
        fixes.record("a", "b")
        assertNull("one-letter words are not remembered", fixes.preferred("a"))
        fixes.clear()
        assertEquals(0, fixes.size)
    }

    @Test fun aFixYouMakeTwiceIsAppliedOnItsOwn() {
        val fixes = FixMemory(null)
        val engine = TestData.engine(fixes)
        val before = engine.suggest("fro", null, true, false)
        fixes.record("fro", "for")
        val once = engine.suggest("fro", null, true, false)
        assertTrue("offered after one time", once.slots.any { it.equals("for", true) })
        assertEquals("not applied on its own yet", before.correction, once.correction)
        fixes.record("fro", "for")
        assertEquals("for", engine.suggest("fro", null, true, false).correction)
        // Undoing it forgets it.
        fixes.reject("fro", "for")
        assertEquals(before.correction, engine.suggest("fro", null, true, false).correction)
    }

    @Test fun rememberedFixesDoNotOverrideWordsYouTaughtTheKeyboard() {
        val fixes = FixMemory(null)
        val engine = TestData.engine(fixes)
        repeat(3) { fixes.record("brio", "bro") }
        engine.user.learn("brio", null, weight = 8)
        assertNotNull(engine.suggest("brio", null, true, false))
        assertNull(engine.suggest("brio", null, true, false).correction)
    }

    /** A tap near the edge of a key must never turn a correctly spelled word into another one. */
    @Test fun edgeTapsDoNotChangeCorrectWords() {
        val words = ("the quick brown fox jumps over lazy dog hello world please thanks sorry okay yes maybe tonight " +
            "tomorrow today going coming sleep awake time your because without through though thought soon asleep " +
            "message phone love miss home work food dinner lunch were well think thing thank would should could").split(" ")
        val engine = TestData.engine()
        val changed = ArrayList<String>()
        for (w in words) {
            for (i in w.indices) for ((fx, fy) in listOf(0.45f to 0f, -0.45f to 0f, 0f to 0.45f, 0f to -0.45f)) {
                val hint = FloatArray(2 * w.length) { Float.NaN }
                hint[2 * i] = fx
                hint[2 * i + 1] = fy
                if (engine.suggest(w, null, true, false, hint).correction != null) changed.add("$w@$i($fx,$fy)")
            }
        }
        assertTrue("Correct words changed by an edge tap: ${changed.take(12)}", changed.isEmpty())
    }
}
