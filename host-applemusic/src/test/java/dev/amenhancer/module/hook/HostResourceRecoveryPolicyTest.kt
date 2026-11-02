package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class HostResourceRecoveryPolicyTest {
    @Test fun recoversNativeWrongTypeIncludingDrawableCauseChain() {
        val wrong = UnsupportedOperationException("Can't convert to ComplexColor: type=0x5")
        assertTrue(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "color", "color", wrong))
        assertTrue(HostResourceRecoveryPolicy.accepts("com.tcrrry.ampplyrics.coexist", "color", "color", wrong))
        assertTrue(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "color", "color",
            UnsupportedOperationException("Can't convert value at index 2 to color: type=0x5")))
        assertTrue(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "drawable", "drawable",
            IllegalStateException("File layer_apple_material.xml", wrong)))
        assertTrue(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "bool", "bool",
            IllegalArgumentException("Resource ID #0x7f050016 type #0x3 is not valid")))
    }
    @Test fun neverRecoversHealthyReadsWrongIdsForeignResourcesOrMissingFiles() {
        val wrong = UnsupportedOperationException("Can't convert to ComplexColor: type=0x5")
        assertFalse(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "color", "color", null))
        assertFalse(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "dimen", "color", wrong))
        assertFalse(HostResourceRecoveryPolicy.accepts("dev.amenhancer.module", "color", "color", wrong))
        assertFalse(HostResourceRecoveryPolicy.accepts("android", "color", "color", wrong))
        assertFalse(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "drawable", "drawable", IllegalStateException("File not found")))
    }
    @Test fun cyclicCausesCannotLoopDuringFailureHandling() {
        val first=IllegalStateException("first");val second=IllegalStateException("second",first);first.initCause(second)
        assertFalse(HostResourceRecoveryPolicy.accepts("com.apple.android.music", "color", "color", first))
    }
}
