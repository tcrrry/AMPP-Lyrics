package dev.amenhancer.module.hook

import android.content.Context
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.config.ConfigurationReader
import dev.amenhancer.module.config.TargetConfigClient
import dev.amenhancer.module.model.FeatureState
import java.io.InputStream
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularDataEntryFeatureTest {
    @Test
    fun `feature installs while disabled independently of other capabilities and reads live config`() {
        val values = mutableMapOf<String, Any>("dual_pane_enabled" to false, "title_correction_enabled" to false)
        val config = TargetConfigClient(object : ConfigurationReader {
            override fun values(): Map<String, *> = values.toMap()
            override fun openFile(name: String): InputStream? = null
        })
        val fixture = FeatureFixture { config.settings().forceCellularDataEntryEnabled }
        val context = HookContext(config, TargetAdaptation(
            identity = "test",
            dualPane = DualPaneTarget { error("unrelated dual pane") },
            editorialVideo = EditorialVideoTarget { error("unrelated editorial video") },
            bidirectionalLyricBlur = BidirectionalLyricBlurTarget { error("unrelated blur") },
            hleMetadata = HleMetadataTarget { error("unrelated metadata") },
            cellularDataEntry = fixture.target,
        ))
        assertEquals(FeatureState.DISABLED, CellularDataEntryFeature().install(context).state)
        assertEquals(3, fixture.hooks.registrations)
        values["force_cellular_data_entry_enabled"] = true
        assertEquals(true, fixture.hooks.availability(false))
        assertEquals(FeatureState.ACTIVE, CellularDataEntryFeature().install(context).state)
        assertEquals(3, fixture.hooks.registrations)
        values["force_cellular_data_entry_enabled"] = false
        assertEquals(false, fixture.hooks.availability(false))
    }

    @Test
    fun `unsupported capability maps to the existing feature health state`() {
        assertEquals(FeatureState.UNSUPPORTED,
            TargetCapabilityInstall.Unsupported("unverified build").toFeatureInstallResult().state)
    }

    private class FeatureFixture(enabled: () -> Boolean) {
        class Hooks(private val enabled: () -> Boolean) {
            var registrations = 0
            fun availability(original: Boolean): Boolean = original || enabled()
        }
        val hooks = Hooks(enabled)
        private var installed: TargetCapabilityInstall? = null
        val target = CellularDataEntryTarget {
            installed ?: TargetCapabilityInstall.Active("installed").also {
                hooks.registrations = 3
                installed = it
            }
        }
    }
}
