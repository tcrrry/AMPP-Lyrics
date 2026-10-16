package com.tcrrry.lyrics.plugin

import org.junit.Assert.*
import org.junit.Test

class OfflineModelPolicyTest {
    private val codes = listOf("en", "zh", "ja", "ko")
    @Test fun downloadAlwaysIncludesChineseWithoutInventingAnEnglishModel() {
        assertEquals(setOf("ja", "zh"), OfflineModelPolicy.downloads("ja", codes))
        assertEquals(setOf("zh"), OfflineModelPolicy.downloads("en", codes))
        assertEquals(setOf("zh"), OfflineModelPolicy.downloads("zh", codes))
    }
    @Test fun deleteOnlyTargetsARealSelectedModel() {
        assertTrue(OfflineModelPolicy.canDelete("ja", codes))
        assertFalse(OfflineModelPolicy.canDelete("en", codes))
        assertFalse(OfflineModelPolicy.canDelete("auto", codes))
    }
    @Test(expected = IllegalArgumentException::class) fun unknownLanguageNeverStartsDownloads() {
        OfflineModelPolicy.downloads("auto", codes)
    }
}
