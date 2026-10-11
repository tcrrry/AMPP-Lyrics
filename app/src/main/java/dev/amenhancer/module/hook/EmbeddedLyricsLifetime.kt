package dev.amenhancer.module.hook

/** Embedded modules share the host process lifetime rather than an SDK plugin
 * unload event. View detach/recycle cleanup remains owned by each presentation.
 * This scope also provides orderly cleanup for an explicit runtime shutdown.
 */
internal object EmbeddedLyricsLifetime {
    private var scope = HookRegistrationScope().apply { activate() }
    val alive get() = scope.isActive
    fun close(action: () -> Unit) = scope.onClose(action)
    fun stop() = scope.close()
    fun start() { if (!alive) scope = HookRegistrationScope().apply { activate() } }
}
