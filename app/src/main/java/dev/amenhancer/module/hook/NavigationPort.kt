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
}

/** Selection is confirmed from native state; reselection goes through the same native callback. */
interface NavigationPort {
    fun snapshot(): NavigationSnapshot
    fun select(id: Int): NavigationSnapshot
    /** Fixed sidebar action is separate from the selectable tab/lens region. */
    fun openDrawer(): Boolean = false
    fun observe(observer: (NavigationSnapshot) -> Unit): HostSubscription
}
