package dev.amenhancer.module.hook

import android.graphics.drawable.Drawable

enum class NavigationItemKind { TAB, DRAWER }

/** IDs are native destination IDs; no model, Compose type, or reflective member escapes. */
data class NavigationItem(
    val id: Int,
    val label: String,
    val icon: Drawable?,
    val enabled: Boolean,
    val kind: NavigationItemKind = NavigationItemKind.TAB,
    /**
     * Semantic search destination, resolved by each page family from its own evidence
     * (the Fragment enum kind name, or the legacy menu resource entry name). It is only
     * ever a hint: an unresolved or ambiguous menu reports false and the bar keeps its
     * single capsule instead of detaching a control it cannot identify.
     */
    val isSearch: Boolean = false,
)

data class NavigationSnapshot(
    val items: List<NavigationItem>,
    val selectedId: Int?,
    val placement: NavigationPlacement,
    val revision: Long,
    val actionsReady: Boolean,
) {
    val tabs: List<NavigationItem> get() = items.filter { it.kind == NavigationItemKind.TAB }
    val renderable: Boolean get() = actionsReady && tabs.size >= 2 &&
        tabs.map { it.id }.distinct().size == tabs.size && tabs.any { it.id == selectedId }

    /**
     * The detachable search control, or null when the bar must stay one capsule.
     *
     * Only the **last** tab qualifies: the collapsed bar mirrors the expanded order (the
     * remaining tabs on the left, search pinned to the right edge), so detaching a search tab
     * from the middle would silently reorder the visible destinations. A host that ever moves
     * search elsewhere therefore degrades to the single-capsule form rather than drawing a bar
     * that disagrees with the native menu.
     *
     * Detaching also has to leave a real tab strip behind: the capsule needs at least two cells
     * for the reference drag animation to normalize against, so a two-tab menu keeps its
     * single capsule.
     */
    val searchTab: NavigationItem?
        get() = tabs.lastOrNull()?.takeIf { it.isSearch && tabs.size > 2 }
}

/** Selection is confirmed from native state; reselection goes through the same native callback. */
interface NavigationPort {
    fun snapshot(): NavigationSnapshot
    fun select(id: Int): NavigationSnapshot
    /** Fixed sidebar action is separate from the selectable tab/lens region. */
    fun openDrawer(): Boolean = false
    fun observe(observer: (NavigationSnapshot) -> Unit): HostSubscription
}
