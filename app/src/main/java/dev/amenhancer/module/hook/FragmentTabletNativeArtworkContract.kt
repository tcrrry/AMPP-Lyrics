package dev.amenhancer.module.hook

import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 1606's own selector handles both the song card and native queue header. No artwork mutation. */
internal fun resolveFragmentNativeCoverGetter(controller: Class<*>): Method =
    controller.getDeclaredMethod("f1", controller).apply {
        check(Modifier.isStatic(modifiers) && returnType == View::class.java) {
            "Native artwork selector descriptor mismatch: $this"
        }
        isAccessible = true
    }

/** Native expanded offset includes fit-to-contents and system-inset rules; never guess from slide. */
internal fun resolveFragmentExpandedSheetTop(behavior: Class<*>): Method =
    behavior.getDeclaredMethod("F").apply {
        check(!Modifier.isStatic(modifiers) && returnType == Int::class.javaPrimitiveType) {
            "Native expanded sheet offset descriptor mismatch: $this"
        }
        isAccessible = true
    }
