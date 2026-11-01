package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

internal class StartupPagePrewarmFeature : FeatureHook {
    override val key = ModuleConstants.FEATURE_STARTUP_PAGE_PREWARM

    override fun install(context: HookContext): FeatureInstallResult {
        if (!context.config.settings().startupPagePrewarmEnabled) return FeatureInstallResult.disabled()
        return context.target.startupPagePrewarm.install().toFeatureInstallResult()
    }
}
