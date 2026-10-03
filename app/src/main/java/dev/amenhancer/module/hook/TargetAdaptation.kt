package dev.amenhancer.module.hook



internal fun TargetCapabilityInstall.toFeatureInstallResult(): FeatureInstallResult = when (this) {
    is TargetCapabilityInstall.Active -> FeatureInstallResult.active(message)
    is TargetCapabilityInstall.Degraded -> FeatureInstallResult.degraded(message)
    is TargetCapabilityInstall.Unsupported -> FeatureInstallResult.unsupported(message)
}

/**
 * The complete target-specific seam used by feature hooks.
 *
 * Each feature receives only its own capability, while symbol discovery and
 * reflective hook installation remain private to the Apple Music adapters.
 */
data class TargetAdaptation(
    val identity: String,
    val build: TargetBuild = TargetBuild.UNKNOWN,
    val currentSong: CurrentSongIdentityCache = CurrentSongIdentityCache(),
    val dualPane: DualPaneTarget,
    val editorialVideo: EditorialVideoTarget,
    val bidirectionalLyricBlur: BidirectionalLyricBlurTarget,
    val cjkKaraokeAnimation: CjkKaraokeAnimationTarget = CjkKaraokeAnimationTarget {
        TargetCapabilityInstall.Degraded("CJK karaoke animation target was not configured")
    },
    val lyricsTypeface: LyricsTypefaceTarget = LyricsTypefaceTarget {
        TargetCapabilityInstall.Degraded("Lyrics typeface target was not configured")
    },
    val customLyrics: CustomLyricsTarget = CustomLyricsTarget {
        TargetCapabilityInstall.Degraded("Custom lyrics target was not configured")
    },
    val currentSongIdentity: CurrentSongIdentityTarget = CurrentSongIdentityTarget {
        TargetCapabilityInstall.Degraded("Current song identity target was not configured")
    },
    /** Retained only for compatibility; the former global target is never installed. */
    val catalogLanguage: CatalogLanguageTarget = CatalogLanguageTarget {
        TargetCapabilityInstall.Degraded("Catalog language target is intentionally disabled")
    },
    val hleMetadata: HleMetadataTarget = HleMetadataTarget {
        TargetCapabilityInstall.Degraded("HLE metadata target was not configured")
    },
    val cellularDataEntry: CellularDataEntryTarget = CellularDataEntryTarget {
        TargetCapabilityInstall.Degraded("Cellular data entry target was not configured")
    },
) {
}

fun interface DualPaneTarget {
    fun install(): TargetCapabilityInstall
}

fun interface EditorialVideoTarget {
    fun install(): TargetCapabilityInstall
}

fun interface BidirectionalLyricBlurTarget {
    fun install(): TargetCapabilityInstall
}

fun interface CjkKaraokeAnimationTarget {
    fun install(): TargetCapabilityInstall
}

fun interface LyricsTypefaceTarget {
    fun install(): TargetCapabilityInstall
}

fun interface CustomLyricsTarget {
    fun install(): TargetCapabilityInstall
}

fun interface CurrentSongIdentityTarget {
    fun install(): TargetCapabilityInstall
}

fun interface HleMetadataTarget {
    fun install(): TargetCapabilityInstall
}

fun interface CellularDataEntryTarget {
    fun install(): TargetCapabilityInstall
}

sealed interface TargetCapabilityInstall {
    val message: String

    data class Active(override val message: String) : TargetCapabilityInstall {
        init {
            require(message.isNotBlank()) { "Target capability diagnostic must not be blank" }
        }
    }

    data class Degraded(override val message: String) : TargetCapabilityInstall {
        init {
            require(message.isNotBlank()) { "Target capability diagnostic must not be blank" }
        }
    }

    data class Unsupported(override val message: String) : TargetCapabilityInstall {
        init {
            require(message.isNotBlank()) { "Target capability diagnostic must not be blank" }
        }
    }
}
