package dev.amenhancer.module.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlphaGradientEdgeFieldProfilesTest {
    private val beta = TargetBuild("com.apple.android.music", "7.0.0-beta", 1606)

    @Test fun `1606 accepts only its complete gradient descriptor`() {
        val fields = listOf("W", "a0", "b0", "c0").associateWith { Boolean::class.javaPrimitiveType!! } +
            listOf("d0", "e0", "f0", "g0", "p0").associateWith { Int::class.javaPrimitiveType!! }
        assertEquals(listOf("W", "a0"), AlphaGradientEdgeFieldProfiles.resolve(fields, beta)?.vertical)
        assertNull(AlphaGradientEdgeFieldProfiles.resolve(fields - "p0", beta))
    }

    @Test fun `1606 never substitutes a complete old gradient variant`() {
        val oldFields = listOf("P", "Q", "R", "S").associateWith { Boolean::class.javaPrimitiveType!! } +
            listOf("T", "U", "V", "W").associateWith { Int::class.javaPrimitiveType!! }
        assertEquals(listOf("P", "Q"), AlphaGradientEdgeFieldProfiles.resolve(oldFields)?.vertical)
        assertNull(AlphaGradientEdgeFieldProfiles.resolve(oldFields, beta))
    }
    @Test
    fun `resolves official 650 edge fields`() {
        val profile = AlphaGradientEdgeFieldProfiles.resolve(
            mapOf(
                "P" to Boolean::class.javaPrimitiveType!!,
                "Q" to Boolean::class.javaPrimitiveType!!,
                "R" to Boolean::class.javaPrimitiveType!!,
                "S" to Boolean::class.javaPrimitiveType!!,
                "T" to Int::class.javaPrimitiveType!!,
                "U" to Int::class.javaPrimitiveType!!,
                "V" to Int::class.javaPrimitiveType!!,
                "W" to Int::class.javaPrimitiveType!!,
            ),
        )

        assertEquals(listOf("P", "Q"), profile?.vertical)
        assertEquals(listOf("R", "S"), profile?.horizontal)
    }

    @Test
    fun `resolves landscape 650 edge fields`() {
        val profile = AlphaGradientEdgeFieldProfiles.resolve(
            mapOf(
                "R" to Boolean::class.javaPrimitiveType!!,
                "S" to Boolean::class.javaPrimitiveType!!,
                "T" to Boolean::class.javaPrimitiveType!!,
                "U" to Boolean::class.javaPrimitiveType!!,
                "V" to Int::class.javaPrimitiveType!!,
                "W" to Int::class.javaPrimitiveType!!,
            ),
        )

        assertEquals(listOf("R", "S"), profile?.vertical)
        assertEquals(listOf("T", "U"), profile?.horizontal)
    }

    @Test
    fun `rejects unknown edge field contracts`() {
        assertNull(
            AlphaGradientEdgeFieldProfiles.resolve(
                mapOf("R" to Boolean::class.javaPrimitiveType!!),
            ),
        )
    }
}
