package dev.amenhancer.module.hook

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PronunciationHeaderLayoutTest {
    // Host ConstraintLayout uses these public parameters; no host dependency is bundled.
    class HostParams(width: Int, height: Int) : ViewGroup.MarginLayoutParams(width, height) {
        constructor(source: ViewGroup.LayoutParams) : this(source.width, source.height)
        @JvmField var topToTop = -1
        @JvmField var topToBottom = -1
        @JvmField var bottomToTop = -1
        @JvmField var startToStart = -1
        @JvmField var endToEnd = -1
    }
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private class HostLayout(activity: Activity) : ViewGroup(activity) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
        override fun checkLayoutParams(params: LayoutParams) = params is HostParams
        override fun generateDefaultLayoutParams(): LayoutParams = HostParams(-1, -2)
    }
    private fun row(): Pair<ViewGroup, FrameLayout> {
        val root = HostLayout(activity)
        val words = FrameLayout(activity).apply { id = View.generateViewId(); setPaddingRelative(12, 8, 14, 0) }
        root.addView(words, HostParams(0, -2).apply { topToTop = 0; topMargin = 7 })
        return root to words
    }

    @Test fun headerAnchorsAboveOriginalWithoutChangingWordIndicesOrStyle() {
        val (root, words) = row()
        val first = TextView(activity)
        val second = TextView(activity)
        val pronunciation = TextView(activity).apply { text = "a no ko ni"; textSize = 17f }
        words.addView(first); words.addView(second); words.addView(pronunciation)
        val originalSize = pronunciation.textSize
        try {
            PronunciationHeaderLayout.moveAbove(words, pronunciation)
            assertSame(root, pronunciation.parent)
            assertSame(first, words.getChildAt(0)); assertSame(second, words.getChildAt(1))
            assertEquals(2, words.childCount)
            val header = pronunciation.layoutParams as HostParams
            val main = words.layoutParams as HostParams
            assertEquals(0, header.topToTop); assertEquals(pronunciation.id, main.topToBottom)
            assertEquals(words.id, header.bottomToTop)
            assertEquals(-1, main.topToTop); assertEquals(words.id, header.startToStart)
            assertEquals(words.id, header.endToEnd); assertEquals(12, header.marginStart)
            assertEquals(14, header.marginEnd); assertEquals(7, header.topMargin)
            assertEquals(originalSize, pronunciation.textSize, 0f)
        } finally { PronunciationHeaderLayout.clear(words) }
    }

    @Test fun rowReuseRestoresOriginalConstraintsAndRemovesOldPronunciation() {
        val (root, words) = row()
        val pronunciation = TextView(activity)
        words.addView(pronunciation)
        PronunciationHeaderLayout.moveAbove(words, pronunciation)
        PronunciationHeaderLayout.clearRow(root)
        val main = words.layoutParams as HostParams
        assertEquals(0, main.topToTop); assertEquals(-1, main.topToBottom)
        assertEquals(7, main.topMargin); assertNull(pronunciation.parent)
        assertEquals(1, root.childCount)
        PronunciationHeaderLayout.clearRow(root)
        assertEquals(1, root.childCount)
    }

    @Test fun backgroundPronunciationKeepsItsOwnOriginalAnchor() {
        val (root, words) = row()
        val background = FrameLayout(activity).apply { id = View.generateViewId() }
        root.addView(background, HostParams(0, -2).apply { topToBottom = words.id })
        (words.layoutParams as HostParams).bottomToTop = background.id
        val pronunciation = TextView(activity)
        background.addView(pronunciation)
        try {
            PronunciationHeaderLayout.moveAbove(background, pronunciation)
            assertEquals(words.id, (pronunciation.layoutParams as HostParams).topToBottom)
            assertEquals(pronunciation.id, (background.layoutParams as HostParams).topToBottom)
            assertEquals(pronunciation.id, (words.layoutParams as HostParams).bottomToTop)
            assertEquals(background.id, (pronunciation.layoutParams as HostParams).bottomToTop)
        } finally { PronunciationHeaderLayout.clearRow(root) }
        assertEquals(words.id, (background.layoutParams as HostParams).topToBottom)
        assertEquals(background.id, (words.layoutParams as HostParams).bottomToTop)
    }

    // 1606 A.U accepts D9/b9 auxiliary bindings; M9/O9 word-pronunciation
    // bindings are rejected even though all four roots are CustomTextView.
    private val nativeLayouts = mapOf(
        "lyrics_translation_line_karaoke" to 0x7f0d0422,
        "lyrics_bg_translation_line_karaoke" to 0x7f0d0414,
        "lyrics_word_pronunciation" to 0x7f0d0425,
        "lyrics_word_pronunciation_bg" to 0x7f0d0426,
    )

    @Test fun pronunciationUsesAcceptedNativeSubtitleBindingForBothVocalTracks() {
        for (background in listOf(false, true)) {
            val (root, words) = row()
            val original = TextView(activity).apply { text = "君の声" }
            words.addView(original)
            try {
                NativeLyricsPronunciationSubtitle.renderHeader(words, "ki mi no ko e", background,
                    resolveLayout = { nativeLayouts[it] ?: 0 }) { text, layout ->
                    val rendered = TextView(activity).apply { this.text = text; textSize = 17f }
                    words.addView(rendered)
                    // Like Apple's U, fail after inflation for an incompatible binding.
                    if (layout !in setOf(0x7f0d0422, 0x7f0d0414)) error("unrecognized layout binding")
                }
                assertEquals(1, words.childCount)
                assertSame(original, words.getChildAt(0))
                val header = root.getChildAt(1) as TextView
                assertEquals("ki mi no ko e", header.text.toString())
                assertEquals(View.VISIBLE, header.visibility)
                assertEquals(header.id, (words.layoutParams as HostParams).topToBottom)
            } finally { PronunciationHeaderLayout.clearRow(root) }
            assertEquals(1, root.childCount)
            assertEquals("君の声", original.text.toString())
        }
    }

    @Test fun rejectedNativeBindingDoesNotLeaveEmptyInflatedViewInWordContainer() {
        val (root, words) = row()
        val original = TextView(activity)
        words.addView(original)
        try {
            NativeLyricsPronunciationSubtitle.renderHeader(words, "kimi", false,
                resolveLayout = { 0x7f0d0425 }) { _, _ ->
                words.addView(TextView(activity))
                error("unrecognized layout binding: M9")
            }
            fail("Expected native binding failure")
        } catch (expected: IllegalStateException) {
            assertEquals("unrecognized layout binding: M9", expected.message)
        }
        assertEquals(1, words.childCount)
        assertSame(original, words.getChildAt(0))
        assertEquals(1, root.childCount)
        assertEquals(0, (words.layoutParams as HostParams).topToTop)
    }

    @Test fun unsupportedNativeLayoutsRemainUntouched() {
        val root = FrameLayout(activity)
        val words = FrameLayout(activity)
        root.addView(words)
        assertFalse(PronunciationHeaderLayout.supports(words))
        assertEquals(1, root.childCount)
    }
}
