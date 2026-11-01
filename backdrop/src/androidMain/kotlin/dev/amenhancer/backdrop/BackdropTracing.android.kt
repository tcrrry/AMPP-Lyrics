package dev.amenhancer.backdrop

import android.os.Build
import android.os.Trace

@PublishedApi
internal actual fun beginBackdropTrace(name: String): Boolean {
    if (Build.VERSION.SDK_INT < 29 || !Trace.isEnabled()) return false
    Trace.beginSection(name)
    return true
}

@PublishedApi
internal actual fun endBackdropTrace() {
    Trace.endSection()
}
