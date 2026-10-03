package dev.amenhancer.module.hook

import android.view.View
import org.junit.Assert.*
import org.junit.Test

class FragmentTabletNativeArtworkContractTest {
    class NativeBehavior { fun F(): Int = 24 }
    class WrongOffsetReturn { fun F(): Long = 24L }
    class StaticOffset { companion object { @JvmStatic fun F(): Int = 24 } }
    class SimilarOffset { fun F(view: View): Int = 24 }

    @Test fun expandedViewportUsesTheNativeInstanceOffset() {
        val method = resolveFragmentExpandedSheetTop(NativeBehavior::class.java)
        assertEquals(24, method.invoke(NativeBehavior()))
        assertSame(Int::class.javaPrimitiveType, method.returnType)
        assertTrue(method.parameterTypes.isEmpty())
    }
    @Test fun wrongExpandedOffsetReturnIsRejected() {
        assertTrue(runCatching { resolveFragmentExpandedSheetTop(WrongOffsetReturn::class.java) }.isFailure)
    }
    @Test fun aStaticExpandedOffsetCannotReplaceTheBehaviorInstance() {
        assertTrue(runCatching { resolveFragmentExpandedSheetTop(StaticOffset::class.java) }.isFailure)
    }
    @Test fun expandedOffsetWithAnExtraArgumentIsRejected() {
        assertTrue(runCatching { resolveFragmentExpandedSheetTop(SimilarOffset::class.java) }.isFailure)
    }

    class Native { companion object { @JvmStatic fun f1(owner: Native): View? = null } }
    class WrongReturn { companion object { @JvmStatic fun f1(owner: WrongReturn): Any? = null } }
    class Instance { fun f1(owner: Instance): View? = null }
    class Similar { companion object { @JvmStatic fun f1(owner: Any): View? = null } }

    @Test fun exactNativeSelectorIsReadOnlyAndOwnedByTheControllerClass() {
        val method = resolveFragmentNativeCoverGetter(Native::class.java)
        assertSame(Native::class.java, method.declaringClass)
        assertSame(View::class.java, method.returnType)
        assertArrayEquals(arrayOf(Native::class.java), method.parameterTypes)
    }
    @Test fun wrongReturnDescriptorCannotBeAcceptedAsArtwork() {
        assertTrue(runCatching { resolveFragmentNativeCoverGetter(WrongReturn::class.java) }.isFailure)
    }
    @Test fun anInstanceMethodCannotSubstituteForTheNativeStaticSelector() {
        assertTrue(runCatching { resolveFragmentNativeCoverGetter(Instance::class.java) }.isFailure)
    }
    @Test fun similarNameWithBroadOwnerArgumentIsNotACompatibleFallback() {
        assertTrue(runCatching { resolveFragmentNativeCoverGetter(Similar::class.java) }.isFailure)
    }
}
