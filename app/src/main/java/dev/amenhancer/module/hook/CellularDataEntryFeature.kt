package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants

/** Install dormant hooks too, so changing the toggle does not require a new process. */
internal class CellularDataEntryFeature : FeatureHook {
    override val key = ModuleConstants.FEATURE_CELLULAR_DATA_ENTRY

    override fun install(context: HookContext): FeatureInstallResult {
        val result = context.target.cellularDataEntry.install()
        return if (result is TargetCapabilityInstall.Active &&
            !context.config.settings().forceCellularDataEntryEnabled
        ) {
            FeatureInstallResult.disabled("Hooks installed; toggle is off. ${result.message}")
        } else {
            result.toFeatureInstallResult()
        }
    }
}
