package dev.amenhancer.module.hook

import org.junit.Assert.*
import org.junit.Test

class BackdropPluginAbiTest {
    @Test fun legacyDefaultEntryUsedByMediaPluginIsStillExported() {
        val type=Class.forName("com.kyant.backdrop.DrawBackdropModifierKt",false,javaClass.classLoader)
        val signature="androidx.compose.ui.Modifier;com.kyant.backdrop.Backdrop;" +
            "kotlin.jvm.functions.Function0;kotlin.jvm.functions.Function1;" +
            "kotlin.jvm.functions.Function0;kotlin.jvm.functions.Function0;kotlin.jvm.functions.Function0;" +
            "kotlin.jvm.functions.Function1;int;com.kyant.backdrop.backdrops.LayerBackdrop;" +
            "kotlin.jvm.functions.Function1;kotlin.jvm.functions.Function2;" +
            "kotlin.jvm.functions.Function1;kotlin.jvm.functions.Function1;int;java.lang.Object"
        val method=type.declaredMethods.singleOrNull { it.name=="drawBackdrop-QPzRGKA\$default" &&
            it.parameterTypes.joinToString(";") { p -> p.name }==signature }
        assertNotNull("Existing media plugins must link without recompilation",method)
        assertEquals("androidx.compose.ui.Modifier",method!!.returnType.name)
        assertTrue(type.declaredMethods.any { it.name.startsWith("drawBackdrop-") &&
            it.parameterTypes.any { p -> p==java.lang.Long.TYPE } })
    }
}
