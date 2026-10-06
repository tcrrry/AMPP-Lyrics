package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class NativeLyricsTimedWordsTest {
    @Test fun nestedStylingInheritsActualParentTimeAndCombinesSameEnvelope() {
        val parsed = requireNotNull(NativeLyricsTimedWords.parse("<span begin='1s' end='2s'><span>君</span><span>よ</span></span>"))
        assertEquals("君よ", parsed.text)
        assertEquals(listOf(Triple(1000L, 2000L, "君よ")), parsed.words)
    }
    @Test fun xmlEntitiesCommentsAndCdataDoNotEraseTextOrLoseTimes() {
        val parsed = requireNotNull(NativeLyricsTimedWords.parse("<!--style--><span begin='0:01.000' end='2000ms'>&#x541B;&amp;<![CDATA[よ]]></span>"))
        assertEquals("君&よ", parsed.text)
        assertEquals(listOf(Triple(1000L, 2000L, "君&よ")), parsed.words)
    }
    @Test fun untimedTextMissingEndsAndConflictingNestedRangesAreRejected() {
        for (body in listOf("君<span begin='1s' end='2s'>よ</span>",
            "<span begin='1s'>君</span>", "<span begin='2s' end='1s'>君</span>",
            "<span begin='1s' end='2s'><span begin='0s' end='3s'>君</span></span>"))
            assertNull(requireNotNull(NativeLyricsTimedWords.parse(body)).words)
        assertNull(NativeLyricsTimedWords.parse("<!DOCTYPE x SYSTEM 'file:///missing'><span>君</span>"))
    }
}
