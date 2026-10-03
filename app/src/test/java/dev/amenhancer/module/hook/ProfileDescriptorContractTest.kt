package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class ProfileDescriptorContractTest {
    @Test fun `descriptor rejects wrong name return parameter staticness and owner`() {
        val exact = ProfileMethodContract(Methods::class.java.name, "renamed", listOf("long"), "void", false)
        assertTrue(exact.matches(Methods::class.java.getDeclaredMethod("renamed", Long::class.javaPrimitiveType)))
        assertFalse(exact.matches(Methods::class.java.getDeclaredMethod("renamed", Int::class.javaPrimitiveType)))
        assertFalse(exact.matches(Methods::class.java.getDeclaredMethod("other", Long::class.javaPrimitiveType)))
        assertFalse(exact.copy(returns="boolean").matches(Methods::class.java.getDeclaredMethod("renamed", Long::class.javaPrimitiveType)))
        assertFalse(exact.copy(isStatic=true).matches(Methods::class.java.getDeclaredMethod("renamed", Long::class.javaPrimitiveType)))
        assertFalse(exact.matches(Foreign::class.java.getDeclaredMethod("renamed", Long::class.javaPrimitiveType)))
    }

    @Test fun `field descriptor rejects foreign holder and same name with wrong type`() {
        val exact = ProfileFieldContract(Fields::class.java.name, "current", "java.lang.String")
        assertTrue(exact.matches(Fields::class.java.getDeclaredField("current")))
        assertFalse(exact.copy(type="int").matches(Fields::class.java.getDeclaredField("current")))
        assertFalse(exact.matches(ForeignFields::class.java.getDeclaredField("current")))
    }

    @Test fun `1606 data pins renamed seams without changing legacy predicates`() {
        val profiles = dev.amenhancer.host.applemusic.AppleMusicHostProfiles
        val current = profiles.find("com.apple.android.music", "7.0.0-beta", 1606)!!.document
        val contracts = current.getJSONObject("indexed").getJSONObject("methodContracts")
        assertEquals("w2", contracts.getJSONObject("lyrics-install-method").getString("name"))
        assertEquals("b2", contracts.getJSONObject("lyrics-item-update-method").getString("name"))
        assertEquals("z3.w", contracts.getJSONObject("lyrics-item-update-method").getJSONArray("parameters").getString(0))
        assertEquals("V", contracts.getJSONObject("cjk-karaoke-animation-method").getString("name"))
        assertEquals("c", current.getJSONObject("indexed").getJSONObject("fieldContracts")
            .getJSONObject("lyrics-current-item-field").getString("name"))
        assertFalse(profiles.find("com.apple.android.music", "6.5.3", 1599)!!.document
            .getJSONObject("indexed").has("methodContracts"))
    }

    class Methods {
        fun renamed(value: Long) = Unit
        fun renamed(value: Int) = Unit
        fun other(value: Long) = Unit
    }
    class Foreign { fun renamed(value: Long) = Unit }
    class Fields { @JvmField var current: String? = null }
    class ForeignFields { @JvmField var current: String? = null }
}
