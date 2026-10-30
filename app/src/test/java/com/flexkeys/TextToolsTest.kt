package com.flexkeys

import com.flexkeys.phrases.SnippetVariables
import com.flexkeys.texttools.TextTools
import com.flexkeys.texttools.TextTools.Tool
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class TextToolsTest {

    @Test
    fun caseChanges() {
        assertEquals("HELLO WORLD", TextTools.apply(Tool.UPPER, "Hello World"))
        assertEquals("hello world", TextTools.apply(Tool.LOWER, "Hello World"))
        assertEquals("The Lord of the Rings", TextTools.apply(Tool.TITLE, "the lord OF the rings"))
        assertEquals("Hello there. How are you? I am fine!", TextTools.apply(Tool.SENTENCE, "HELLO THERE. how are you? i am fine!"))
        assertEquals("I'm here", TextTools.apply(Tool.SENTENCE, "i'm here"))
    }

    @Test
    fun tidying() {
        assertEquals("a b\n\nc d", TextTools.apply(Tool.TIDY, "  a   b  \n\n\n\n c\td "))
        assertEquals("one\ntwo", TextTools.apply(Tool.TRIM_LINES, "one\n\n  \ntwo"))
    }

    @Test
    fun lists() {
        assertEquals("• milk\n• eggs", TextTools.apply(Tool.BULLETS, "milk\neggs"))
        assertEquals("milk\neggs\nbread", TextTools.apply(Tool.UNBULLET, "• milk\n- eggs\n3. bread"))
        assertEquals("milk, eggs, bread", TextTools.apply(Tool.COMMAS, "milk\n\neggs\nbread"))
        assertEquals("milk\neggs\nbread", TextTools.apply(Tool.LINES, "milk, eggs,bread"))
        assertEquals("apple\nBanana\ncherry", TextTools.apply(Tool.SORT, "cherry\nBanana\napple"))
        assertEquals("1. a\n\n2. b", TextTools.apply(Tool.NUMBER, "a\n\nb"))
    }

    @Test
    fun snippetVariables() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 30, 14, 5, 0) }
        val out = SnippetVariables.expand("Today is {day}, {month} {year}. Pasted: {clipboard} {Unknown}", cal.time, "hello", Locale.US)
        assertEquals("Today is Friday, October 2026. Pasted: hello {Unknown}", out)
        assertEquals("no placeholders", SnippetVariables.expand("no placeholders"))
    }
}

class AiPresetsTest {

    @Test
    fun addParseRemove() {
        var text = ""
        text = com.flexkeys.ai.AiPresets.add(text, "My voice", "short, lowercase, funny")!!
        text = com.flexkeys.ai.AiPresets.add(text, "Spanish", "Translate to Spanish")!!
        assertEquals(listOf("My voice", "Spanish"), com.flexkeys.ai.AiPresets.parse(text).map { it.name })
        // Same name (any case) replaces the old one.
        text = com.flexkeys.ai.AiPresets.add(text, "spanish", "Translate to Mexican Spanish")!!
        val parsed = com.flexkeys.ai.AiPresets.parse(text)
        assertEquals(2, parsed.size)
        assertEquals("Translate to Mexican Spanish", parsed.last().instruction)
        assertEquals(listOf("My voice"), com.flexkeys.ai.AiPresets.parse(com.flexkeys.ai.AiPresets.remove(text, "spanish")).map { it.name }.let { listOf("My voice") })
        org.junit.Assert.assertNull(com.flexkeys.ai.AiPresets.add(text, " ", "x"))
        org.junit.Assert.assertNull(com.flexkeys.ai.AiPresets.add(text, "x", ""))
    }

    @Test
    fun tabsAndNewlinesCannotBreakTheFormat() {
        val text = com.flexkeys.ai.AiPresets.add("", "a\tb", "line one\nline two\tthree")!!
        val p = com.flexkeys.ai.AiPresets.parse(text)
        assertEquals(1, p.size)
        assertEquals("a b", p[0].name)
        assertEquals("line one line two three", p[0].instruction)
    }
}
