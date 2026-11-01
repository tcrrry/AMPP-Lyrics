package dev.amenhancer.module.hook

import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Modifier

/** Re-evaluates the native startup predicate for phone and tablet after queue/account restoration. */
internal class FragmentMiniStartup(
    private val owner: Any,
    private val root: ViewGroup,
    private val material: View,
    contract: FragmentChromeContract,
) : Runnable, AutoCloseable {
    private val browser = contract.browser
    private val item = contract.currentItem
    private val loggedIn = contract.accountReady
    private val show = contract.miniVisible
    private val visibilityStatic = Modifier.isStatic(show.modifiers)
    private val view = contract.view
    private var attempts = 0
    private var closed = false

    fun start() { root.post(this) }
    override fun run() {
        if (closed || view.invoke(owner) !== root || !root.isAttachedToWindow) return
        if (material.visibility == View.VISIBLE) return
        runCatching {
            val readyAccount = loggedIn.invoke(null) == true
            val currentItem = browser.invoke(owner)?.let { item.invoke(it) != null } == true
            if (readyAccount && currentItem) {
                if (visibilityStatic) show.invoke(null, owner, true) else show.invoke(owner, true)
                ModernXposedRuntime.log("Fragment mini startup: restored native visibility after account/queue ready")
                return
            }
            if (++attempts < 40) root.postDelayed(this, 250)
            else ModernXposedRuntime.log("Fragment mini startup remains native: account=$readyAccount, item=$currentItem")
        }.onFailure { ModernXposedRuntime.log("Fragment mini startup refresh failed", it) }
    }
    override fun close() { closed = true; root.removeCallbacks(this) }
}
