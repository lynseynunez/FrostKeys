package com.flexkeys

import com.flexkeys.settings.AutoBackup
import com.flexkeys.theme.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    @Test fun keepsNewestFour() {
        val names = (1..6).map { "SakuriKey-auto-2026-01-0$it-0900.json" } + "notes.txt"
        assertEquals(listOf("SakuriKey-auto-2026-01-02-0900.json", "SakuriKey-auto-2026-01-01-0900.json"), AutoBackup.toDelete(names))
    }

    @Test fun nothingToDeleteWithFour() {
        assertTrue(AutoBackup.toDelete((1..4).map { "SakuriKey-auto-2026-01-0$it-0900.json" }).isEmpty())
    }

    @Test fun fileNamesSortByDate() {
        assertTrue(AutoBackup.fileName(1_700_000_000_000L) < AutoBackup.fileName(1_800_000_000_000L))
    }

    @Test fun sakuriLooksAreAdded() {
        val names = Looks.builtIn.map { it.name }
        listOf("Sakuri Night", "Midnight Ice", "Rose Noir", "Sakuri Frost").forEach { assertTrue(it in names) }
        assertEquals(names.size, names.toSet().size)
    }
}
