package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

/** Wrapped lyric rendering correction controlled by lyric effects optimization. */
internal class LyricWrapSweepFeature : FeatureHook {
    override val key = ModuleConstants.FEATURE_LYRIC_WRAP_SWEEP
    override fun install(context: HookContext): FeatureInstallResult {
        if (!context.config.settings().lyricEffectsOptimizationEnabled) {
            return FeatureInstallResult.disabled("歌词效果优化已关闭")
        }
        return context.target.lyricWrapSweep.install().toFeatureInstallResult()
    }
}
