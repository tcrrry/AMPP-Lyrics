package dev.amenhancer.module.hook

import android.graphics.Rect
import android.view.ViewGroup
import dev.amenhancer.module.host.OwnedHostProperty

/** Lets blur spread beyond each short item while keeping the list viewport clipped. */
internal class LyricBlurClippingLease(val recycler: ViewGroup) : AutoCloseable {
    private val children = OwnedHostProperty({ recycler.clipChildren }, { recycler.clipChildren = it })
    private val viewport = OwnedHostProperty<Rect?>(
        { recycler.clipBounds?.let(::Rect) },
        { recycler.clipBounds = it?.let(::Rect) },
    )
    private var nativeBounds = recycler.clipBounds?.let(::Rect)
    private var lastBounds: Rect? = null
    private var applied = false

    fun needsUpdate(): Boolean = applied && (recycler.clipChildren || recycler.clipBounds != lastBounds)

    fun update() {
        val current = recycler.clipBounds
        if (applied && current != lastBounds) nativeBounds = current?.let(::Rect)
        val bounds = Rect(0, 0, recycler.width, recycler.height)
        nativeBounds?.let { if (!bounds.intersect(it)) bounds.setEmpty() }
        if (!applied || current != bounds) viewport.set(bounds)
        lastBounds = bounds
        if (!applied || recycler.clipChildren) children.set(false)
        applied = true
    }

    override fun close() {
        viewport.close()
        children.close()
        applied = false
    }
}
