package com.flexkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutocorrectTest {

    private fun fix(typed: String, engine: com.flexkeys.suggest.SuggestionEngine = TestData.engine()): String? =
        engine.suggest(typed, null, true, false).correction

    @Test
    fun fixesTyposFromRealMessages() {
        val cases = mapOf(
            "tgungs" to "things", "thsi" to "this", "recieve" to "receive", "definately" to "definitely",
            "cinfurmation" to "confirmation", "ankmatioh" to "animation", "wamt" to "want", "thos" to "this",
            "ans" to "and", "keybord" to "keyboard", "settigns" to "settings", "bascially" to "basically",
            "wierd" to "weird", "frist" to "first", "becuase" to "because", "somthing" to "something",
            "wich" to "which", "alot" to "a lot", "tomorow" to "tomorrow", "tommorow" to "tomorrow",
            "helo" to "hello", "ti" to "to", "ot" to "to", "teh" to "the", "adn" to "and", "whn" to "when",
            "thier" to "their", "freind" to "friend", "beleive" to "believe", "untill" to "until", "goign" to "going",
            "pleae" to "please", "lve" to "love", "inspirtabion" to "inspiration", "boackgrojnd" to "background",
            "spellchdck" to "spellcheck", "adjustablw" to "adjustable", "everyrhkng" to "everything",
        )
        val engine = TestData.engine()
        val misses = cases.filter { (typed, want) -> fix(typed, engine)?.equals(want, ignoreCase = true) != true }
        assertTrue("Not corrected as expected: $misses", misses.isEmpty())
    }

    @Test
    fun leavesCorrectWordsAlone() {
        val words = ("the quick brown fox jumps over lazy dog hello world please thanks sorry okay yes no maybe tonight " +
            "tomorrow today going coming sleep awake time waste your don't can't won't i'm it's that's lol yup nope " +
            "okies hubby aww haha wanna gonna gotta kinda sorta whatever anyway really actually probably definitely " +
            "because without through though thought bed soon fall asleep yet did he message text call phone love miss " +
            "you too home work car food dinner lunch its lets were well").split(" ")
        val engine = TestData.engine()
        val changed = words.filter { fix(it, engine) != null }
        assertTrue("Correct words that were changed: $changed", changed.isEmpty())
    }

    @Test
    fun missingApostropheBecomesTheContraction() {
        val engine = TestData.engine()
        val cases = mapOf(
            "cant" to "can't", "dont" to "don't", "wont" to "won't", "im" to "I'm", "ive" to "I've",
            "isnt" to "isn't", "didnt" to "didn't", "couldnt" to "couldn't", "wouldnt" to "wouldn't",
            "doesnt" to "doesn't", "thats" to "that's", "whats" to "what's", "youre" to "you're", "theyre" to "they're",
        )
        for ((typed, want) in cases) assertEquals(typed, want, fix(typed, engine))
    }

    @Test
    fun splitsWordsRunTogether() {
        val engine = TestData.engine()
        assertEquals("testing it", fix("testingvit", engine))
        assertEquals("still missing", fix("stillcmissing", engine))
        assertEquals("thank you", fix("thankyou", engine))
        assertEquals("do you", fix("doyou", engine))
        assertEquals("hello there", fix("hellothere", engine))
    }

    @Test
    fun protectedWordsAreNeverChanged() {
        val engine = TestData.engine()
        // "hellp" is not a word, so it is normally corrected; protecting it stops that.
        assertTrue(fix("hellp", engine) != null)
        engine.setRules(setOf("hellp"), emptyMap())
        assertNull(fix("hellp", engine))
    }

    @Test
    fun customFixesAlwaysApply() {
        val engine = TestData.engine()
        engine.setRules(emptySet(), mapOf("gpu" to "GPU", "teh" to "The thing"))
        assertEquals("GPU", fix("gpu", engine))
        assertEquals("The thing", fix("teh", engine))
    }

    @Test
    fun repeatedTyposAreNotLearned() {
        val engine = TestData.engine()
        repeat(4) { engine.learn("thsi", null) }
        assertEquals("this", fix("thsi", engine))
        repeat(2) { engine.learn("thsi", null) }
        assertNull("six uses is enough to count as learned", fix("thsi", engine))
    }

    @Test
    fun realNewWordsAreLearnedQuickly() {
        val engine = TestData.engine()
        // A made-up name that is not close to any common word.
        repeat(2) { engine.learn("zorblax", null) }
        assertNull(fix("zorblax", engine))
    }

    @Test
    fun cleanupRemovesLearnedTypos() {
        val engine = TestData.engine()
        assertTrue(!TestData.dictionary.contains("hellp"))
        repeat(2) { engine.user.learn("hellp", null) }
        repeat(2) { engine.user.learn("zorblax", null) }
        val removed = engine.cleanLearned()
        assertEquals(1, removed)
        assertEquals(0, engine.user.count("hellp"))
        assertEquals(2, engine.user.count("zorblax"))
    }

    @Test
    fun suggestionsStayFast() {
        val engine = TestData.engine()
        val words = listOf("tgungs", "recieve", "somthing", "keybord", "communication", "extraordinary", "hello", "the")
        // Warm up, then time every prefix of every word, as while typing.
        words.forEach { w -> for (i in 1..w.length) engine.suggest(w.take(i), "i", true, false) }
        val times = ArrayList<Long>()
        repeat(5) {
            for (w in words) for (i in 1..w.length) {
                val t0 = System.nanoTime()
                engine.suggest(w.take(i), "i", true, false)
                times.add(System.nanoTime() - t0)
            }
        }
        times.sort()
        val p95 = times[(times.size * 95) / 100] / 1_000_000.0
        val mean = times.average() / 1_000_000.0
        println("suggest(): mean %.2f ms, p95 %.2f ms over %d calls".format(mean, p95, times.size))
        // A generous budget for a loaded CI machine; a phone is several times slower, so keep this tight.
        assertTrue("mean $mean ms", mean < 2.0)
        assertTrue("p95 $p95 ms", p95 < 12.0)
    }
}

class VocabularyTest {

    private val engine = TestData.engine()

    @Test
    fun namesBrandsAndSlangAreLeftAlone() {
        val words = listOf(
            "Logan", "Sasuke", "okies", "Klipy", "iPhone", "YouTube", "TikTok", "Groq", "gpu", "wifi", "tbh", "omw",
            "bestie", "Samsung", "Pixel", "Sakura", "Hinata", "Ethan", "Priya", "Mateo", "Toronto", "Tokyo", "chatgpt",
        )
        val changed = words.filter { engine.suggest(it, null, true, true).correction != null }
        assertTrue("Changed but should not be: $changed", changed.isEmpty())
    }

    @Test
    fun brandsKeepTheirCapitalization() {
        assertEquals("iPhone", engine.displayForm("iphone"))
        assertEquals("YouTube", engine.displayForm("youtube"))
        assertEquals("TikTok", engine.displayForm("tiktok"))
    }

    @Test
    fun ordinaryWordsKeepTheirLowercaseForm() {
        // Words that are also names or brands must stay lowercase when typed lowercase.
        for (w in listOf("target", "switch", "mark", "grace", "teams", "slack", "prime", "jack", "frank")) {
            assertEquals(w, engine.displayForm(w))
        }
    }

    @Test
    fun capitalizationOfIAndContractions() {
        assertEquals("I", engine.displayForm("i"))
        assertEquals("I'm", engine.displayForm("i'm"))
        assertEquals("I'll", engine.displayForm("i'll"))
        assertEquals("I've", engine.displayForm("i've"))
        assertEquals("I'd", engine.displayForm("i'd"))
    }

    @Test
    fun mixedUpRealWordsUseThePreviousWord() {
        fun fix(word: String, prev: String?) = engine.suggest(word, prev, true, false).correction
        assertEquals("than", fix("then", "better"))
        assertEquals("than", fix("then", "more"))
        assertEquals("from", fix("form", "away"))
        assertEquals("lose", fix("loose", "to"))
        // Correct uses stay alone.
        assertNull(fix("then", "and"))
        assertNull(fix("then", null))
        assertNull(fix("form", "the"))
        assertNull(fix("loose", "very"))
    }
}

class ContextTest {

    private val engine = TestData.engine()

    private fun fix(word: String, prev: String?) = engine.suggest(word, prev, true, false).correction

    @Test
    fun theWordBeforeDecidesBetweenRealWords() {
        assertEquals("from", fix("form", "away"))
        assertEquals("from", fix("form", "come"))
        assertEquals("than", fix("then", "better"))
    }

    @Test
    fun correctPairsAreLeftAlone() {
        val pairs = listOf(
            "the" to "form", "see" to "you", "i" to "love", "love" to "you", "and" to "then", "what" to "then",
            "back" to "then", "very" to "loose", "thank" to "you", "good" to "morning", "how" to "are", "are" to "you",
            "at" to "home", "right" to "now", "come" to "here", "to" to "go", "going" to "to", "want" to "to",
            "a" to "lot", "kind" to "of", "out" to "of", "this" to "is", "that" to "is", "it" to "was",
        )
        val changed = pairs.filter { (prev, word) -> fix(word, prev) != null }
        assertTrue("Correct pairs that were changed: $changed", changed.isEmpty())
    }

    @Test
    fun nextWordPredictionUsesCommonPairs() {
        val next = engine.predict("thank", false).slots
        assertTrue("expected 'you' among $next", next.contains("you"))
        val afterGood = engine.predict("good", false).slots
        assertTrue("expected predictions after 'good', got $afterGood", afterGood.size == 3)
    }

    @Test
    fun pairTableLoads() {
        assertTrue(TestData.bigrams.size > 1_000)
        assertTrue(TestData.bigrams.count("away", "from") > TestData.bigrams.count("away", "form"))
    }
}

class NaturalTextTest {

    private val engine = TestData.engine()

    @Test
    fun ordinaryMessagesAreNotChanged() {
        val messages = listOf(
            "Are you coming to bed soon",
            "Yup on my way",
            "Don't waste your awake time",
            "Did logan fall asleep yet",
            "I think we should get dinner tonight and then watch a movie",
            "Can you send me the form before the meeting",
            "She is taller than her brother but he runs faster",
            "Thanks so much for the help I really appreciate it",
            "What time does the store open tomorrow morning",
            "I will call you back when I get home from work",
            "We were going to the park but it started to rain",
            "Please let me know if you need anything else",
            "Good morning how did you sleep last night",
            "The new keyboard looks great and feels really smooth",
        )
        val changed = ArrayList<String>()
        for (m in messages) {
            var prev: String? = null
            for (w in m.split(" ")) {
                val c = engine.suggest(w, prev, true, prev == null).correction
                if (c != null) changed.add("$prev $w -> $c")
                prev = w.lowercase()
            }
        }
        assertTrue("Words changed in ordinary text: $changed", changed.isEmpty())
    }
}
