package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

/** Retained reference fixtures: native routing, literal exit positions and interrupted returns. */
class FragmentPhoneGlassPolicyTest {
    @Test fun `phone route excludes drawer tablets even in landscape`() {
        assertTrue(FragmentPhoneGlassPolicy.eligible(33, false, true))
        assertFalse(FragmentPhoneGlassPolicy.eligible(32, false, true))
        assertFalse(FragmentPhoneGlassPolicy.eligible(36, true, true))
        assertFalse(FragmentPhoneGlassPolicy.eligible(36, false, false))
    }
    @Test fun `old native holder exits rapidly instead of following beta nav positions`() {
        assertEquals(0f, FragmentPhoneGlassPolicy.navigationExit(0f, 123), 0f)
        assertEquals(77.75083f, FragmentPhoneGlassPolicy.navigationExit(.05f, 123), .001f)
        assertEquals(106.35376f, FragmentPhoneGlassPolicy.navigationExit(.1f, 123), .001f)
        assertEquals(122.88784f, FragmentPhoneGlassPolicy.navigationExit(.35f, 123), .001f)
    }
    @Test fun `interrupted return uses the same old holder positions`() {
        val values = listOf(0f, .05f, .1f, .35f, .1f, .05f, 0f).map { FragmentPhoneGlassPolicy.navigationExit(it, 123) }
        assertEquals(values[0], values[6]); assertEquals(values[1], values[5]); assertEquals(values[2], values[4])
        assertEquals(0f, FragmentPhoneGlassPolicy.navigationExit(Float.NaN, 123), 0f)
        assertEquals(0f, FragmentPhoneGlassPolicy.navigationExit(.1f, -1), 0f)
    }
}
