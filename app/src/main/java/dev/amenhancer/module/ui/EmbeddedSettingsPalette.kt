package dev.amenhancer.module.ui

/** Immutable colours copied from the reference SettingsColors palette. */
internal data class EmbeddedSettingsColors(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val raised: Int,
    val text: Int,
    val secondary: Int,
    val muted: Int,
    val outline: Int,
    val primary: Int,
    val selected: Int,
    val disabled: Int,
) {
    val filledAccent: Int get() = 0xFFD62447.toInt()
    val onFilledAccent: Int get() = 0xFFFFFFFF.toInt()
    val onPrimary: Int get() = if (isDark) 0xFF2B0710.toInt() else 0xFFFFFFFF.toInt()

    // Existing dialogs use this role for the panel itself, which the reference paints as surface.
    val pageBackground: Int get() = surface
    val softBackground: Int get() = raised
    val softSurface: Int get() = selected
    val primaryPressed: Int get() = primary
    val accent: Int get() = primary
    val accentPressed: Int get() = primary
    val onSurface: Int get() = text
    val onSurfaceVariant: Int get() = secondary
    val mutedText: Int get() = muted
    val disabledSurface: Int get() = disabled
    val disabledText: Int get() = muted
    val divider: Int get() = outline
    val inputUnderline: Int get() = outline
    val actionOutline: Int get() = outline
    val actionDivider: Int get() = outline
    val switchTrackOn: Int get() = primary
    val switchTrackOff: Int get() = if (isDark) outline else 0xFFB5B5BE.toInt()
    val switchThumbOff: Int get() = muted
    val controlThumb: Int get() = if (isDark) text else onPrimary

    /** Icon factories accept the original light token (or a caller's custom colour). */
    fun iconColor(original: Int): Int = when (original) {
        EmbeddedSettingsPalette.Light.background -> background
        EmbeddedSettingsPalette.Light.surface -> surface
        EmbeddedSettingsPalette.Light.raised -> raised
        EmbeddedSettingsPalette.Light.text -> text
        EmbeddedSettingsPalette.Light.secondary -> secondary
        EmbeddedSettingsPalette.Light.muted -> muted
        EmbeddedSettingsPalette.Light.outline -> outline
        EmbeddedSettingsPalette.Light.primary -> primary
        EmbeddedSettingsPalette.Light.selected -> selected
        EmbeddedSettingsPalette.Light.disabled -> disabled
        EmbeddedSettingsPalette.Light.filledAccent -> filledAccent
        else -> original
    }
}

internal object EmbeddedSettingsPalette {
    val Light = EmbeddedSettingsColors(
        isDark = false,
        background = 0xFFF2F2F7.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        raised = 0xFFF5F5F8.toInt(),
        text = 0xFF1C1C1E.toInt(),
        secondary = 0xFF55555D.toInt(),
        muted = 0xFF696971.toInt(),
        outline = 0xFFD1D1D6.toInt(),
        primary = 0xFFC91D40.toInt(),
        selected = 0xFFFCE8EC.toInt(),
        disabled = 0xFFE4E4E9.toInt(),
    )

    val Dark = EmbeddedSettingsColors(
        isDark = true,
        background = 0xFF0B0B0C.toInt(),
        surface = 0xFF1C1C1E.toInt(),
        raised = 0xFF2C2C2E.toInt(),
        text = 0xFFF5F5F7.toInt(),
        secondary = 0xFFB7B7BF.toInt(),
        muted = 0xFF9999A2.toInt(),
        outline = 0xFF444448.toInt(),
        primary = 0xFFFF6B83.toInt(),
        selected = 0xFF3B2029.toInt(),
        disabled = 0xFF353538.toInt(),
    )

    /** Only the host Activity's effective configuration should be passed here. */
    fun forUiMode(uiMode: Int): EmbeddedSettingsColors =
        if (uiMode and 0x30 == 0x20) Dark else Light
}

internal fun embeddedSvgColor(icon: EmbeddedSvgIcon, colors: EmbeddedSettingsColors): Int =
    when (icon) {
        EmbeddedSvgIcon.Back, EmbeddedSvgIcon.RestoreDefault -> colors.primary
        else -> colors.accent
    }
