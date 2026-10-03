package dev.amenhancer.module.hook

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NativeLineLyricsPresentationTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val root = object : ViewGroup(activity) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        override fun checkLayoutParams(params: LayoutParams) = params is PronunciationHeaderLayoutTest.HostParams
        override fun generateDefaultLayoutParams(): LayoutParams = PronunciationHeaderLayoutTest.HostParams(-1, -2)
    }
    private fun text() = TextView(activity).apply {
        id = View.generateViewId()
        root.addView(this, PronunciationHeaderLayoutTest.HostParams(-1, -2))
    }
    private val original = text()
    private val pronunciation = text()
    private val translation = text()
    private fun params(view: View) = view.layoutParams as PronunciationHeaderLayoutTest.HostParams
    init {
        params(original).apply { topToTop = 0; bottomToTop = pronunciation.id }
        params(pronunciation).apply { topToBottom = original.id; bottomToTop = translation.id }
        params(translation).apply { topToBottom = pronunciation.id; bottomToBottom = 0 }
    }
    private fun apply() = NativeLineLyricsPresentation.apply(root, original, pronunciation, translation)

    @Test fun lineOrderPreservesNativeBindingViewsAndOriginalText() {
        original.text = "フツフツと鳴り出す青春の音"
        pronunciation.text = "fu tsu fu tsu"
        translation.text = "青春音符"
        original.alpha = 0.7f
        apply()
        assertEquals(0, params(pronunciation).topToTop)
        assertEquals(original.id, params(pronunciation).bottomToTop)
        assertEquals(pronunciation.id, params(original).topToBottom)
        assertEquals(translation.id, params(original).bottomToTop)
        assertEquals(original.id, params(translation).topToBottom)
        assertEquals(0, params(translation).bottomToBottom)
        assertEquals(3, root.childCount)
        assertSame(root, pronunciation.parent)
        assertEquals("フツフツと鳴り出す青春の音", original.text.toString())
        assertEquals(0.7f, original.alpha, 0f)
        assertEquals(pronunciation.currentTextColor, translation.currentTextColor)
        assertEquals(89, Color.alpha(pronunciation.currentTextColor))
        NativeLineLyricsPresentation.clear(root)
    }

    @Test fun nativeHighlightAndFadeValuesCannotChangeAuxiliaryBrightness() {
        apply()
        for (alpha in listOf(0.35f, 0.55f, 1f, 0.8f, 0.35f)) {
            assertEquals(1f, NativeLyricsPronunciationSubtitle.protectedAlpha(pronunciation, alpha), 0f)
            assertEquals(1f, NativeLyricsPronunciationSubtitle.protectedAlpha(translation, alpha), 0f)
            assertEquals(alpha, NativeLyricsPronunciationSubtitle.protectedAlpha(original, alpha), 0f)
            assertEquals(0x59ffffff, NativeLyricsPronunciationSubtitle.protectedColor(pronunciation, Color.WHITE))
        }
        NativeLineLyricsPresentation.clear(root)
    }

    @Test fun hiddenPronunciationAndRepeatedBindingKeepSameChain() {
        pronunciation.visibility = View.GONE
        apply(); apply()
        assertEquals(View.GONE, pronunciation.visibility)
        assertEquals(pronunciation.id, params(original).topToBottom)
        pronunciation.visibility = View.VISIBLE
        apply()
        assertEquals(0, params(pronunciation).topToTop)
        NativeLineLyricsPresentation.clear(root)
    }

    @Test fun recycledNativeRowRestoresAnchorsAndReleasesBrightnessGuard() {
        apply(); apply()
        NativeLineLyricsPresentation.clear(root)
        NativeLineLyricsPresentation.clear(root)
        assertEquals(0, params(original).topToTop)
        assertEquals(-1, params(original).topToBottom)
        assertEquals(pronunciation.id, params(original).bottomToTop)
        assertEquals(original.id, params(pronunciation).topToBottom)
        assertEquals(pronunciation.id, params(translation).topToBottom)
        assertEquals(0.35f, NativeLyricsPronunciationSubtitle.protectedAlpha(pronunciation, 0.35f), 0f)
        assertEquals(Color.WHITE, NativeLyricsPronunciationSubtitle.protectedColor(translation, Color.WHITE))
    }
}
