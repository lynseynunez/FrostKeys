package com.flexkeys

import com.flexkeys.settings.BackupManager
import com.flexkeys.theme.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private val settings: Map<String, Any> = mapOf(
        Prefs.KEY_LAYOUT to "colemak",
        Prefs.KEY_NUMBER_ROW to true,
        Prefs.KEY_HEIGHT to 112,
        Prefs.KEY_ACCENT to -9_000_000, // colors are negative ints
        Prefs.KEY_BG_VERSION to 1_760_000_000_000L,
        Prefs.KEY_PROTECTED_WORDS to "gpu\nokies",
        Prefs.KEY_CUSTOM_FIXES to "gpu\tGPU",
        Prefs.KEY_TOOLBAR_ITEMS to "AI,EMOJI,GIF",
        Prefs.KEY_CUSTOM_KEYS to "e:é€",
    )

    @Test
    fun settingsRoundTripWithTheirTypes() {
        val restored = BackupManager.parsePrefs(BackupManager.prefsToJson(settings))
        assertEquals(settings, restored)
        assertTrue(restored[Prefs.KEY_HEIGHT] is Int)
        assertTrue(restored[Prefs.KEY_BG_VERSION] is Long)
    }

    @Test
    fun apiKeysAreNeverBackedUp() {
        val secrets = mapOf(
            Prefs.KEY_KLIPY_KEY to "k1", Prefs.KEY_KLIPY_CUSTOMER to "c1", Prefs.KEY_TRANSLATE_EMAIL to "me@example.com",
            Prefs.KEY_GEMINI_KEY to "g1", Prefs.KEY_GROQ_KEY to "q1",
        )
        val json = BackupManager.prefsToJson(settings + secrets)
        for (key in secrets.keys) assertFalse("$key leaked", json.has(key))
        // Even a file that somehow contains them doesn't restore them.
        val tampered = org.json.JSONObject().put(Prefs.KEY_GROQ_KEY, org.json.JSONObject().put("t", "s").put("v", "stolen"))
        assertTrue(BackupManager.parsePrefs(tampered).isEmpty())
    }

    @Test
    fun stickerNamesMustBePlain() {
        assertTrue(BackupManager.isSafeStickerName("s1760000000000_42.png"))
        assertFalse(BackupManager.isSafeStickerName("../evil.png"))
        assertFalse(BackupManager.isSafeStickerName("a/b.png"))
        assertFalse(BackupManager.isSafeStickerName("script.sh"))
        assertFalse(BackupManager.isSafeStickerName(".png"))
    }
}
