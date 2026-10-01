package dev.amenhancer.module.hook

import dev.amenhancer.module.lyrics.DesktopLyricsPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationSubtitlePolicyTest {
    @Test fun `nested binding inherits true choices instead of temporary subtitle flags`() {
        val adapter = Any()
        val outer = NativeLyricsPronunciationSubtitle.Selection(adapter, 1, true, false)
        val inner = NativeLyricsPronunciationSubtitle.Selection.resolve(adapter, 2, false, true, outer)
        assertTrue(inner.pronunciation)
        assertFalse(inner.translation)
        assertEquals(2, inner.lineId)
    }
    @Test fun `nested different adapter does not inherit another songs choices`() {
        val outer = NativeLyricsPronunciationSubtitle.Selection(Any(), 1, true, false)
        val inner = NativeLyricsPronunciationSubtitle.Selection.resolve(Any(), 1, false, true, outer)
        assertFalse(inner.pronunciation)
        assertTrue(inner.translation)
    }
    @Test fun `Apple and unmarked pronunciation pointers keep their native layout`() {
        val pointer = Any()
        NativeLyricsPronunciationSubtitle.remember(pointer, "<tt itunes:timing=\"Word\"><transliterations>kimi</transliterations></tt>")
        assertFalse(NativeLyricsPronunciationSubtitle.isManaged(pointer))
        assertFalse(NativeLyricsPronunciationSubtitle.isManaged(null))
    }
    @Test fun `only exact marked Word pronunciation pointer is registered`() {
        val marker = DesktopLyricsPresentation("QQ音乐", true, true, false, false, true).marker()
        val pointer = Any()
        NativeLyricsPronunciationSubtitle.remember(pointer, "$marker<tt itunes:timing=\"Word\"></tt>")
        assertTrue(NativeLyricsPronunciationSubtitle.isManaged(pointer))
        assertFalse(NativeLyricsPronunciationSubtitle.isManaged(Any()))
        NativeLyricsPronunciationSubtitle.remember(pointer, "$marker<tt itunes:timing=\"Line\"></tt>")
        assertFalse(NativeLyricsPronunciationSubtitle.isManaged(pointer))
    }
    @Test fun `source with no pronunciation is not registered`() {
        val marker = DesktopLyricsPresentation("网易云音乐", true, true, false, false, false).marker()
        val pointer = Any()
        NativeLyricsPronunciationSubtitle.remember(pointer, "$marker<tt itunes:timing=\"Word\"></tt>")
        assertFalse(NativeLyricsPronunciationSubtitle.isManaged(pointer))
    }
}
