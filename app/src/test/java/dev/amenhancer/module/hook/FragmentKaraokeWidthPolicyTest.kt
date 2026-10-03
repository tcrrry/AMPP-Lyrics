package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentKaraokeWidthPolicyTest {
    @Test fun cachedHolderCanBindBeforeAnyNewHolderWasCreated() {
        var nativeWidth = -1 // Native A constructor; RecyclerView reuses an existing holder.
        FragmentKaraokeWidthPolicy.initialize(nativeWidth, 1500, listOf(16, 16, 24, 24))?.let { nativeWidth = it }
        assertEquals(1420, nativeWidth)
        assertTrue(nativeWidth > 0) // Native n0 can now run, without allocating a throwaway holder.
    }
    @Test fun initializedNativeWidthKeepsItsOwnValue() {
        assertNull(FragmentKaraokeWidthPolicy.initialize(1200, 1500, listOf(20, 20)))
    }
    @Test fun notMeasuredViewportNeverBecomesAnInventedOnePixelWidth() {
        assertNull(FragmentKaraokeWidthPolicy.initialize(-1, 0, listOf(20, 20)))
        assertNull(FragmentKaraokeWidthPolicy.initialize(0, -1, emptyList()))
        assertNull(FragmentKaraokeWidthPolicy.initialize(-1, 40, listOf(20, 20)))
    }
    @Test fun paddingTooWideAndOverflowDoNotPublishInvalidGeometry() {
        assertNull(FragmentKaraokeWidthPolicy.initialize(-1, 100, listOf(Int.MAX_VALUE, Int.MAX_VALUE)))
        assertNull(FragmentKaraokeWidthPolicy.initialize(-1, Int.MAX_VALUE, listOf(-1)))
    }
}
