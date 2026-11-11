package com.flexkeys

import com.flexkeys.suggest.AutocorrectLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutocorrectLogTest {

    private fun fill(log: AutocorrectLog, total: Int, undone: Int) {
        for (i in 0 until total) {
            log.recordFix(AutocorrectLog.Kind.WORD, "teh$i", "the$i")
            if (i < undone) log.markUndone("teh$i", "the$i")
        }
    }

    @Test fun recordsAndMarksUndone() {
        val log = AutocorrectLog(null)
        log.recordFix(AutocorrectLog.Kind.WORD, "teh", "the")
        log.recordFix(AutocorrectLog.Kind.WORD, "brio", "bro")
        log.markUndone("brio", "bro")
        assertEquals(2, log.fixedCount)
        assertEquals(1, log.undoneCount)
        assertEquals("newest first", "brio", log.recent(5).first().typed)
        assertTrue(log.recent(5).first().undone)
        assertFalse(log.recent(5).last().undone)
    }

    @Test fun keepsOnlyTheLatest() {
        val log = AutocorrectLog(null)
        for (i in 0 until 500) log.recordFix(AutocorrectLog.Kind.WORD, "w$i", "x$i")
        assertEquals(200, log.fixedCount)
        assertEquals("w499", log.recent(1).first().typed)
    }

    @Test fun undoRateNeedsEnoughHistory() {
        val log = AutocorrectLog(null)
        fill(log, 8, 8)
        assertNull("too few to judge", log.undoRate())
        assertEquals(0.0, log.carefulness(), 0.0001)
        fill(log, 12, 0)
        assertNotNull(log.undoRate())
    }

    @Test fun carefulnessGrowsWithUndosAndIsCapped() {
        val calm = AutocorrectLog(null)
        fill(calm, 40, 2)
        assertEquals("5% undone is normal", 0.0, calm.carefulness(), 0.0001)
        val unhappy = AutocorrectLog(null)
        fill(unhappy, 40, 12)
        assertTrue("30% undone makes it more careful", unhappy.carefulness() > 0.5)
        val awful = AutocorrectLog(null)
        fill(awful, 40, 40)
        assertEquals(1.5, awful.carefulness(), 0.0001)
    }

    @Test fun beingCarefulNeverAddsCorrections() {
        val awful = AutocorrectLog(null)
        fill(awful, 40, 40)
        val normal = TestData.engine()
        val careful = TestData.engine(log = awful)
        val typos = listOf("tgungs", "thsi", "recieve", "definately", "cinfurmation", "keybord", "settigns", "wierd", "frist", "becuase", "somthing", "tomorow", "goign", "thier", "freind", "somthng", "probly", "defintly", "evrything", "diffrent")
        val changedNormal = typos.filter { normal.suggest(it, null, true, false).correction != null }
        val changedCareful = typos.filter { careful.suggest(it, null, true, false).correction != null }
        assertTrue("careful changes are a subset: $changedCareful vs $changedNormal", changedNormal.containsAll(changedCareful))
        assertTrue("it still fixes the clear ones", changedCareful.size >= typos.size / 2)
    }

    @Test fun unknownWordsAreCountedAndForgotten() {
        val log = AutocorrectLog(null)
        repeat(3) { log.noteUnknown("Brio") }
        log.noteUnknown("zork")
        log.noteUnknown("ab")
        assertEquals(listOf("brio" to 3, "zork" to 1), log.unknownWords(5))
        log.forgetUnknown("BRIO")
        assertEquals(listOf("zork" to 1), log.unknownWords(5))
    }

    @Test fun wrongOnAWordMarksItsChanges() {
        val log = AutocorrectLog(null)
        log.recordFix(AutocorrectLog.Kind.WORD, "brio", "bro")
        log.recordFix(AutocorrectLog.Kind.WORD, "brio", "bro")
        log.markWrong("Brio")
        assertEquals(2, log.undoneCount)
    }

    @Test fun survivesRestart() {
        val file = File.createTempFile("ac_log", ".tsv").apply { delete(); deleteOnExit() }
        val log = AutocorrectLog(file)
        log.recordFix(AutocorrectLog.Kind.SPLIT, "thankyou", "thank you")
        log.markUndone("thankyou", "thank you")
        log.noteUnknown("zork")
        Thread.sleep(300)
        val again = AutocorrectLog(file)
        again.install(again.loadBlocking())
        assertEquals(1, again.fixedCount)
        assertTrue(again.recent(1).first().undone)
        assertEquals(AutocorrectLog.Kind.SPLIT, again.recent(1).first().kind)
        assertEquals(listOf("zork" to 1), again.unknownWords(3))
    }

    @Test fun reportListsWhatHappened() {
        val log = AutocorrectLog(null)
        log.recordFix(AutocorrectLog.Kind.WORD, "teh", "the")
        log.recordFix(AutocorrectLog.Kind.WORD, "brio", "bro")
        log.markUndone("brio", "bro")
        log.noteUnknown("zork")
        val text = log.report()
        assertTrue(text.contains("brio -> bro   [undone]"))
        assertTrue(text.contains("teh -> the"))
        assertTrue(text.contains("zork"))
    }
}
