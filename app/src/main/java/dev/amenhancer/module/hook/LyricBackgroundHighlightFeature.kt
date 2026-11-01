package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

/** Preserves native main-line highlighting while background lyric timing remains active. */
internal class LyricBackgroundHighlightFeature : FeatureHook {
    override val key = ModuleConstants.FEATURE_LYRIC_BACKGROUND_HIGHLIGHT
    override fun install(context: HookContext): FeatureInstallResult {
        if (!context.config.settings().lyricEffectsOptimizationEnabled) {
            return FeatureInstallResult.disabled("歌词效果优化已关闭")
        }
        return context.target.lyricBackgroundHighlight.install().toFeatureInstallResult()
    }
}
