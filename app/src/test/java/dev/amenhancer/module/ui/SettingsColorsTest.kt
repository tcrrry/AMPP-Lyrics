package dev.amenhancer.module.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class SettingsColorsTest {
    @Test fun followingHostSupportsBothAppearances() {
        assertSame(SettingsColors.Light, SettingsColors.resolve(SettingsAppearance.FOLLOW, false))
        assertSame(SettingsColors.Dark, SettingsColors.resolve(SettingsAppearance.FOLLOW, true))
    }
    @Test fun manualChoiceOverridesHostUntilFollowingIsSelected() {
        assertSame(SettingsColors.Dark, SettingsColors.resolve(SettingsAppearance.DARK, false))
        assertSame(SettingsColors.Light, SettingsColors.resolve(SettingsAppearance.LIGHT, true))
    }
    @Test fun missingOrUnknownPreferenceFollowsHost() {
        for (value in listOf(null, "", "invalid")) assertEquals(SettingsAppearance.FOLLOW, SettingsAppearance.decode(value))
        assertEquals(SettingsAppearance.DARK, SettingsAppearance.decode("DARK"))
    }
    @Test fun bodyTextAndSecondaryLabelsRemainReadableOnBothSurfaces() {
        for (p in listOf(SettingsColors.Light, SettingsColors.Dark)) {
            for (background in listOf(p.background, p.surface, p.raised)) {
                for (foreground in listOf(p.text, p.secondary, p.muted)) {
                    assertTrue("${p.dark}: $foreground on $background", contrast(foreground, background) >= 4.5)
                }
            }
        }
    }
    @Test fun selectedLabelsAndFilledButtonsHaveReadableContrast() {
        for (p in listOf(SettingsColors.Light, SettingsColors.Dark)) {
            assertTrue(contrast(p.primary, p.selected) >= 4.5)
            assertTrue(contrast(p.primary, p.surface) >= 4.5)
            assertTrue(contrast(p.onPrimary, p.filledAccent) >= 4.5)
        }
    }
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((color ushr shift) and 255) / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
