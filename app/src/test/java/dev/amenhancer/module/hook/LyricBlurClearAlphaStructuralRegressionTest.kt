package dev.amenhancer.module.hook

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STRUCTURAL EXPERIMENT TEST (no Android View test dependencies in this project):
 * guards the single-variable experiment that [LyricBlurRenderer.clear] must stop
 * force-writing `view.alpha = 1f` on instrumental rows. Hypothesis: that write
 * stomps the instrumental interlude three-dot animation, which animates row alpha.
 * [clearAll] (fragment destruction) is deliberately out of scope and keeps its write.
 */
class LyricBlurClearAlphaStructuralRegressionTest {
    private val rendererSource: String by lazy {
        sourceFile("LyricBlurRenderer.kt").readText()
    }

    private val clearBody: String by lazy {
        rendererSource.substringAfter("fun clear(view: View)")
            .substringBefore("fun clearAll()")
    }

    @Test
    fun `instrumental row clear path stops force-writing alpha`() {
        assertFalse(
            "LyricBlurRenderer.clear() must not write view.alpha = 1f; it may stomp the interlude dot animation",
            clearBody.contains("view.alpha = 1f"),
        )
    }

    @Test
    fun `clear path keeps its blur state and render effect duties`() {
        assertTrue(clearBody.contains("transitions.remove(view)"))
        assertTrue(clearBody.contains("view.setRenderEffect(null)"))
    }

    private fun sourceFile(name: String): File = sequenceOf(
        File("src/main/java/dev/amenhancer/module/hook/$name"),
        File("app/src/main/java/dev/amenhancer/module/hook/$name"),
    ).firstOrNull(File::isFile) ?: error("$name was not found from the unit-test working directory")
}
