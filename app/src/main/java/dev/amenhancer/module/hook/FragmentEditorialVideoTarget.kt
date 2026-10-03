package dev.amenhancer.module.hook

/** 7.0 owns its video cover renderer and availability decisions, including the dual-pane host. */
internal object FragmentEditorialVideoTarget : EditorialVideoTarget {
    override fun install(): TargetCapabilityInstall = TargetCapabilityInstall.Active(
        "Preserving native 7.0 dynamic artwork; no tablet Editorial Video URL suppression installed",
    )
}

internal fun editorialVideoTargetForFamily(family: String, legacy: () -> EditorialVideoTarget): EditorialVideoTarget =
    when (family) {
        "fragment-content" -> FragmentEditorialVideoTarget
        "legacy-activity" -> legacy()
        else -> error("Unverified editorial host family: $family")
    }
