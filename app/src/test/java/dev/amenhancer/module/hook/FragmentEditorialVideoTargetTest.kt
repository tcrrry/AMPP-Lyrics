package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentEditorialVideoTargetTest {
    @Test fun fragmentHostPreservesNativeArtworkWithoutConstructingTheSuppressor() {
        val target = editorialVideoTargetForFamily("fragment-content") { error("legacy suppressor must stay untouched") }
        assertSame(FragmentEditorialVideoTarget, target)
        assertTrue(target.install() is TargetCapabilityInstall.Active)
    }
    @Test fun legacyHostStillUsesItsExistingSuppressionCapability() {
        var installs = 0
        val original = EditorialVideoTarget { installs++; TargetCapabilityInstall.Active("legacy suppression") }
        val target = editorialVideoTargetForFamily("legacy-activity") { original }
        assertSame(original, target)
        target.install()
        assertEquals(1, installs)
    }
    @Test fun anUnknownHostCannotFallBackToEitherArtworkBehavior() {
        assertTrue(runCatching {
            editorialVideoTargetForFamily("unverified-family") { error("unexpected legacy fallback") }
        }.isFailure)
    }
}
