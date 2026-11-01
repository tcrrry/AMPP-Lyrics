package dev.amenhancer.module.hook

import android.view.View
import android.view.ViewGroup
import java.util.IdentityHashMap

/** Framework scroll contracts work across the host/module AndroidX ClassLoader boundary. */
internal class HostScrollBoundaries {
    private val composeOwners = IdentityHashMap<Class<*>, Boolean>()

    /** The outer ComposeView does not expose its child's semantics scroll state. */
    fun composeOwner(root: View): View? {
        val ownsScrollSemantics = composeOwners.getOrPut(root.javaClass) {
            runCatching {
                root.javaClass.getMethod("canScrollVertically", Int::class.javaPrimitiveType)
                    .declaringClass != View::class.java
            }.getOrDefault(false)
        }
        if (ownsScrollSemantics) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                composeOwner(root.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    fun canScroll(view: View, direction: Int): Boolean? =
        runCatching { view.canScrollVertically(direction) }.getOrNull()

    fun clear() = composeOwners.clear()
}
