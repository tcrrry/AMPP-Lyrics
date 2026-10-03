package dev.amenhancer.module.hook

import java.lang.reflect.Proxy
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test

class FragmentSettingsModelsTest {
    private val models = FragmentSettingsModels.resolve(SettingsContractLoader(), FragmentSettingsContract())

    @Test
    fun `native constructors retain enabled navigation style and host Unit`() {
        var opened = 0
        val category = models.createCategory { opened++ } as SettingsHostCategory
        val row = category.c.single() as SettingsHostAction
        assertEquals("", category.a)
        assertEquals("AM++", row.b)
        assertTrue(row.d)
        assertNull(row.c)
        assertNull(row.e)
        assertNull(row.f)
        assertFalse(row.g)
        assertTrue(row.h)
        assertFalse(row.i)
        val callback = checkNotNull(row.j)
        assertTrue(Proxy.isProxyClass(callback.javaClass))
        assertTrue(callback.javaClass.classLoader is SettingsContractLoader)
        assertSame(SettingsHostUnit.a, callback.invoke())
        assertEquals(1, opened)
        assertTrue(callback == callback)
        assertEquals(System.identityHashCode(callback), callback.hashCode())
        assertEquals("ampp_embedded_settings_preference", callback.toString())
    }

    @Test
    fun `prepend copies immutable result and deduplicates by module callback identity not title`() {
        val own = models.createCategory {} as SettingsHostCategory
        val staleOwn = models.createCategory {} as SettingsHostCategory
        val hostCallback = object : SettingsHostFunction0 { override fun invoke(): Any = SettingsHostUnit.a }
        val sameTitle = SettingsHostCategory("", listOf(SettingsHostAction("AM++", null, true, null, null, hostCallback, 0x17a)), null, 0x3b)
        val host = Any()
        val original = Collections.unmodifiableList(listOf(staleOwn, host, sameTitle, staleOwn))
        val result = models.prependUnique(original, own) as List<*>
        assertEquals(listOf(own, host, sameTitle), result)
        assertEquals(4, original.size)
        assertSame(own, result.first())
        assertSame(sameTitle, result.last())
        assertSame(host, result[1])
        assertEquals(listOf(own), models.prependUnique(emptyList<Any>(), own))
        assertNull(models.prependUnique(null, own))
        assertSame(host, models.prependUnique(host, own))
    }

    @Test
    fun `callback failure cannot break host event dispatch and still returns host Unit`() {
        val row = (models.createCategory { error("settings surface unavailable") } as SettingsHostCategory).c.single() as SettingsHostAction
        assertSame(SettingsHostUnit.a, row.j!!.invoke())
    }

    @Test
    fun `unregistered viewmodel remains native and each rebuild reuses one row`() {
        val opened = mutableListOf<Any>()
        val sessions = FragmentSettingsSessions(models, opened::add)
        val fragment = Any()
        val viewModel = Any()
        val original = listOf(Any())
        assertSame(original, sessions.transform(viewModel, original))
        assertTrue(sessions.bind(fragment, viewModel))
        val first = sessions.transform(viewModel, original) as List<*>
        val second = sessions.transform(viewModel, first) as List<*>
        assertEquals(2, second.size)
        assertSame(first.first(), second.first())
        assertTrue(sessions.bind(fragment, viewModel))
        assertSame(first.first(), (sessions.transform(viewModel, original) as List<*>).first())
        val row = (second.first() as SettingsHostCategory).c.single() as SettingsHostAction
        row.j!!.invoke()
        assertEquals(listOf(fragment), opened)
    }

    @Test
    fun `view destruction revokes old row and recreation creates a fresh callback`() {
        var opened = 0
        val sessions = FragmentSettingsSessions(models) { opened++ }
        val fragment = Any()
        val viewModel = Any()
        val original = emptyList<Any>()
        sessions.bind(fragment, viewModel)
        val first = (sessions.transform(viewModel, original) as List<*>).first() as SettingsHostCategory
        val oldClick = (first.c.single() as SettingsHostAction).j!!
        sessions.unbind(fragment)
        assertSame(original, sessions.transform(viewModel, original))
        oldClick.invoke()
        assertEquals(0, opened)
        sessions.bind(fragment, viewModel)
        val next = (sessions.transform(viewModel, original) as List<*>).first() as SettingsHostCategory
        assertNotSame(first, next)
        oldClick.invoke()
        (next.c.single() as SettingsHostAction).j!!.invoke()
        assertEquals(1, opened)
        sessions.clear()
        (next.c.single() as SettingsHostAction).j!!.invoke()
        assertEquals(1, opened)
    }

    @Test
    fun `changing viewmodel revokes the former model and callback`() {
        var opened = 0
        val sessions = FragmentSettingsSessions(models) { opened++ }
        val fragment = Any()
        val oldVm = Any()
        val nextVm = Any()
        val original = emptyList<Any>()
        sessions.bind(fragment, oldVm)
        val old = (sessions.transform(oldVm, original) as List<*>).first() as SettingsHostCategory
        sessions.bind(fragment, nextVm)
        assertSame(original, sessions.transform(oldVm, original))
        (old.c.single() as SettingsHostAction).j!!.invoke()
        assertEquals(0, opened)
        assertEquals(1, (sessions.transform(nextVm, original) as List<*>).size)
    }

    @Test
    fun `a shared viewmodel moves ownership to its new Fragment and revokes the former callback`() {
        val opened = mutableListOf<Any>()
        val sessions = FragmentSettingsSessions(models, opened::add)
        val firstFragment = Any()
        val secondFragment = Any()
        val viewModel = Any()
        sessions.bind(firstFragment, viewModel)
        val first = (sessions.transform(viewModel, emptyList<Any>()) as List<*>).first() as SettingsHostCategory
        sessions.bind(secondFragment, viewModel)
        val next = (sessions.transform(viewModel, emptyList<Any>()) as List<*>).first() as SettingsHostCategory
        (first.c.single() as SettingsHostAction).j!!.invoke()
        (next.c.single() as SettingsHostAction).j!!.invoke()
        assertEquals(listOf(secondFragment), opened)
        sessions.unbind(firstFragment)
        (next.c.single() as SettingsHostAction).j!!.invoke()
        assertEquals(listOf(secondFragment, secondFragment), opened)
    }

    @Test
    fun `full preference descriptor rejects another getPreferenceItems overload`() {
        val names = FragmentSettingsContract()
        val method = names.preferenceItemsMethod(SettingsContractLoader())
        assertEquals(29, method.parameterCount)
        assertEquals(SettingsHostFunction0::class.java, method.parameterTypes[8])
        assertThrows(NoSuchMethodException::class.java) {
            names.preferenceItemsMethod(SettingsContractLoader(mapOf(names.viewModelClass to SettingsWrongViewModel::class.java)))
        }
        assertThrows(NoSuchFieldException::class.java) {
            FragmentSettingsModels.resolve(SettingsContractLoader(), names.copy(actionCallbackField = "wrong"))
        }
    }
}
