package dev.amenhancer.module.hook

/** Native material alpha ownership, including resume writes and cached RenderNode refreshes. */
class FragmentNativeAlphaLease(private val read: () -> Float, private val write: (Float) -> Unit) {
    var native = read()
        private set
    var factor = 1f
        private set
    private var last: Float? = null

    fun observe() { val current = read(); if (current != last) native = current }
    private fun own(alpha: Float): Float { last = alpha; return alpha }
    fun hostWrite(value: Float): Float? {
        native = value
        return if (factor != 1f) own(value * factor) else null
    }
    fun hide(value: Boolean) = scale(if (value) 0f else 1f)
    fun scale(value: Float) {
        observe()
        val old = factor
        factor = value
        if (value != 1f) write(own(native * value))
        else if (old != 1f) {
            if (read() == last) write(own(native))
            last = null
        }
    }
}
