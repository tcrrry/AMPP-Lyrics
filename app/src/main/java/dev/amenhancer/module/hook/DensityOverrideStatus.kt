package dev.amenhancer.module.hook
enum class AppleMusicDpiOverrideState {
    DISABLED,
    ACTIVE,
    DEGRADED,
}

data class AppleMusicDpiOverrideStatus(
    val state: AppleMusicDpiOverrideState,
    val targetDpi: Int,
    val message: String,
) {
}
