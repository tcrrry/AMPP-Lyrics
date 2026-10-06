package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import java.lang.reflect.Method
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], packageName = "com.apple.android.music")
class NativeSettingsColdStartTest {
    class Main : Activity() {
        override fun getPackageName() = dev.amenhancer.module.BuildConfig.HOST_PACKAGE
    }
    class VisibleRoot(host: Activity) : View(host) {
        var invalidations = 0
        override fun isShown() = true
        override fun getWindowVisibility() = VISIBLE
        override fun postInvalidateOnAnimation() { invalidations++ }
    }
    internal class ColdFragment(private val host: Activity) {
        var modelReads = 0
        fun getActivity() = host
        fun getView(): View? = null
        fun z1(): SettingsHostViewModel { modelReads++; error("preference model not ready yet") }
        fun r1(composer: SettingsHostComposer) = Unit
        fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, state: Bundle?): View? = null
        fun onViewCreated(view: View?, state: Bundle?) = Unit
        fun onResume() = Unit
        fun onDestroyView() = Unit
    }
    private class Hooks : FragmentSettingsHookInstaller {
        val befores = mutableMapOf<String, (Any?, Array<Any?>) -> Unit>()
        override fun install(method: Method, scope: HookRegistrationScope,
            before: (Any?, Array<Any?>) -> Unit, after: (Any?, Array<Any?>, Any?) -> Any?) {
            befores[method.name] = before
        }
    }
    @Test fun coldViewCreationReleasesDrawVetoBeforeCompositionOrPreferenceBinding() = handoff("onCreateView")
    @Test fun coldCompositionStillReleasesDrawVetoWhenModelCannotBind() = handoff("r1")

    private fun handoff(callback: String) {
        val controller = Robolectric.buildActivity(Main::class.java).setup()
        val host = controller.get()
        val root = VisibleRoot(host)
        host.setContentView(root)
        val names = FragmentSettingsContract()
        val hooks = Hooks()
        val runtime = FragmentSettingsRuntime(SettingsContractLoader(mapOf(names.fragmentClass to ColdFragment::class.java,
            names.mainActivity to Main::class.java)), names, hooks)
        val observer = object : SettingsEntryObserver {
            override fun onSettingsPreferencesReady(fragment: Any, activity: Activity) = Unit
            override fun onSettingsFragmentViewCreated(fragment: Any, activity: Activity, view: View?) = Unit
            override fun onSettingsFragmentResumed(fragment: Any, activity: Activity) = Unit
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) = false
        }
        assertTrue(runtime.install(observer) is TargetCapabilityInstall.Active)
        assertEquals(dev.amenhancer.module.BuildConfig.HOST_PACKAGE, host.packageName)
        val fragment = ColdFragment(host)
        val first = FragmentGlassFirstDraw(root) { true }
        @Suppress("UNCHECKED_CAST")
        val pending = FragmentGlassRuntime::class.java.getDeclaredField("firstDraws").apply { isAccessible = true }
            .get(FragmentGlassRuntime) as MutableMap<Any, FragmentGlassFirstDraw>
        pending[root] = first
        try {
            assertFalse(first.onPreDraw())
            assertFalse(runtime.bind(fragment))
            val reads = fragment.modelReads
            val invalidations = root.invalidations
            hooks.befores.getValue(callback)(fragment, emptyArray())
            if (callback == "onCreateView") assertEquals(reads, fragment.modelReads)
            assertFalse(pending.containsKey(root))
            assertTrue(root.invalidations > invalidations)
            assertTrue(first.onPreDraw())
        } finally {
            first.close(); pending.remove(root)
            FragmentGlassRuntime.settingsExited(fragment)
            controller.pause().stop().destroy()
        }
    }
}
