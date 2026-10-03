package dev.amenhancer.module.hook

import android.view.Menu
import android.view.View
import android.app.Activity
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** All model and action objects stay inside the host adapter. No host Compose ownership crosses it. */
internal class FragmentChromeNavigation(
    private val owner: Any,
    private val view: View,
    private val placement: NavigationPlacement,
    private val contract: FragmentChromeContract,
    private val callbacks: IdentityHashMap<Any, Any>,
) : NavigationPort, AutoCloseable {
    private val listeners = LinkedHashSet<(NavigationSnapshot) -> Unit>()
    private val vm = contract.viewModel.invoke(owner)!!
    private val selected = FragmentChromeContract.method(vm.javaClass, "getSelectedTab")
    private val live = FragmentChromeContract.method(vm.javaClass, "getGetBottomTabsLiveData").invoke(vm)!!
    private val value = FragmentChromeContract.method(live.javaClass, "getValue")
    private val menuMethod: Method? = if (placement == NavigationPlacement.BOTTOM)
        FragmentChromeContract.method(view.javaClass, "getMenu") else null
    private val selectedIdMethod: Method? = if (menuMethod != null)
        FragmentChromeContract.method(view.javaClass, "getSelectedItemId") else null
    private val selectMethod: Method? = if (menuMethod != null)
        FragmentChromeContract.method(view.javaClass, "setSelectedItemId", java.lang.Integer.TYPE) else null
    private var key: List<Any?> = emptyList()
    private var revision = 0L
    private var models = emptyMap<Int, Any>()
    private var last: NavigationSnapshot? = null
    private var iconConfiguration = 0
    private val icons = HashMap<Int, android.graphics.drawable.Drawable?>()
    private val activity = FragmentChromeContract.method(owner.javaClass, "getActivity").invoke(owner) as Activity
    private val drawerView = activity.findViewById<View>(view.resources.getIdentifier(
        contract.resources.getString("drawerNavigation"), "id", activity.packageName))
    private val drawerMenu = drawerView?.let { FragmentChromeContract.method(it.javaClass, "getMenu").invoke(it) as? Menu }
    private val libraryTitleLive = contract.libraryTitle.invoke(contract.libraryModel.invoke(owner))
    private val libraryTitleValue = libraryTitleLive?.let { FragmentChromeContract.method(it.javaClass, "getValue") }
    private var closed = false

    override fun snapshot(): NavigationSnapshot {
        if (closed) return NavigationSnapshot(emptyList(), null, placement, revision, false)
        val menu = menuMethod?.invoke(view) as? Menu
        val items: List<NavigationItem>
        val selectedId: Int?
        val ready: Boolean
        if (menu != null) {
            items = (0 until menu.size()).map(menu::getItem).filter { it.isVisible }.map {
                NavigationItem(it.itemId, it.title?.toString().orEmpty(), it.icon, it.isEnabled)
            }
            selectedId = selectedIdMethod!!.invoke(view) as Int
            ready = true
        } else {
            val configuration = view.resources.configuration.hashCode()
            if (configuration != iconConfiguration) {
                iconConfiguration = configuration
                icons.clear()
            }
            val nativeModels = (value.invoke(live) as? List<*>)?.filterNotNull().orEmpty()
            models = nativeModels.associateBy { contract.kindId.invoke(contract.modelKind.get(it)) as Int }
            items = nativeModels.mapNotNull { model ->
                val kind = contract.modelKind.get(model)!!
                val kindName = (kind as Enum<*>).name
                val id = contract.kindId.invoke(kind) as Int
                val menuItem = drawerMenu?.findItem(id) ?: return@mapNotNull null
                if (!menuItem.isVisible) return@mapNotNull null
                val iconId = contract.kindIcon.invoke(kind) as Int
                val label = if (kindName == "LIBRARY") libraryTitleValue?.invoke(libraryTitleLive) as? String else null
                NavigationItem(id, label ?: contract.kindLabel.invoke(kind) as String,
                    if (kindName == "SEARCH" && iconId != 0) icons.getOrPut(iconId) { view.context.getDrawable(iconId) } else null,
                    menuItem?.isEnabled ?: true)
            }
            selectedId = selected.invoke(vm)?.let { contract.kindId.invoke(it) as Int }
            // The callback may be captured for an equivalent model object from the same live menu.
            ready = items.size > 1 && contract.nativeMenuListener.get(owner) != null
        }
        val nextKey = items.flatMap { listOf(it.id, it.label, it.enabled, it.icon?.constantState) } +
            listOf(selectedId, ready, view.resources.configuration.hashCode())
        if (key != nextKey) { key = nextKey; revision++ }
        return NavigationSnapshot(items, selectedId, placement, revision, ready)
    }

    private fun callbackFor(model: Any): Any? = callbacks[model] ?: callbacks.entries.firstOrNull {
        contract.modelKind.get(it.key) == contract.modelKind.get(model)
    }?.value

    override fun select(id: Int): NavigationSnapshot {
        val before = snapshot()
        if (before.tabs.none { it.id == id && it.enabled }) return before
        if (selectMethod != null) selectMethod.invoke(view, id)
        else drawerMenu?.findItem(id)?.let { item ->
            contract.nativeMenuListener.get(owner)?.let { contract.selectMenuItem.invoke(it, item) }
        }
        return snapshot().also { publish(it) }
    }

    override fun openDrawer(): Boolean {
        if (closed || placement != NavigationPlacement.TOP) return false
        val drawer = contract.drawerOf.invoke(activity) ?: return false
        contract.drawerOpen.invoke(drawer)
        return true
    }

    fun refresh() { if (!closed) publish(snapshot()) }
    private fun publish(snapshot: NavigationSnapshot) {
        if (snapshot == last) return
        last = snapshot
        listeners.toList().forEach { it(snapshot) }
    }
    override fun observe(observer: (NavigationSnapshot) -> Unit): HostSubscription {
        if (closed) return HostSubscription {}
        listeners += observer
        observer(snapshot())
        return HostSubscription { listeners -= observer }
    }
    override fun close() { closed = true; listeners.clear(); models = emptyMap(); icons.clear() }
}
