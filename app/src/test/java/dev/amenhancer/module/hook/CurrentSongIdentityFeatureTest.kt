package dev.amenhancer.module.hook

import android.content.SharedPreferences
import dev.amenhancer.module.CurrentSongDetails
import dev.amenhancer.module.config.TargetConfigClient
import dev.amenhancer.module.model.FeatureState
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentSongIdentityFeatureTest {
    @Test
    fun `feature installs unconditionally and maps the capability result`() {
        val target = TargetAdaptation(
            identity = "test",
            dualPane = DualPaneTarget { TargetCapabilityInstall.Active("unused") },
            editorialVideo = EditorialVideoTarget { TargetCapabilityInstall.Active("unused") },
            bidirectionalLyricBlur = BidirectionalLyricBlurTarget {
                TargetCapabilityInstall.Active("unused")
            },
            currentSongIdentity = CurrentSongIdentityTarget {
                TargetCapabilityInstall.Active("identity published")
            },
        )

        val result = CurrentSongIdentityFeature().install(
            HookContext(config(), target),
        )

        assertEquals(FeatureState.ACTIVE, result.state)
        assertTrue(result.message.contains("identity published"))
    }

    @Test
    fun `feature maps a degraded capability as degraded without a settings gate`() {
        val target = TargetAdaptation(
            identity = "test",
            dualPane = DualPaneTarget { TargetCapabilityInstall.Active("unused") },
            editorialVideo = EditorialVideoTarget { TargetCapabilityInstall.Active("unused") },
            bidirectionalLyricBlur = BidirectionalLyricBlurTarget {
                TargetCapabilityInstall.Active("unused")
            },
            currentSongIdentity = CurrentSongIdentityTarget {
                TargetCapabilityInstall.Degraded("holder missing")
            },
        )

        val result = CurrentSongIdentityFeature().install(
            HookContext(config(), target),
        )

        assertEquals(FeatureState.DEGRADED, result.state)
        assertTrue(result.message.contains("holder missing"))
    }

    private fun config(): TargetConfigClient = TargetConfigClient(
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getAll" -> emptyMap<String, Any>()
                "toString" -> "current-song-identity-test-preferences"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as SharedPreferences,
    )
}
