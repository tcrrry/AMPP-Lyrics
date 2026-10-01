package dev.amenhancer.module.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatLandscapeWindowPolicyTest {
    @Test
    fun `disabled compensation never reserves navigation space`() {
        assertFalse(
            FlatLandscapeWindowPolicy.shouldApplyCompensation(
                isTabletLandscape = true,
                compensationEnabled = false,
            ),
        )
    }

    @Test
    fun `enabled compensation ignores display ratio`() {
        assertTrue(
            FlatLandscapeWindowPolicy.shouldApplyCompensation(
                isTabletLandscape = true,
                compensationEnabled = true,
            ),
        )
    }

    @Test
    fun `compensation never bypasses the tablet landscape gate`() {
        assertFalse(
            FlatLandscapeWindowPolicy.shouldApplyCompensation(
                isTabletLandscape = false,
                compensationEnabled = true,
            ),
        )
    }
}
