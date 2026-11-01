package dev.amenhancer.module.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedSettingsPaletteTest {
    @Test
    fun `host night flag selects dark regardless of other configuration flags`() {
        assertSame(EmbeddedSettingsPalette.Dark, EmbeddedSettingsPalette.forUiMode(0x21))
        assertSame(EmbeddedSettingsPalette.Dark, EmbeddedSettingsPalette.forUiMode(0xA4))
        assertSame(EmbeddedSettingsPalette.Light, EmbeddedSettingsPalette.forUiMode(0x11))
        assertSame(EmbeddedSettingsPalette.Light, EmbeddedSettingsPalette.forUiMode(0))
    }

    @Test
    fun `dark surfaces use layered panels with visible card and action layers`() {
        val colors = EmbeddedSettingsPalette.Dark
        assertEquals(0xFF1C1C1E.toInt(), colors.pageBackground)
        assertEquals(0xFF1C1C1E.toInt(), colors.surface)
        assertEquals(0xFF3B2029.toInt(), colors.softSurface)
        assertTrue(luminance(colors.background) < luminance(colors.surface))
        assertTrue(luminance(colors.softSurface) > luminance(colors.surface))
    }

    @Test
    fun `dark text and actions remain readable on their actual backgrounds`() {
        val colors = EmbeddedSettingsPalette.Dark
        listOf(colors.pageBackground, colors.surface, colors.softSurface).forEach { background ->
            listOf(colors.onSurface, colors.onSurfaceVariant, colors.mutedText, colors.primary, colors.accent).forEach { text ->
                assertTrue("text contrast on ${background.toUInt().toString(16)}", contrast(text, background) >= 4.5)
            }
        }
        assertTrue(contrast(colors.onPrimary, colors.primary) >= 4.5)
    }

    @Test
    fun `functional SVG roles and fallback icons follow palette without changing custom colours`() {
        val colors = EmbeddedSettingsPalette.Dark
        EmbeddedSvgIcon.values().forEach { icon ->
            val expected = if (icon == EmbeddedSvgIcon.Back || icon == EmbeddedSvgIcon.RestoreDefault) colors.primary else colors.accent
            assertEquals(expected, embeddedSvgColor(icon, colors))
        }
        assertEquals(colors.accent, colors.iconColor(EmbeddedSettingsPalette.Light.accent))
        assertEquals(colors.primary, colors.iconColor(EmbeddedSettingsPalette.Light.primary))
        assertEquals(0xFF123456.toInt(), colors.iconColor(0xFF123456.toInt()))
        assertEquals(EmbeddedSettingsPalette.Light.accent, EmbeddedSettingsPalette.Light.iconColor(EmbeddedSettingsPalette.Light.accent))
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }

    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}
