package dev.amenhancer.module.hook

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.module.ModuleConstants
import java.io.Serializable
import java.util.Collections
import java.util.IdentityHashMap

/** A bounded startup task; no retained global page cache, native tab changes or back-stack writes. */
internal class StartupPagePrewarmSession(
    private val owner: Any,
    val activity: Activity,
    private val root: ViewGroup,
    private val native: StartupPagePrewarmContract,
    private val navigationResource: String,
    private val enabled: () -> Boolean,
    private val onPrepared: (Any) -> Unit,
    private val onFinished: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable, View.OnAttachStateChangeListener {
    private data class Tab(val kind: Enum<*>, val title: String)
    private val hostId = resource(native.names.getString("hostResource"))
    private val navigationId = resource(navigationResource)
    private val tabletNavigation = TabletModeQualifier.isOfficialTablet(activity)
    private val menuMethod = if (tabletNavigation) checkNotNull(native.tabletMenu) else native.menu
    private val manager = checkNotNull(native.childManager.invoke(owner))
    private val deadline = SystemClock.uptimeMillis() + 10_000L
    private var closed = false
    private var tabNames: List<String>? = null
    private var waitingForCommit = false
    private var measureNext: Any? = null
    private var waitReason = "first callback"
    private var preparedCount = 0
    private val laidOut = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    private val step = Runnable {
        if (!closed) runCatching(::advance).onFailure { close(); onFailure(it) }
    }
    // The deadline must still fire if the native transaction never invokes its callback.
    private val timeout = Runnable { finish("timeout waiting for $waitReason") }

    fun start() {
        check(hostId != 0 && navigationId != 0) { "Native tab host resources are missing" }
        root.addOnAttachStateChangeListener(this)
        ModernXposedRuntime.log("Startup page prewarm: session scheduled; navigation=$navigationResource")
        root.postDelayed(timeout, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        root.post(step)
    }

    private fun resource(name: String) = root.resources.getIdentifier(name, "id", ModuleConstants.TARGET_PACKAGE)
    private fun schedule(delay: Long = 0L) { if (!closed) root.postDelayed(step, delay) }
    private fun waitFor(reason: String) { waitReason = reason; schedule(50L) }

    private fun advance() {
        if (closed || waitingForCommit) return
        if (!enabled()) { finish("setting disabled"); return }
        if (SystemClock.uptimeMillis() >= deadline) { finish("timeout waiting for $waitReason"); return }
        if (activity.isFinishing || activity.isDestroyed || native.view.invoke(owner) !== root ||
            native.stateSaved.invoke(manager) == true
        ) { finish("content unavailable or state saved"); return }
        if (!root.isAttachedToWindow || !root.isShown || native.resumed.invoke(owner) != true) {
            if (tabNames != null) finish("content hidden or paused") else waitFor("visible resumed content")
            return
        }
        val host = root.findViewById<ViewGroup>(hostId)
        // Tablet tabs are Compose; their native menu lives in the Activity's drawer.
        val navigation = if (tabletNavigation) activity.findViewById<View>(navigationId)
            else root.findViewById<View>(navigationId)
        val primary = native.primary.get(manager)
        if (host == null || host.width <= 0 || host.height <= 0 || navigation == null || primary == null) {
            waitFor(when {
                navigation == null -> "navigation view $navigationResource"
                host == null || host.width <= 0 || host.height <= 0 -> "measured tab host"
                else -> "primary navigation"
            }); return
        }
        val model = native.viewModel.invoke(owner)
        val selected = native.selectedTab.invoke(model) as? Enum<*>
        val selectedId = selected?.let { native.kindId.invoke(it) as Int }
        val menu = menuMethod.invoke(navigation) as Menu
        val tabs = (native.value.invoke(native.tabs.invoke(model)) as? List<*>)?.mapNotNull { item ->
            val kind = item?.let { native.modelKind.get(it) } as? Enum<*> ?: return@mapNotNull null
            val menuItem = menu.findItem(native.kindId.invoke(kind) as Int)
            if (menuItem?.isEnabled != true || !menuItem.isVisible) null
            else Tab(kind, native.kindLabel.invoke(kind) as String)
        }?.distinctBy { it.kind.name }
        val selectionMatches = if (tabletNavigation)
            menu.findItem(selectedId ?: View.NO_ID)?.isChecked == true
        else native.selectedId.invoke(navigation) == selectedId
        if (tabs.isNullOrEmpty() || tabs.none { it.kind == selected } ||
            native.tag.invoke(primary) != selected?.name ||
            !selectionMatches
        ) { waitFor("consistent native tab selection"); return }
        val names = tabs.map { it.kind.name }
        if (tabNames == null) tabNames = names
        // Native navigation may change the selected tab while this one-shot queue runs.
        // Read the current selection above and reuse its actual retained containers below.
        if (tabNames != names) { finish("tab list changed"); return }

        measureNext?.let { fragment ->
            if (native.hidden.invoke(fragment) == true) {
                val view = native.view.invoke(fragment) as? View
                if (view == null) { waitFor("hidden page view ${native.tag.invoke(fragment)}"); return }
                view.measure(View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(host.height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, host.width, host.height)
                laidOut.add(fragment)
                ModernXposedRuntime.log("Startup page laid out: ${native.tag.invoke(fragment)}")
            }
            measureNext = null
        }
        val next = tabs.sortedBy { if (it.kind.name == native.names.getString("priorityTab")) 0 else 1 }
            .firstOrNull {
                val existing = native.find.invoke(manager, it.kind.name)
                existing == null || (existing !in laidOut && native.hidden.invoke(existing) == true)
            }
        if (next == null) { finish("complete; available tab roots retained and laid out"); return }
        native.find.invoke(manager, next.kind.name)?.let { existing ->
            // Restored hidden roots also need a first layout; keep their actual instance and state.
            measureNext = existing
            schedule()
            return
        }
        val fragment = native.container.newInstance()
        native.arguments.invoke(fragment, Bundle().apply {
            putSerializable(native.names.getString("tabArgumentKey"), next.kind as Serializable)
            putString(native.names.getString("titleArgumentKey"), next.title)
        })
        val transaction = native.transaction.newInstance(manager)
        native.reordering.setBoolean(transaction, true)
        native.add.invoke(transaction, hostId, fragment, next.kind.name, 1)
        native.hide.invoke(transaction, fragment)
        onPrepared(fragment)
        native.onCommit.invoke(transaction, Runnable {
            waitingForCommit = false
            if (!closed) {
                preparedCount++
                measureNext = fragment
                ModernXposedRuntime.log("Startup page created: ${next.kind.name}")
                schedule()
            }
        })
        waitingForCommit = true
        waitReason = "native page transaction ${next.kind.name}"
        native.commit.invoke(transaction, false)
    }

    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = finish("content detached")
    override fun close() = finish("cancelled by lifecycle")
    private fun finish(reason: String) {
        if (closed) return
        closed = true
        root.removeCallbacks(step)
        root.removeCallbacks(timeout)
        root.removeOnAttachStateChangeListener(this)
        measureNext = null
        ModernXposedRuntime.log("Startup page prewarm: stopped; prepared=$preparedCount; laidOut=${laidOut.size}; $reason")
        laidOut.clear()
        onFinished()
    }
}
