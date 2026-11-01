package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class FragmentPlayerRecoveryTargetTest {
    private val build = TargetBuild("com.apple.android.music", "7.0.0-beta", 1606)

    @Test fun `legacy and unknown builds never resolve or register repair hooks`() {
        for (candidate in listOf(TargetBuild("com.apple.android.music", "6.5.3", 1599),
            build.copy(versionCode = 1608), build.copy(versionName = "7.0.0"), build.copy(packageName = ""))) {
            val symbols = FixtureSymbols()
            val target = FragmentPlayerRecoveryTarget(symbols, candidate) { _, _ -> fail("unexpected repair registration") }
            assertTrue(target.install() is TargetCapabilityInstall.Unsupported)
            assertEquals(0, symbols.calls)
        }
    }

    @Test fun `missing native member leaves all repair hooks unregistered`() {
        val symbols = FixtureSymbols(missing = "player-background-shader")
        val target = FragmentPlayerRecoveryTarget(symbols, build) { _, _ -> fail("partial contract registered") }
        assertTrue(target.install() is TargetCapabilityInstall.Degraded)
        val attempts = symbols.calls
        assertSame(target.install(), target.install())
        assertEquals(attempts, symbols.calls)
    }

    @Test fun `repair callbacks become active only after both layers register once`() {
        var registrations = 0
        var active: HookRegistrationScope? = null
        val target = FragmentPlayerRecoveryTarget(FixtureSymbols(), build) { _, scope ->
            assertFalse(scope.isActive)
            registrations++
            active = scope
        }
        val result = target.install()
        assertTrue(result is TargetCapabilityInstall.Active)
        assertTrue(checkNotNull(active).isActive)
        assertSame(result, target.install())
        assertEquals(1, registrations)
    }

    @Test fun `partial hook registration closes scope and its listener cleanup`() {
        var cleaned = false
        var captured: HookRegistrationScope? = null
        val target = FragmentPlayerRecoveryTarget(FixtureSymbols(), build) { _, scope ->
            captured = scope
            scope.onClose { cleaned = true }
            error("fixture registration failure")
        }
        assertTrue(target.install() is TargetCapabilityInstall.Degraded)
        assertTrue(cleaned)
        assertTrue(checkNotNull(captured).isClosed)
        assertFalse(checkNotNull(captured).isActive)
    }

    private class FixtureSymbols(private val missing: String? = null) : TargetSymbolResolver {
        var calls = 0
        override fun <T : Any> resolve(symbol: TargetSymbolKey<T>): TargetResolution<T> {
            calls++
            if (symbol.id == missing) return TargetResolution.Missing(symbol.id, "fixture")
            val fields = setOf("player-artwork-video-mode", "player-artwork-size-animation", "player-artwork-static-baseline",
                "player-artwork-target-size", "player-pane-enter-running", "player-pane-shared-running", "player-page-behavior",
                "player-page-behavior-state", "player-background-source", "player-background-queued", "player-background-derived",
                "player-background-shader", "player-background-previous-shader", "player-background-paint",
                "player-background-previous-paint", "player-background-fade")
            val member: Any = if (symbol.id in fields) Members::class.java.getDeclaredField("value")
                else Members::class.java.getDeclaredMethod("method")
            @Suppress("UNCHECKED_CAST")
            return TargetResolution.Found(symbol.id, member as T, SymbolMatch.VERSION_PROFILE, "fixture")
        }
    }
    private class Members {
        @JvmField var value: Any? = null
        fun method() = Unit
    }
}
