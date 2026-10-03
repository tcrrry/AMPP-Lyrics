package dev.amenhancer.module.hook

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import dev.amenhancer.module.host.GlassFirstFrame

/** A pre-draw handoff lets layout, Compose and source recording run without exposing native chrome. */
internal class FragmentGlassFirstDraw(private val root: View, private val enabled: () -> Boolean) :
    ViewTreeObserver.OnPreDrawListener, AutoCloseable {
    private val frame = GlassFirstFrame()
    private val tree = root.viewTreeObserver
    init { tree.addOnPreDrawListener(this) }
    override fun onPreDraw(): Boolean {
        if (!root.isShown || root.windowVisibility != View.VISIBLE) return true
        val allow = try { frame.allowDraw(SystemClock.uptimeMillis(), enabled()) } catch (error: Throwable) {
            ModernXposedRuntime.log("Fragment glass handoff failed; restoring native drawing", error)
            true
        }
        if (allow) { close(); return true }
        root.postInvalidateOnAnimation()
        return false
    }
    override fun close() {
        frame.ready()
        tree.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        // An unattached observer merges into the window's observer on first attachment.
        root.viewTreeObserver.takeIf { it !== tree && it.isAlive }?.removeOnPreDrawListener(this)
    }
}
