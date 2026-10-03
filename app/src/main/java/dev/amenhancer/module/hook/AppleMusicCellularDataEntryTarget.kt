/*
 * Adapted from HyperLyrics-Enhanced AppleCellularDataSettingsHooks.
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.amenhancer.module.hook

import dev.amenhancer.module.ModuleConstants
import io.github.proify.lyricon.amprovider.xposed.hooks.AppleHookRegistrar
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Only the verified settings gate is scoped; availability follows the current toggle globally. */
internal class AppleMusicCellularDataEntryTarget(
    private val symbols: TargetSymbolResolver,
    private val build: TargetBuild,
    private val enabled: () -> Boolean,
    private val hooksFactory: () -> CellularDataEntryHookInstaller? = {
        ModernXposedRuntime.activeModule()?.let { module ->
            RegistrarCellularDataEntryHookInstaller(AppleHookRegistrar(module))
        }
    },
) : CellularDataEntryTarget {
    private val scope = CellularDataSettingsScope()
    private val ready = HookRegistrationScope()
    private var installedResult: TargetCapabilityInstall? = null

    @Synchronized
    override fun install(): TargetCapabilityInstall {
        installedResult?.let { return it }
        return installOnce().also { installedResult = it }
    }

    private fun installOnce(): TargetCapabilityInstall {
        if (!supports(build)) {
            return TargetCapabilityInstall.Unsupported(
                "Cellular data entry requires verified Apple Music 6.5.2 (1586) or 6.5.3 (1599); " +
                    "found ${build.displayName}",
            )
        }

        // Resolve every target before registering any callback. Never scan for a generic boolean helper.
        val buildResolution = symbols.resolve(AppleMusicSymbols.SettingsDataCategoryBuild)
        val simResolution = symbols.resolve(AppleMusicSymbols.SettingsCellularSimCheck)
        val availabilityResolution = symbols.resolve(AppleMusicSymbols.CellularAvailability)
        val buildMethod = buildResolution.valueOrNull()
        val simCheck = simResolution.valueOrNull()
        val availability = availabilityResolution.valueOrNull()
        if (buildMethod == null || simCheck == null || availability == null) {
            return TargetCapabilityInstall.Degraded(
                listOf(buildResolution, simResolution, availabilityResolution)
                    .joinToString("; ") { it.summary },
            )
        }

        return runCatching {
            val hooks = hooksFactory() ?: return TargetCapabilityInstall.Degraded(
                "Modern Xposed module was not attached for cellular data entry",
            )
            hooks.overrideResult(simCheck) { original ->
                if (scope.consume()) true else original
            }
            hooks.withScope(
                buildMethod,
                enter = { scope.enter(enabledNow()) },
                exit = scope::exit,
            )
            hooks.overrideResult(availability) { original ->
                if (enabledNow()) true else original
            }
            // Partial registration remains dormant on failure, even if the user toggle is already on.
            ready.activate()
            TargetCapabilityInstall.Active(
                "Installed cellular data entry: ${buildMethod.toGenericString()}; " +
                    "${simCheck.toGenericString()}; ${availability.toGenericString()}",
            )
        }.getOrElse { error ->
            ready.close()
            TargetCapabilityInstall.Degraded(
                "Cellular data entry registration failed; callbacks remain dormant: " +
                    "${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}",
            )
        }
    }

    // Still push a disabled frame on a read failure, so a nested rebuild cannot borrow its parent's allowance.
    private fun enabledNow(): Boolean = ready.isActive && runCatching(enabled).getOrDefault(false)

    companion object {
        fun supports(build: TargetBuild): Boolean =
            dev.amenhancer.host.applemusic.AppleMusicHostProfiles.supportsCellular(
                build.packageName, build.versionName, build.versionCode,
            )
    }
}

/** One allowance per synchronous settings rebuild, including nested disabled frames. */
internal class CellularDataSettingsScope {
    private val frames = ThreadLocal<ArrayDeque<Boolean>>()

    fun enter(enabled: Boolean) {
        val stack = frames.get() ?: ArrayDeque<Boolean>().also(frames::set)
        stack.addLast(enabled)
    }

    fun consume(): Boolean {
        val stack = frames.get() ?: return false
        if (stack.isEmpty() || !stack.last()) return false
        stack.removeLast()
        stack.addLast(false)
        return true
    }

    fun exit() {
        val stack = frames.get() ?: return
        stack.removeLastOrNull()
        if (stack.isEmpty()) frames.remove()
    }
}

/** A small registration seam that permits exercising real target callbacks without a device. */
internal interface CellularDataEntryHookInstaller {
    fun overrideResult(method: Method, override: (Any?) -> Any?)
    fun withScope(method: Method, enter: () -> Unit, exit: () -> Unit)
}

private class RegistrarCellularDataEntryHookInstaller(
    private val registrar: AppleHookRegistrar,
) : CellularDataEntryHookInstaller {
    override fun overrideResult(method: Method, override: (Any?) -> Any?) {
        registrar.withModule(ModuleConstants.FEATURE_CELLULAR_DATA_ENTRY) {
            registrar.installResultOverrideHook(method) { _, original -> override(original) }
        }
    }

    override fun withScope(method: Method, enter: () -> Unit, exit: () -> Unit) {
        registrar.withModule(ModuleConstants.FEATURE_CELLULAR_DATA_ENTRY) {
            registrar.installScopedHook(
                method,
                enter = { enter(); true },
                after = { _, _ -> },
                exit = exit,
            )
        }
    }
}
