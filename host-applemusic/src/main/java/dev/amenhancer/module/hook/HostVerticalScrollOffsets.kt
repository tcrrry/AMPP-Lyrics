package dev.amenhancer.module.hook

import android.view.View
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** Native AndroidX instances belong to the host ClassLoader, never the module's AndroidX types. */
internal class HostVerticalScrollOffsets {
    private val readers = IdentityHashMap<Class<*>, Method?>()

    /** Resolve during container discovery so scroll callbacks only invoke a cached virtual method. */
    fun prepare(type: Class<*>) {
        if (readers.containsKey(type)) return
        // This is View's framework override contract, including RecyclerView and AbsListView.
        // Look up the implementation on the receiver's actual class; no host AndroidX cast.
        readers[type] = generateSequence(type as Class<*>?) { it.superclass }.firstNotNullOfOrNull { owner ->
            runCatching {
                owner.getDeclaredMethod("computeVerticalScrollOffset").takeIf {
                    it.returnType == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
            }.getOrNull()
        }
    }

    fun read(view: View): Int? {
        prepare(view.javaClass)
        val reader = readers[view.javaClass] ?: return null
        return try {
            reader.invoke(view) as Int
        } catch (error: Throwable) {
            // Disable only this signal source. Page touch observation remains available.
            readers[view.javaClass] = null
            ModernXposedRuntime.log("Glass scroll offset unavailable for ${view.javaClass.name}", error)
            null
        }
    }

    fun clear() = readers.clear()
}
