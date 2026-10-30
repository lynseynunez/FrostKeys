package com.flexkeys

import com.flexkeys.layout.AccentSets
import com.flexkeys.layout.KeyDef
import com.flexkeys.layout.Layer
import com.flexkeys.layout.Layouts
import com.flexkeys.layout.PopupSettings
import com.flexkeys.theme.GlassTheme
import com.flexkeys.theme.Look
import com.flexkeys.theme.Looks
import com.flexkeys.theme.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupAndLooksTest {

    private fun letters() = Layouts.forLayer(Layer.LETTERS, "qwerty", numberRow = false)

    private fun key(layout: com.flexkeys.layout.LayoutDef, c: Char): KeyDef =
        layout.rows.flatMap { it.keys }.first { it.label == c.toString() }

    @Test fun defaultSettingsLeaveTheLayoutAlone() {
        val base = letters()
        assertEquals(base, PopupSettings.DEFAULT.apply(base))
    }

    @Test fun accentsCanBeTurnedOff() {
        val e = key(PopupSettings(accents = false).apply(letters()), 'e')
        assertFalse(e.popup.any { it.isNotEmpty() && it[0].isLetter() })
    }

    @Test fun symbolsCanBeTurnedOffAndTheCornerHintGoes() {
        val settings = PopupSettings(symbols = false)
        val q = key(settings.apply(letters()), 'q')
        assertNull(q.hint)
        assertTrue(q.popup.none { it.isNotEmpty() && !it[0].isLetter() })
    }

    @Test fun cornerHintsCanBeHiddenAlone() {
        val q = key(PopupSettings(cornerHints = false).apply(letters()), 'q')
        assertNull(q.hint)
        assertTrue(q.popup.isNotEmpty())
    }

    @Test fun accentsCanComeFirst() {
        val a = key(PopupSettings(accentsFirst = true).apply(letters()), 'a')
        assertEquals("à", a.popup.first())
        val plain = key(letters(), 'a')
        assertEquals("@", plain.popup.first())
    }

    @Test fun otherLanguageGroupsAddTheirLetters() {
        val l = key(PopupSettings(accentSets = setOf(AccentSets.CENTRAL)).apply(letters()), 'l')
        assertTrue(l.popup.contains("ł"))
        val i = key(PopupSettings(accentSets = setOf(AccentSets.WESTERN, AccentSets.TURKISH)).apply(letters()), 'i')
        assertTrue(i.popup.contains("ı"))
        // Turning Western off removes é but keeps what the chosen group offers.
        val e = key(PopupSettings(accentSets = setOf(AccentSets.NORDIC)).apply(letters()), 'e')
        assertFalse(e.popup.contains("ê"))
    }

    @Test fun nonLetterKeysAreNotTouched() {
        val withPunctuation = Layouts.forLayer(Layer.LETTERS, "qwerty", numberRow = false, punctuation = true)
        val before = key(withPunctuation, ',')
        val after = key(PopupSettings(accents = false, symbols = false).apply(withPunctuation), ',')
        assertEquals(before.popup, after.popup)
    }

    @Test fun landscapeHeightFactors() {
        assertEquals(0.6f, GlassTheme.landscapeFactor(0, tablet = false), 0.001f)
        assertEquals(0.85f, GlassTheme.landscapeFactor(0, tablet = true), 0.001f)
        assertEquals(0.5f, GlassTheme.landscapeFactor(1, tablet = true), 0.001f)
        assertEquals(1f, GlassTheme.landscapeFactor(3, tablet = false), 0.001f)
    }

    @Test fun savedLooksRoundTrip() {
        val look = Look("Mine", mapOf(Prefs.KEY_ACCENT to 0xFF123456.toInt(), Prefs.KEY_BLUR to 42))
        val back = Looks.parse(Looks.format(listOf(look, Look("Two", mapOf(Prefs.KEY_BLUR to 7))))) 
        assertEquals(2, back.size)
        assertEquals("Mine", back[0].name)
        assertEquals(0xFF123456.toInt(), back[0].values[Prefs.KEY_ACCENT])
        assertEquals(42, back[0].values[Prefs.KEY_BLUR])
    }

    @Test fun brokenLookDataIsIgnored() {
        assertTrue(Looks.parse("not json").isEmpty())
        assertTrue(Looks.parse("").isEmpty())
    }

    @Test fun builtInLooksAreComplete() {
        assertTrue(Looks.builtIn.size >= 12)
        assertEquals(Looks.builtIn.size, Looks.builtIn.map { it.name }.toSet().size)
        for (l in Looks.builtIn) {
            assertTrue(l.name, l.values.containsKey(Prefs.KEY_ACCENT) && l.values.containsKey(Prefs.KEY_GLASS_OPACITY))
            assertTrue(l.name, l.values.keys.all { it in Looks.keys })
        }
    }
}
