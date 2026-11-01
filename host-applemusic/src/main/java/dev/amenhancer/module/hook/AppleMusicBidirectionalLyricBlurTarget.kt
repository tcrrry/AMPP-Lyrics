package dev.amenhancer.module.hook

import android.os.Build
import dev.amenhancer.host.applemusic.AppleMusicHostProfile
import dev.amenhancer.module.config.TargetConfigClient

/** Atomic registration of one exact host contract. No legacy guessed-highlight hooks run beside it. */
internal class AppleMusicBidirectionalLyricBlurTarget(
    private val profile: AppleMusicHostProfile,
    private val loader: ClassLoader,
) : BidirectionalLyricBlurTarget {
    private var registration: HookRegistrationScope? = null

    override fun install(): TargetCapabilityInstall {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return TargetCapabilityInstall.Degraded("Lyric blur requires Android 12")
        }
        if (registration?.isActive == true) return TargetCapabilityInstall.Active("Lyric blur already active; ${profile.id}")
        val scope = HookRegistrationScope()
        return try {
            val contract = AppleMusicLyricBlurContract(profile, loader)
            lateinit var runtime: AppleMusicLyricBlurRuntime
            val effects = LyricBlurEffectOwner { view -> runtime.effectChanged(view) }
            runtime = AppleMusicLyricBlurRuntime(contract, effects,
                TargetConfigClient.currentSettings().lyricBlurRadiusOffsetPx, scope)
            effects.install(scope)
            runtime.install()
            scope.activate()
            registration = scope
            TargetCapabilityInstall.Active("Lyric blur displayed-state sessions installed; ${profile.id}")
        } catch (error: Throwable) {
            scope.close()
            TargetCapabilityInstall.Degraded("Lyric blur contract/registration failed; ${profile.id}: ${error.message ?: error.javaClass.simpleName}")
        }
    }
}
