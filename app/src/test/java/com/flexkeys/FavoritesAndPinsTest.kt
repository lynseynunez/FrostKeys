package com.flexkeys

import com.flexkeys.clipboard.ClipboardStore
import com.flexkeys.gif.GifFavorites
import com.flexkeys.gif.GifItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesAndPinsTest {

    @Test fun gifFavoritesRoundTrip() {
        val items = listOf(
            GifItem("a1", "https://x/p1.gif", "https://x/s1.gif", 200, 150, "Cat"),
            GifItem("b2", "https://x/p2.gif", "https://x/s2.gif", 100, 100, null),
        )
        val back = GifFavorites.parse(GifFavorites.format(items))
        assertEquals(2, back.size)
        assertEquals("a1", back[0].id)
        assertEquals("https://x/s1.gif", back[0].sendUrl)
        assertEquals(150, back[0].height)
        assertEquals("Cat", back[0].title)
        assertNull(back[1].title)
    }

    @Test fun brokenFavoriteDataIsIgnored() {
        assertTrue(GifFavorites.parse("").isEmpty())
        assertTrue(GifFavorites.parse("not json").isEmpty())
        // Entries missing a link are dropped.
        assertEquals(1, GifFavorites.parse("""[{"id":"a","p":"u","s":"v"},{"id":"b"}]""").size)
    }

    @Test fun pinnedClipsRoundTrip() {
        val texts = listOf("P.O. Box 20825", "someone@example.com")
        assertEquals(texts, ClipboardStore.pinnedFromJson(ClipboardStore.pinnedToJson(texts)))
    }

    @Test fun pinnedClipsAreChecked() {
        assertTrue(ClipboardStore.pinnedFromJson("nope").isEmpty())
        assertEquals(listOf("keep"), ClipboardStore.pinnedFromJson("""["keep","  ",""]"""))
        val many = (1..300).map { "item $it" }
        assertEquals(100, ClipboardStore.pinnedFromJson(ClipboardStore.pinnedToJson(many)).size)
    }
}
