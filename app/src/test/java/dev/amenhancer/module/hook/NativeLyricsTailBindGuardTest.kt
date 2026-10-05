package dev.amenhancer.module.hook

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class NativeLyricsTailBindGuardTest {
    @After fun cleanUp() { NativeLyricsTailBindGuard.retain = null }

    @Test fun fullBindingSeesTheRealHolderBeforeAQuickFinalWordStartsAndExpiresWithTheClock() {
        val adapter = Any()
        val holder = Any()
        val document = Any()
        val tails = LyricWordTailState()
        tails.process(document, 150219)
        tails.update("word", mapOf(10 to 151043L))
        var calls = 0
        NativeLyricsTailBindGuard.retain = { target, item, position ->
            assertSame(adapter, target)
            assertSame(holder, item)
            assertEquals(10, position)
            calls++
            tails.protects(document, position)
        }
        tails.process(document, 150570)
        assertTrue(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 10))
        tails.process(document, 150660)
        assertTrue(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 10))
        tails.process(document, 151043)
        assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 10))
        assertEquals(3, calls)
    }

    @Test fun manualSubtitleChangesBypassBothHookLayersAndRestoreProtectionAfterAnException() {
        var calls = 0
        NativeLyricsTailBindGuard.retain = { _, _, _ -> calls++; true }
        val adapter = Any()
        val holder = Any()
        assertTrue(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 3))
        try {
            NativeLyricsTailBindGuard.updatingAuxiliary {
                assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 3))
                NativeLyricsTailBindGuard.updatingAuxiliary {
                    assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 3))
                }
                assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 3))
                throw IllegalStateException("Native subtitle refresh failed")
            }
            fail("Expected failed refresh")
        } catch (_: IllegalStateException) { }
        assertTrue(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 3))
        assertEquals(2, calls)
    }

    @Test fun emptyNativePayloadKeepsBothFullBindingLayersProtected() {
        var calls = 0
        NativeLyricsTailBindGuard.retain = { _, _, _ -> calls++; true }
        NativeLyricsTailBindGuard.updatingAuxiliary(explicitChange = false) {
            assertTrue(NativeLyricsTailBindGuard.beforeBind(Any(), Any(), 43))
            assertTrue(NativeLyricsTailBindGuard.beforeBind(Any(), Any(), 43))
        }
        assertEquals(2, calls)
    }

    @Test fun missingOrInvalidNativeTimingCannotBlockOrdinaryFullBinding() {
        val adapter = Any()
        val holder = Any()
        assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 1))
        NativeLyricsTailBindGuard.retain = { _, _, _ -> throw IllegalStateException("Disposed native holder") }
        assertFalse(NativeLyricsTailBindGuard.beforeBind(adapter, holder, 1))
    }
}
