package io.github.proify.lyricon.amprovider.xposed

import dev.amenhancer.module.hook.AppleMusicProfiles
import dev.amenhancer.module.hook.ProfileMethodContract
import dev.amenhancer.module.hook.TargetBuild
import java.lang.reflect.Method

/** New-build lifecycle descriptors are authoritative; absent descriptors retain the legacy path. */
internal object NativeMetadataLifecycleContract {
    private const val RESUME = "metadata-fragment-resume-method"
    private const val PAUSE = "metadata-fragment-pause-method"

    fun resolve(version: AppleMusicVersion, loadClass: (String) -> Class<*>): Pair<Method, Method>? {
        val name = version.versionName ?: return null
        val code = version.versionCode ?: return null
        val contracts = AppleMusicProfiles.match(
            TargetBuild("com.apple.android.music", name, code),
        )?.methodContracts.orEmpty()
        return resolve(contracts, loadClass)
    }

    internal fun resolve(
        contracts: Map<String, ProfileMethodContract>,
        loadClass: (String) -> Class<*>,
    ): Pair<Method, Method>? {
        if (RESUME !in contracts && PAUSE !in contracts) return null
        val resume = checkNotNull(contracts[RESUME]) { "Metadata Fragment resume descriptor missing" }
        val pause = checkNotNull(contracts[PAUSE]) { "Metadata Fragment pause descriptor missing" }
        check(resume.owner == pause.owner) { "Metadata Fragment lifecycle owners differ" }
        val owner = loadClass(resume.owner)
        fun method(contract: ProfileMethodContract): Method {
            check(contract.parameters.isEmpty() && contract.returns == "void" && !contract.isStatic) {
                "Metadata Fragment lifecycle descriptor is invalid"
            }
            return owner.getDeclaredMethod(contract.name).also {
                check(contract.matches(it)) { "Metadata Fragment lifecycle descriptor is stale" }
                it.isAccessible = true
            }
        }
        return method(resume) to method(pause)
    }
}
