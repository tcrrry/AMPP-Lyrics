package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

/**
 * The host capability preserves native dynamic covers on 7.0. Legacy 6.5 hosts retain
 * their tablet-landscape URL suppression, static preview and separate Music Video path.
 */
internal class EditorialVideoFeature : FeatureHook {
    override val key: String = ModuleConstants.FEATURE_EDITORIAL_VIDEO

    override fun install(context: HookContext): FeatureInstallResult {
        if (!context.config.settings().dualPaneEnabled) {
            return FeatureInstallResult.disabled()
        }
        return context.target.editorialVideo.install().toFeatureInstallResult()
    }
}
