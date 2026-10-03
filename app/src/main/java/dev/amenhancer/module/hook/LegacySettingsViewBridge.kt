package dev.amenhancer.module.hook
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.ArrayDeque
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationHandler
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles
private data class NativePreferenceGroupAccessors(
    val count: Method,
    val itemAt: Method,
    val remove: Method?,
)

internal class LegacySettingsViewBridge(context: Context, private val onOpen: (Activity)->Unit) : SettingsViewBridge {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val build = targetBuild(context)
    private val names = checkNotNull(AppleMusicHostProfiles.find(build.packageName,build.versionName,build.versionCode))
        .document.getJSONObject("settings")
    override fun refreshNativePreferenceAdapter(root: ViewGroup?) {
        val recycler = root?.let(::findRecyclerView) ?: return
        runCatching {
            val adapter = recycler.javaClass.getMethod("getAdapter").invoke(recycler) ?: return
            adapter.javaClass.getMethod("notifyDataSetChanged").invoke(adapter)
        }
    }

    override fun fragmentView(fragment: Any): ViewGroup? = runCatching {
        ModernXposedRuntime.callMethod(fragment, "getView") as? ViewGroup
    }.getOrNull()

    override fun findSettingsListOverlayContainer(root: View): ViewGroup? {
        val pending = ArrayDeque<View>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1024) {
            val view = pending.removeFirst()
            if (view is ViewGroup && isRecyclerView(view)) {
                return view.parent as? ViewGroup
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    pending.addLast(view.getChildAt(index))
                }
            }
        }
        return null
    }

    private fun findRecyclerView(root: View): ViewGroup? {
        val pending = ArrayDeque<View>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 1024) {
            val view = pending.removeFirst()
            if (view is ViewGroup && isRecyclerView(view)) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    pending.addLast(view.getChildAt(index))
                }
            }
        }
        return null
    }

    override fun injectNativeSettingsPreference(fragment: Any, activity: Activity): Boolean {
        val classLoader = fragment.javaClass.classLoader ?: activity.javaClass.classLoader ?: return false
        val preferenceClass = Class.forName(
            names.getString("preferenceClass"),
            false,
            classLoader,
        )
        val key = "ampp_embedded_settings_preference"
        val screen = findNativePreferenceScreen(fragment)
        val screenMatches = screen?.let {
            findNativePreferencesByKey(it, preferenceClass, key)
        }.orEmpty()
        val existing = screenMatches.firstOrNull() ?: runCatching {
            // AndroidX 6.5.1/6.5.2 maps PreferenceFragmentCompat.findPreference()
            // to t0(String); keep this as a fallback for repacked builds where
            // the PreferenceScreen field is not directly discoverable.
            ModernXposedRuntime.callMethod(fragment, names.getString("findMethod"), key)
        }.getOrNull()
        if (existing != null) {
            val keeper = screen?.let {
                removeDuplicateNativePreferences(it, preferenceClass, key, existing)
            } ?: existing
            if (!installNativePreferenceClick(preferenceClass, keeper, activity)) return false
            return hasNativePreferenceClick(preferenceClass, keeper)
        }

        val preference = preferenceClass
            .getConstructor(Context::class.java)
            .newInstance(activity)
        val keyWasSet = runCatching {
            preferenceClass.getDeclaredField(names.getString("keyField")).apply { isAccessible = true }.set(preference, key)
            true
        }.getOrDefault(false)
        if (!keyWasSet) {
            return false
        }
        // On the verified 6.5.1/6.5.2 AndroidX builds, K is setTitle and J is
        // setSummary (J rejects a SummaryProvider, which distinguishes them).
        preferenceClass.getDeclaredMethod(names.getString("titleMethod"), CharSequence::class.java)
            .apply { isAccessible = true }
            .invoke(preference, "AM++ 模块设置")
        preferenceClass.getDeclaredMethod(names.getString("summaryMethod"), CharSequence::class.java)
            .apply { isAccessible = true }
            .invoke(preference, "字体、歌词与模块功能")
        if (!installNativePreferenceClick(preferenceClass, preference, activity)) return false

        val targetScreen = screen ?: return false
        // AndroidX 6.5.1/6.5.2 maps PreferenceGroup.P() to addPreference(); S()
        // is the corresponding remove path. Add once, then normalize the
        // whole screen so repeated lifecycle callbacks cannot accumulate rows.
        ModernXposedRuntime.callMethod(targetScreen, names.getString("addMethod"), preference)
        val keeper = removeDuplicateNativePreferences(
            targetScreen,
            preferenceClass,
            key,
            preference,
        ) ?: preference
        if (keeper !== preference) {
            installNativePreferenceClick(preferenceClass, keeper, activity)
        }
        return hasNativePreferenceClick(preferenceClass, keeper)
    }

    private fun findNativePreferencesByKey(
        screen: Any,
        preferenceClass: Class<*>,
        key: String,
    ): List<Any> {
        val accessors = findNativePreferenceGroupAccessors(screen, preferenceClass)
            ?: return findNativePreferencesInBackingList(screen, preferenceClass, key)
        val count = runCatching {
            accessors.count.apply { isAccessible = true }.invoke(screen) as? Int
        }.getOrNull()?.coerceIn(0, 256)
            ?: return findNativePreferencesInBackingList(screen, preferenceClass, key)
        val matches = ArrayList<Any>()
        for (index in 0 until count) {
            val item = runCatching {
                accessors.itemAt.apply { isAccessible = true }.invoke(screen, index)
            }.getOrNull() ?: continue
            if (preferenceClass.isInstance(item) && nativePreferenceKey(item) == key) {
                matches += item
            }
        }
        return matches
    }

    private fun findNativePreferencesInBackingList(
        screen: Any,
        preferenceClass: Class<*>,
        key: String,
    ): List<Any> {
        var current: Class<*>? = screen.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                if (!java.util.List::class.java.isAssignableFrom(field.type)) continue
                val list = runCatching {
                    field.apply { isAccessible = true }.get(screen) as? List<*>
                }.getOrNull() ?: continue
                val matches = list.filterIsInstance<Any>().filter {
                    preferenceClass.isInstance(it) && nativePreferenceKey(it) == key
                }
                if (matches.isNotEmpty()) return matches
            }
            current = current.superclass
        }
        return emptyList()
    }

    /** Keep the earliest matching Preference and remove every later duplicate. */
    private fun removeDuplicateNativePreferences(
        screen: Any,
        preferenceClass: Class<*>,
        key: String,
        preferred: Any? = null,
    ): Any? {
        val accessors = findNativePreferenceGroupAccessors(screen, preferenceClass)
        val matches = findNativePreferencesByKey(screen, preferenceClass, key)
        if (matches.isEmpty()) return preferred
        val keeper = matches.firstOrNull { it === preferred } ?: matches.first()
        matches.forEach { candidate ->
            if (candidate === keeper) return@forEach
            val removed = accessors?.remove?.let { removeMethod ->
                runCatching {
                    removeMethod.apply { isAccessible = true }.invoke(screen, candidate) as? Boolean
                }.getOrNull() == true
            } == true
            if (!removed) removeNativePreferenceFromBackingList(screen, candidate)
        }
        return keeper
    }

    private fun findNativePreferenceGroupAccessors(
        screen: Any,
        preferenceClass: Class<*>,
    ): NativePreferenceGroupAccessors? {
        val hierarchy = buildList {
            var current: Class<*>? = screen.javaClass
            while (current != null) {
                add(current)
                current = current.superclass
            }
        }
        val orderedTypes = (hierarchy.filter { it.name.endsWith(".PreferenceGroup") } + hierarchy)
            .distinct()
        for (type in orderedTypes) {
            if (!type.name.startsWith("androidx.preference.")) continue
            val methods = type.declaredMethods.toList()
            val count = methods.firstOrNull {
                it.parameterTypes.isEmpty() && it.returnType == Int::class.javaPrimitiveType
            } ?: continue
            val itemAt = methods.firstOrNull {
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                    (preferenceClass.isAssignableFrom(it.returnType) || it.returnType == Any::class.java)
            } ?: continue
            val remove = methods.firstOrNull {
                it.parameterTypes.size == 1 &&
                    (it.parameterTypes[0].isAssignableFrom(preferenceClass) ||
                        preferenceClass.isAssignableFrom(it.parameterTypes[0])) &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            return NativePreferenceGroupAccessors(count, itemAt, remove)
        }
        return null
    }

    private fun nativePreferenceKey(preference: Any): String? {
        var current: Class<*>? = preference.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull {
                it.name == names.getString("keyField") && it.type == String::class.java
            }
            if (field != null) {
                return runCatching {
                    field.apply { isAccessible = true }.get(preference) as? String
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun removeNativePreferenceFromBackingList(screen: Any, target: Any): Boolean {
        var current: Class<*>? = screen.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                if (!java.util.List::class.java.isAssignableFrom(field.type)) continue
                val list = runCatching {
                    field.apply { isAccessible = true }.get(screen) as? MutableList<Any?>
                }.getOrNull() ?: continue
                if (list.none { it === target }) continue
                if (runCatching { list.remove(target) }.getOrDefault(false)) return true
            }
            current = current.superclass
        }
        return false
    }

    private fun hasNativePreferenceClick(preferenceClass: Class<*>, preference: Any): Boolean =
        runCatching {
            findNativePreferenceClickField(preferenceClass)
                ?.apply { isAccessible = true }
                ?.get(preference) != null
        }.getOrDefault(false)

    private fun installNativePreferenceClick(
        preferenceClass: Class<*>,
        preference: Any,
        activity: Activity,
    ): Boolean {
        val clickField = findNativePreferenceClickField(preferenceClass) ?: return false
        val clickInterface = clickField.type
        return runCatching {
            val listener = Proxy.newProxyInstance(
                clickInterface.classLoader,
                arrayOf(clickInterface),
                InvocationHandler { _, method, _ ->
                    if (method.returnType == Boolean::class.javaPrimitiveType) {
                        mainHandler.post { onOpen(activity) }
                        true
                    } else {
                        null
                    }
                },
            )
            clickField.apply { isAccessible = true }.set(preference, listener)
            true
        }.getOrDefault(false)
    }

    private fun findNativePreferenceClickField(preferenceClass: Class<*>): java.lang.reflect.Field? {
        val clickInterface = preferenceClass.declaredClasses.firstOrNull { nested ->
            nested.isInterface && nested.declaredMethods.any { method ->
                method.parameterTypes.contentEquals(arrayOf(preferenceClass)) &&
                    method.returnType == Boolean::class.javaPrimitiveType
            }
        } ?: return null
        return preferenceClass.declaredFields.firstOrNull { field ->
            field.type == clickInterface
        }
    }

    private fun findNativePreferenceScreen(fragment: Any): Any? {
        var fragmentType: Class<*>? = fragment.javaClass
        while (fragmentType != null) {
            val manager = fragmentType.declaredFields.asSequence()
                .mapNotNull { field ->
                    runCatching {
                        field.apply { isAccessible = true }.get(fragment)
                    }.getOrNull()
                }
                .firstOrNull { candidate ->
                    candidate.javaClass.declaredFields.any { field ->
                        field.type.name == names.getString("screenClass")
                    }
                }
            if (manager != null) {
                var managerType: Class<*>? = manager.javaClass
                while (managerType != null) {
                    val screen = managerType.declaredFields.asSequence()
                        .filter { it.type.name == names.getString("screenClass") }
                        .mapNotNull { field ->
                            runCatching {
                                field.apply { isAccessible = true }.get(manager)
                            }.getOrNull()
                        }
                        .firstOrNull()
                    if (screen != null) return screen
                    managerType = managerType.superclass
                }
            }
            fragmentType = fragmentType.superclass
        }
        return null
    }

    override fun findSettingsInsertionContainer(root: ViewGroup): ViewGroup? {
        if (isRecyclerView(root)) return null
        if (isScrollView(root)) {
            val child = root.getChildAt(0) as? ViewGroup ?: return null
            return findSettingsInsertionContainer(child) ?: child
        }
        if (root is LinearLayout) return root
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index) as? ViewGroup ?: continue
            findSettingsInsertionContainer(child)?.let { return it }
        }
        return null
    }

    private fun isScrollView(view: ViewGroup): Boolean =
        view is ScrollView || view.javaClass.name.endsWith("NestedScrollView")

    private fun isRecyclerView(view: ViewGroup): Boolean =
        view.javaClass.name.contains("RecyclerView")

}
