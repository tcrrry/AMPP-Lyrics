package dev.amenhancer.module.hook

/** Registers the writers-credits lyric rows on the blur feature's own resource path. */
internal object LyricCreditsRowResourceHook {
    fun install() {
        CreditsRowIdentity.layoutNames.forEach { layoutName ->
            LayoutInflationRegistry.register(layoutName) { root ->
                CreditsRowIdentity.mark(root)
            }
        }
    }
}
