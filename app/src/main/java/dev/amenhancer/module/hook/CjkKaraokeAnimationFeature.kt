package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

/**
 * Installs the narrow Apple Music 6.5.2 karaoke animation adaptation.
 *
 * The target adapter owns all version and symbol checks; unsupported hosts
 * therefore report a degraded health result without touching existing lyric
 * layout or blur paths.
 */
internal class CjkKaraokeAnimationFeature : FeatureHook {
    override val key: String = ModuleConstants.FEATURE_CJK_KARAOKE_ANIMATION

    override fun install(context: HookContext): FeatureInstallResult {
        return context.target.cjkKaraokeAnimation.install().toFeatureInstallResult()
    }
}
