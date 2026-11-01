package dev.amenhancer.module.hook

/** Tcrrry-Lyrics-Plugin (GPL-3.0): let the fully lit edge pass the last CJK glyph. */
fun cjkTerminalGradientMargin(original: Float, feather: Float): Float =
    if (original.isFinite() && feather.isFinite() && feather > 0f && feather <= 1f) {
        maxOf(original, feather)
    } else original
