package dev.amenhancer.module.hook

import android.content.Context
import dev.amenhancer.module.ModuleConstants
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularDataEntryTargetTest {
    @Test
    fun `dormant hooks install once and follow live toggle changes`() {
        val fixture = Fixture()
        val installed = fixture.target.install()
        assertTrue(installed is TargetCapabilityInstall.Active)
        assertEquals(3, fixture.hooks.registrations)
        assertSame(installed, fixture.target.install())
        assertEquals(3, fixture.hooks.registrations)
        assertEquals(false, fixture.hooks.availability(false))
        fixture.hooks.rebuild { assertEquals(false, fixture.hooks.sim(false)) }
        fixture.enabled = true
        assertEquals(true, fixture.hooks.availability(false))
        fixture.hooks.rebuild { assertEquals(true, fixture.hooks.sim(false)) }
        fixture.enabled = false
        assertEquals(false, fixture.hooks.availability(false))
        assertEquals(true, fixture.hooks.availability(true))
        fixture.hooks.rebuild { assertEquals(false, fixture.hooks.sim(false)) }
    }

    @Test
    fun `only first settings gate changes and queries outside scope are unchanged`() {
        val fixture = Fixture(enabled = true)
        fixture.target.install()
        assertEquals(false, fixture.hooks.sim(false))
        repeat(2) {
            fixture.hooks.rebuild {
                assertEquals(true, fixture.hooks.sim(false))
                assertEquals(false, fixture.hooks.sim(false))
                assertEquals(true, fixture.hooks.sim(true))
            }
            assertEquals(false, fixture.hooks.sim(false))
        }
    }

    @Test
    fun `disabled nested build shadows outer allowance without consuming it`() {
        val fixture = Fixture(enabled = true)
        fixture.target.install()
        fixture.hooks.rebuild {
            fixture.enabled = false
            fixture.hooks.rebuild { assertEquals(false, fixture.hooks.sim(false)) }
            // The outer rebuild took a snapshot at entry, like the original HLE scope.
            assertEquals(true, fixture.hooks.sim(false))
            assertEquals(false, fixture.hooks.sim(false))
        }
        assertEquals(false, fixture.hooks.sim(false))
    }

    @Test
    fun `enabled nested builds each get one independent allowance`() {
        val fixture = Fixture(enabled = true)
        fixture.target.install()
        fixture.hooks.rebuild {
            fixture.hooks.rebuild {
                assertEquals(true, fixture.hooks.sim(false))
                assertEquals(false, fixture.hooks.sim(false))
            }
            assertEquals(true, fixture.hooks.sim(false))
            assertEquals(false, fixture.hooks.sim(false))
        }
    }

    @Test
    fun `configuration read failure stays native and shields an outer settings allowance`() {
        var readFails = false
        val fixture = Fixture(enabledSupplier = {
            if (readFails) error("configuration temporarily unavailable")
            true
        })
        fixture.target.install()
        fixture.hooks.rebuild {
            readFails = true
            fixture.hooks.rebuild {
                assertEquals(false, fixture.hooks.sim(false))
                assertEquals(false, fixture.hooks.availability(false))
            }
            assertEquals(true, fixture.hooks.sim(false))
        }
        assertEquals(false, fixture.hooks.sim(false))
        readFails = false
        assertEquals(true, fixture.hooks.availability(false))
    }

    @Test
    fun `other thread cannot inherit an active settings allowance`() {
        val fixture = Fixture(enabled = true)
        fixture.target.install()
        fixture.hooks.rebuild {
            val otherResult = AtomicReference<Any?>()
            Thread { otherResult.set(fixture.hooks.sim(false)) }.apply { start(); join() }
            assertEquals(false, otherResult.get())
            assertEquals(true, fixture.hooks.sim(false))
        }
    }

    @Test
    fun `original build exception propagates and clears even unconsumed allowance`() {
        val fixture = Fixture(enabled = true)
        fixture.target.install()
        val failure = IllegalStateException("original build failed")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            fixture.hooks.rebuild { throw failure }
        })
        assertEquals(false, fixture.hooks.sim(false))
        fixture.hooks.rebuild { assertEquals(true, fixture.hooks.sim(false)) }
    }

    @Test
    fun `every partial registration failure keeps all callbacks dormant`() {
        for (failureAt in 1..3) {
            val fixture = Fixture(enabled = true, hooks = RecordingHooks(failureAt))
            val result = fixture.target.install()
            assertTrue(result is TargetCapabilityInstall.Degraded)
            assertEquals(failureAt, fixture.hooks.registrations)
            fixture.hooks.rebuild {
                assertEquals(false, fixture.hooks.sim(false))
                assertEquals(false, fixture.hooks.availability(false))
            }
            assertSame(result, fixture.target.install())
            assertEquals(failureAt, fixture.hooks.registrations)
        }
    }

    @Test
    fun `missing availability resolves before any callbacks register`() {
        val fixture = Fixture(enabled = true, includeAvailability = false)
        val result = fixture.target.install()
        assertTrue(result is TargetCapabilityInstall.Degraded)
        assertTrue(result.message.contains("cellular-availability"))
        assertEquals(0, fixture.hooks.registrations)
    }

    @Test
    fun `unsupported version tuples never query symbols or create hooks`() {
        listOf(
            TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.1", 1583L),
            TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.3", 1586L),
            TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.4", 1599L),
            TargetBuild("other.package", "6.5.3", 1599L),
            TargetBuild.UNKNOWN,
        ).forEach { build ->
            val target = AppleMusicCellularDataEntryTarget(
                symbols = object : TargetSymbolResolver {
                    override fun <T : Any> resolve(symbol: TargetSymbolKey<T>): TargetResolution<T> =
                        error("Unsupported build must not query ${symbol.id}")
                },
                build = build,
                enabled = { true },
                hooksFactory = { error("Unsupported build must not create hooks") },
            )
            assertTrue(target.install() is TargetCapabilityInstall.Unsupported)
        }
    }





    private class Fixture(
        var enabled: Boolean = false,
        val hooks: RecordingHooks = RecordingHooks(),
        includeAvailability: Boolean = true,
        enabledSupplier: (() -> Boolean)? = null,
    ) {
        private val classes = buildMap {
            put("com.apple.android.music.settings.fragment.SettingsFragment", SettingsFixture::class.java)
            put("Oa.c", SimFixture::class.java)
            if (includeAvailability) {
                put("com.apple.android.music.playback.connectivity.FuseConnectivityChecker", AvailabilityFixture::class.java)
            }
        }
        val target = AppleMusicCellularDataEntryTarget(
            symbols = IndexedTargetSymbolResolver(BUILD, object : TargetClassSource {
                override fun classNames(): List<String> = error("Profile-only hooks must not scan DEX")
                override fun loadClass(name: String): Class<*>? = classes[name]
            }),
            build = BUILD,
            enabled = enabledSupplier ?: { enabled },
            hooksFactory = { hooks },
        )
    }

    private class RecordingHooks(private val failureAt: Int? = null) : CellularDataEntryHookInstaller {
        var registrations = 0
        private val overrides = mutableMapOf<String, (Any?) -> Any?>()
        private var enter: (() -> Unit)? = null
        private var exit: (() -> Unit)? = null
        override fun overrideResult(method: Method, override: (Any?) -> Any?) {
            overrides[method.name] = override
            registered()
        }
        override fun withScope(method: Method, enter: () -> Unit, exit: () -> Unit) {
            assertEquals("t1", method.name)
            this.enter = enter
            this.exit = exit
            registered()
        }
        private fun registered() {
            registrations++
            // Throw after storing the callback, to exercise the strongest partial-install case.
            if (registrations == failureAt) error("injected registration failure")
        }
        fun sim(original: Any?): Any? = overrides["e"]?.invoke(original) ?: original
        fun availability(original: Any?): Any? = overrides["isCellularAvailable"]?.invoke(original) ?: original
        fun rebuild(body: () -> Unit) {
            enter?.invoke()
            try { body() } finally { exit?.invoke() }
        }
    }
    private class SettingsFixture { fun t1() = Unit }
    private class AvailabilityFixture { fun isCellularAvailable(): Boolean = false }
    private class SimFixture {
        companion object {
            @JvmStatic @Suppress("UNUSED_PARAMETER") fun e(context: Context?): Boolean = false
        }
    }
    private companion object {
        val BUILD = TargetBuild(ModuleConstants.TARGET_PACKAGE, "6.5.3", 1599L)
    }
}
