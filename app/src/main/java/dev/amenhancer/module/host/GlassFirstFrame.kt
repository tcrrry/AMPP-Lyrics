package dev.amenhancer.module.host

/** Holds a new home frame during replacement setup, without suppressing native UI indefinitely. */
class GlassFirstFrame(private val timeoutMs: Long = 750L) {
    private var started: Long? = null
    private var released = false
    val pending: Boolean get() = !released
    fun ready() { released = true }
    fun allowDraw(now: Long, enabled: Boolean): Boolean {
        if (released) return true
        if (!enabled) { ready(); return true }
        val start = started ?: now.also { started = it }
        if (now - start >= timeoutMs) { ready(); return true }
        return false
    }
}
