package dev.amenhancer.backdrop

/** Scoped CPU markers for system traces; a null name leaves uninstrumented callers alone. */
inline fun <T> traceBackdrop(name: String?, block: () -> T): T {
    if (name == null) return block()
    val tracing = beginBackdropTrace(name)
    try {
        return block()
    } finally {
        if (tracing) endBackdropTrace()
    }
}

@PublishedApi
internal expect fun beginBackdropTrace(name: String): Boolean

@PublishedApi
internal expect fun endBackdropTrace()
