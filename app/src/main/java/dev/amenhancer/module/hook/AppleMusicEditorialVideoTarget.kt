package dev.amenhancer.module.hook

import android.app.Application

internal class AppleMusicEditorialVideoTarget(
    private val application: Application,
    private val symbols: TargetSymbolResolver,
) : EditorialVideoTarget {
    override fun install(): TargetCapabilityInstall {
        val resolution = symbols.resolve(AppleMusicSymbols.EditorialVideoUrlSelector)
        val selector = resolution.valueOrNull()
            ?: return TargetCapabilityInstall.Degraded(resolution.summary)

        ModernXposedRuntime.hookMethod(selector, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!TabletModeQualifier.isOfficialTabletLandscape(application)) return
                param.result = null
            }
        })
        return TargetCapabilityInstall.Active(
            "Installed tablet-landscape Editorial Video URL suppression on " +
                "${selector.declaringClass.name}.${selector.name}; ${resolution.summary}",
        )
    }
}

