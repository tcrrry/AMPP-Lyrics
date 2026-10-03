package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Intent
import android.view.View
import java.lang.reflect.Method
import org.junit.Assert.*
import org.junit.Test

class FragmentSettingsRuntimeTest {
    @Test
    fun `native getter compose view lifecycle and inherited SAF bridge commit together`() {
        val hooks = RecordingHooks()
        val runtime = FragmentSettingsRuntime(SettingsContractLoader(), FragmentSettingsContract(), hooks)
        val result = runtime.install(NO_UI_OBSERVER)
        assertTrue(result.message, result is TargetCapabilityInstall.Active)
        assertEquals(listOf("getPreferenceItems", "r1", "onViewCreated", "onResume", "onDestroyView", "onActivityResult"), hooks.methods.map { it.name })
        assertEquals(SettingsHostActivityBase::class.java, hooks.methods.last().declaringClass)
        val fragment = SettingsHostFragment()
        val original = emptyList<Any>()
        assertSame(original, hooks.after("getPreferenceItems", fragment.vm, original))
        hooks.before("r1", fragment)
        val first = hooks.after("getPreferenceItems", fragment.vm, original) as List<*>
        assertEquals(1, first.size)
        assertTrue(first.single() is SettingsHostCategory)
        hooks.before("onDestroyView", fragment)
        assertSame(original, hooks.after("getPreferenceItems", fragment.vm, original))
        hooks.before("r1", fragment)
        assertNotSame(first.single(), (hooks.after("getPreferenceItems", fragment.vm, original) as List<*>).single())
        assertSame(result, runtime.install(NO_UI_OBSERVER))
        assertEquals(6, hooks.methods.size)
    }

    @Test
    fun `every partial registration failure leaves stored callbacks dormant`() {
        for (failureAt in 1..6) {
            val hooks = RecordingHooks(failureAt)
            val runtime = FragmentSettingsRuntime(SettingsContractLoader(), FragmentSettingsContract(), hooks)
            val result = runtime.install(NO_UI_OBSERVER)
            assertTrue(result is TargetCapabilityInstall.Degraded)
            assertEquals(failureAt, hooks.methods.size)
            val fragment = SettingsHostFragment()
            val original = emptyList<Any>()
            hooks.before("r1", fragment)
            assertFalse(runtime.bind(fragment))
            assertSame(original, hooks.after("getPreferenceItems", fragment.vm, original))
            assertSame(result, runtime.install(NO_UI_OBSERVER))
            assertEquals(failureAt, hooks.methods.size)
        }
    }

    @Test
    fun `missing models or wrong getter descriptor fail before installing any callback`() {
        val names = FragmentSettingsContract()
        listOf(
            SettingsContractLoader(mapOf(names.viewModelClass to SettingsWrongViewModel::class.java)) to names,
            SettingsContractLoader() to names.copy(actionCallbackField = "wrong"),
            SettingsContractLoader() to names.copy(composeMethod = "wrong"),
        ).forEach { (loader, contract) ->
            val hooks = RecordingHooks()
            val result = FragmentSettingsRuntime(loader, contract, hooks).install(NO_UI_OBSERVER)
            assertTrue(result is TargetCapabilityInstall.Degraded)
            assertTrue(hooks.methods.isEmpty())
        }
    }

    @Test
    fun `inherited SAF callback leaves unrelated receiver and original result unchanged`() {
        val hooks = RecordingHooks()
        val runtime = FragmentSettingsRuntime(SettingsContractLoader(), FragmentSettingsContract(), hooks)
        assertTrue(runtime.install(NO_UI_OBSERVER) is TargetCapabilityInstall.Active)
        val original = Any()
        assertSame(original, hooks.after("onActivityResult", Any(), original))
        assertSame(original, hooks.after("onActivityResult", SettingsHostMainActivity(), original))
    }

    @Test
    fun `main class matcher uses exact ancestor and rejects lookalikes`() {
        val matcher = FragmentSettingsActivityMatcher(SettingsHostActivityBase::class.java.name)
        assertTrue(matcher.isMainClass(SettingsHostMainActivity::class.java))
        assertFalse(matcher.isMainClass(SettingsHostFragment::class.java))
        assertFalse(FragmentSettingsActivityMatcher("unverified.MainActivity").isMainClass(SettingsHostMainActivity::class.java))
    }

    private class RecordingHooks(private val failureAt: Int? = null) : FragmentSettingsHookInstaller {
        val methods = mutableListOf<Method>()
        private val befores = mutableMapOf<String, (Any?, Array<Any?>) -> Unit>()
        private val afters = mutableMapOf<String, (Any?, Array<Any?>, Any?) -> Any?>()
        override fun install(
            method: Method, scope: HookRegistrationScope,
            before: (Any?, Array<Any?>) -> Unit,
            after: (Any?, Array<Any?>, Any?) -> Any?,
        ) {
            methods += method
            befores[method.name] = before
            afters[method.name] = after
            if (methods.size == failureAt) error("partial registration failure")
        }
        // Deliberately invoke stored closures regardless of scope to audit their dormant checks.
        fun before(name: String, receiver: Any) { befores[name]?.invoke(receiver, emptyArray()) }
        fun after(name: String, receiver: Any, original: Any?): Any? =
            afters[name]?.invoke(receiver, emptyArray(), original) ?: original
    }

    companion object {
        private val NO_UI_OBSERVER = object : SettingsEntryObserver {
            override fun onSettingsPreferencesReady(fragment: Any, activity: Activity) = error("unexpected Activity")
            override fun onSettingsFragmentViewCreated(fragment: Any, activity: Activity, view: View?) = error("unexpected Activity")
            override fun onSettingsFragmentResumed(fragment: Any, activity: Activity) = error("unexpected Activity")
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean = error("unexpected Activity")
        }
    }
}
