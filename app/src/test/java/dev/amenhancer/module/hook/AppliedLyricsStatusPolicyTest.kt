package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class AppliedLyricsStatusPolicyTest {
    @Test fun metadataArrivingAfterInstallationCanReconcileTheSamePointer() {
        val ready = Any()
        assertTrue(shouldReportAppliedLyrics(100L, 100L, ready, ready))
    }
    @Test fun aPreparedCandidateCannotMisreportTheStillNativePage() {
        assertFalse(shouldReportAppliedLyrics(100L, 100L, Any(), Any()))
    }
    @Test fun aPageFromThePreviousSongCannotReplaceTheCurrentStatus() {
        val ready = Any()
        assertFalse(shouldReportAppliedLyrics(101L, 100L, ready, ready))
    }
    @Test fun absentAndInvalidPointersNeverCountAsApplied() {
        assertFalse(shouldReportAppliedLyrics(100L, 100L, null, null))
        assertFalse(shouldReportAppliedLyrics(0L, 0L, Any(), Any()))
        assertFalse(shouldReportAppliedLyrics(100L, null, Any(), Any()))
    }
}
