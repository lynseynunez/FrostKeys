package com.flexkeys

import com.flexkeys.clipboard.OtpDetector
import com.flexkeys.fancy.FancyText
import com.flexkeys.fancy.FancyText.Style
import com.flexkeys.layout.Layer
import com.flexkeys.layout.Layouts
import com.flexkeys.layout.LayoutDef
import com.flexkeys.phrases.CustomKeys
import com.flexkeys.service.WordSelect
import com.flexkeys.settings.Versions
import com.flexkeys.suggest.SuggestionStripView.Action
import com.flexkeys.suggest.ToolbarItems
import com.flexkeys.theme.PhotoFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallLogicTest {

    private fun cp(vararg c: Int) = c.joinToString("") { String(Character.toChars(it)) }

    @Test
    fun fancyTextStyles() {
        assertEquals(cp(0x1D407, 0x1D422) + " " + cp(0x1D7D7) + "!", FancyText.apply(Style.BOLD, "Hi 9!"))
        assertEquals(cp(0x210E), FancyText.apply(Style.ITALIC, "h"))
        assertEquals(cp(0x212C, 0x212F), FancyText.apply(Style.SCRIPT, "Be"))
        assertEquals("ʜᴇʟʟᴏ", FancyText.apply(Style.SMALL_CAPS, "hello"))
        assertEquals("a̶ b̶", FancyText.apply(Style.STRIKETHROUGH, "a b"))
        assertEquals("é😀", FancyText.apply(Style.SANS, "é😀"))
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        for (s in Style.values()) {
            if (s == Style.STRIKETHROUGH || s == Style.UNDERLINE || s == Style.SMALL_CAPS) continue
            val out = FancyText.apply(s, letters)
            assertEquals("style $s", 52L, out.codePoints().distinct().count())
        }
    }

    @Test
    fun wordSelection() {
        assertEquals(5, WordSelect.charsBack("hello big world", 1))
        assertEquals(6, WordSelect.charsBack("hello big world ", 1))
        assertEquals(9, WordSelect.charsBack("hello big world", 2))
        assertEquals(10, WordSelect.charsBack("don't stop", 2))
        assertEquals(2, WordSelect.charsBack("hi", 5))
    }

    @Test
    fun customLongPressKeys() {
        val map = CustomKeys.parse("e:é€ è\nA:ä\nxx:y\nz:")
        assertEquals(listOf("é", "€", "è"), map["e"])
        assertEquals(2, map.size)
        assertEquals(map, CustomKeys.parse(CustomKeys.format(map)))
        val applied = CustomKeys.apply(Layouts.forLayer(Layer.LETTERS, "qwerty", false, true), map)
        val e = applied.rows.flatMap { it.keys }.first { it.output == "e" }
        assertEquals(listOf("é", "€", "è"), e.popup.take(3))
    }

    @Test
    fun oneTimeCodes() {
        assertEquals("482913", OtpDetector.find("482913"))
        assertEquals("482913", OtpDetector.find("Your verification code is 482913. Don't share it."))
        assertEquals("123456", OtpDetector.find("G-123456 is your Google verification code"))
        assertEquals("123456", OtpDetector.find("Use 123-456 to confirm your login"))
        assertNull(OtpDetector.find("Meet at 5pm, call 5551234 later"))
        assertNull(OtpDetector.find("My order total is 1234.50 code"))
        assertNull(OtpDetector.find("hello world"))
    }

    @Test
    fun toolbarItems() {
        assertEquals(ToolbarItems.defaults, ToolbarItems.parse(""))
        assertEquals(listOf(Action.AI, Action.EMOJI), ToolbarItems.parse("AI, EMOJI,bogus,AI"))
        assertEquals(listOf(Action.AI), ToolbarItems.toggle(listOf(Action.AI), Action.AI))
        assertEquals(listOf(Action.GIF), ToolbarItems.toggle(listOf(Action.AI, Action.GIF), Action.AI))
        val row = listOf(Action.CLIPBOARD, Action.EMOJI, Action.GIF, Action.MIC)
        assertEquals(listOf(Action.EMOJI, Action.GIF, Action.CLIPBOARD, Action.MIC), ToolbarItems.move(row, Action.CLIPBOARD, Action.GIF))
        assertEquals(listOf(Action.CLIPBOARD, Action.AI, Action.EMOJI, Action.GIF, Action.MIC), ToolbarItems.move(row, Action.AI, Action.EMOJI))
    }

    @Test
    fun photoFit() {
        val centered = PhotoFit.compute(2000, 1000, 400f, 300f, 1f, 0f, 0f)
        assertEquals(0.3f, centered.scale, 1e-4f)
        assertEquals(-100f, centered.dx, 1e-3f)
        assertEquals(0f, PhotoFit.compute(2000, 1000, 400f, 300f, 1f, -1f, 0f).dx, 1e-3f)
        assertEquals(-200f, PhotoFit.compute(2000, 1000, 400f, 300f, 1f, 1f, 0f).dx, 1e-3f)
        assertEquals(0.6f, PhotoFit.compute(2000, 1000, 400f, 300f, 2f, 0f, 0f).scale, 1e-4f)
    }

    @Test
    fun versions() {
        assertTrue(Versions.isNewer("v1.2.0", "1.1.9"))
        assertTrue(Versions.isNewer("2.0", "1.9.9"))
        assertTrue(!Versions.isNewer("v1.0.0", "1.0.0"))
        assertTrue(!Versions.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun everyLayoutBuilds() {
        val lettersIds = Layouts.letterLayouts.map { it.id }
        for (id in lettersIds) for (numberRow in listOf(false, true)) for (punct in listOf(false, true)) {
            for (layer in Layer.values()) {
                val layout: LayoutDef = Layouts.forLayer(layer, id, numberRow, punct)
                assertTrue("$id/$layer has rows", layout.rows.isNotEmpty())
                for (row in layout.rows) assertTrue("$id/$layer row fits", row.units <= LayoutDef.UNITS + 0.01f)
            }
            val letters = Layouts.forLayer(Layer.LETTERS, id, numberRow, punct).rows.flatMap { it.keys }.count { it.isLetter }
            assertTrue("$id has at least 26 letters", letters >= 26)
        }
    }
}
