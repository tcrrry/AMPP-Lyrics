package dev.amenhancer.module.hook

/** Installs the reference1606 native player through the refactored capability boundary. */
internal class FragmentDualPaneTarget(private val symbols: TargetSymbolResolver, private val targetBuild: TargetBuild) : DualPaneTarget {
    private val registration = HookRegistrationScope()
    private var installed: TargetCapabilityInstall? = null
    @Synchronized override fun install(): TargetCapabilityInstall {
        installed?.let { return it }
        val result = runCatching {
            check(targetBuild.packageName == "com.apple.android.music" && targetBuild.versionName == "7.0.0-beta" && targetBuild.versionCode == 1606L)
            val create = checkNotNull(symbols.resolve(AppleMusicSymbols.PlayerControllerCreateView).valueOrNull())
            FragmentTabletDualPaneCoordinator.install(checkNotNull(create.declaringClass.classLoader), registration)
            TargetCapabilityInstall.Active("1606 player: native SONG/QUEUE left, native lyrics right, native artwork/video animation")
        }.getOrElse { registration.close(); TargetCapabilityInstall.Degraded("1606 reference player contract failed: ${it.message}") }
        if (result is TargetCapabilityInstall.Active) registration.activate() else registration.close()
        installed = result
        return result
    }
}
