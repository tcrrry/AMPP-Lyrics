package io.github.proify.lyricon.amprovider.xposed

import dev.amenhancer.module.hook.ProfileMethodContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeMetadataLifecycleContractTest {
    class Fragment {
        var resumed = false
        fun onResume() { resumed = true }
        fun onPause() { resumed = false }
    }
    class WrongReturn { fun onResume(): Int = 1; fun onPause() = Unit }
    private fun contracts(owner: Class<*>): Map<String, ProfileMethodContract> = mapOf(
        "metadata-fragment-resume-method" to ProfileMethodContract(owner.name, "onResume", emptyList(), "void", false),
        "metadata-fragment-pause-method" to ProfileMethodContract(owner.name, "onPause", emptyList(), "void", false),
    )

    @Test fun `legacy descriptors stay absent without class probing`() {
        assertNull(NativeMetadataLifecycleContract.resolve(emptyMap()) { error("Unexpected class probe") })
    }

    @Test fun `native fragment callbacks clear page priority without dropping playback priority`() {
        val (resume, pause) = requireNotNull(NativeMetadataLifecycleContract.resolve(contracts(Fragment::class.java)) { Class.forName(it) })
        val fragment = Fragment()
        val coordinator = AppleMetadataSurfaceCoordinator(clock = { 0L })
        resume.invoke(fragment)
        coordinator.onSurfaceResumed(fragment)
        coordinator.markCurrentPage(listOf("100"))
        coordinator.markVisible(listOf("100"))
        coordinator.setPlaybackMediaId("200")
        val generation = coordinator.snapshot().generation
        assertEquals(true, fragment.resumed)
        assertEquals(AppleInternalCatalogResolver.RequestPriority.VISIBLE, coordinator.requestContext("100").priority)
        pause.invoke(fragment)
        coordinator.onSurfacePaused(fragment)
        assertEquals(false, fragment.resumed)
        assertEquals(AppleInternalCatalogResolver.RequestPriority.BACKGROUND, coordinator.requestContext("100").priority)
        assertEquals(AppleInternalCatalogResolver.RequestPriority.VISIBLE, coordinator.requestContext("200").priority)
        assertEquals(false, coordinator.allowsRefresh(generation, "100"))
        assertEquals(true, coordinator.allowsRefresh(generation, "200"))
    }

    @Test fun `partial stale or foreign lifecycle descriptors fail closed`() {
        assertThrows(IllegalStateException::class.java) {
            NativeMetadataLifecycleContract.resolve(contracts(Fragment::class.java).filterKeys { it.endsWith("resume-method") }) { Class.forName(it) }
        }
        assertThrows(IllegalStateException::class.java) {
            NativeMetadataLifecycleContract.resolve(contracts(WrongReturn::class.java)) { Class.forName(it) }
        }
        assertThrows(IllegalStateException::class.java) {
            NativeMetadataLifecycleContract.resolve(contracts(Fragment::class.java)) { WrongReturn::class.java }
        }
    }
}
