package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricFollowRecoveryPolicyTest {
    @Test fun `only a current line outside visible rows needs recovery`() {
        assertEquals(47, LyricFollowRecoveryPolicy.offscreenTarget(setOf(47), listOf(41, 42, 43, 44, 45, 46), false))
        assertEquals(39, LyricFollowRecoveryPolicy.offscreenTarget(setOf(39), listOf(41, 42, 43), false))
        assertNull(LyricFollowRecoveryPolicy.offscreenTarget(setOf(43), listOf(41, 42, 43), false))
        assertNull(LyricFollowRecoveryPolicy.offscreenTarget(setOf(47), listOf(41, 42, 43), true))
        assertNull(LyricFollowRecoveryPolicy.offscreenTarget(emptySet(), listOf(41, 42, 43), false))
    }
}
