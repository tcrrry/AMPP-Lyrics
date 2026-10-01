package dev.amenhancer.module.hook

import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Native ConstraintLayout parameters belong to the host's class loader. */
internal object PronunciationHeaderLayout {
    private data class Header(val view: WeakReference<View>, val topToTop: Int, val topToBottom: Int,
        val predecessors: List<WeakReference<View>>)
    private val headers = WeakHashMap<ViewGroup, Header>()

    private fun field(type: Class<*>, name: String) = type.getField(
        if (type.name == "androidx.constraintlayout.widget.ConstraintLayout\$b") {
            // Verified against the 6.5.3 constructor/XML parser and RTL resolver.
            when (name) {
                "topToTop" -> "i"
                "topToBottom" -> "j"
                "bottomToTop" -> "k"
                "startToStart" -> "t"
                "endToEnd" -> "v"
                else -> name
            }
        } else name
    )

    fun supports(container: ViewGroup): Boolean = runCatching {
        val parent = container.parent as? ViewGroup ?: return@runCatching false
        val type = container.layoutParams.javaClass
        type.getConstructor(ViewGroup.LayoutParams::class.java)
        for (name in listOf("topToTop", "topToBottom", "bottomToTop", "startToStart", "endToEnd")) field(type, name)
        parent != container && container.id != View.NO_ID
    }.getOrDefault(false)

    fun moveAbove(container: ViewGroup, view: View) {
        check(view.parent === container && supports(container))
        clear(container)
        val parent = container.parent as ViewGroup
        val original = container.layoutParams
        val type = original.javaClass
        val top = field(type, "topToTop")
        val below = field(type, "topToBottom")
        val topValue = top.getInt(original)
        val belowValue = below.getInt(original)
        val nativeParams = view.layoutParams
        val headerParams = type.getConstructor(ViewGroup.LayoutParams::class.java)
            .newInstance(ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)) as ViewGroup.MarginLayoutParams
        top.setInt(headerParams, topValue)
        below.setInt(headerParams, belowValue)
        field(type, "bottomToTop").setInt(headerParams, container.id)
        field(type, "startToStart").setInt(headerParams, container.id)
        field(type, "endToEnd").setInt(headerParams, container.id)
        headerParams.marginStart = container.paddingStart
        headerParams.marginEnd = container.paddingEnd
        headerParams.topMargin = original.let { (it as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0 }
        view.id = View.generateViewId()
        val predecessors = (0 until parent.childCount).map { parent.getChildAt(it) }.filter {
            it !== container && it.layoutParams.javaClass == type &&
                field(type, "bottomToTop").getInt(it.layoutParams) == container.id
        }
        container.removeView(view)
        try {
            parent.addView(view, headerParams)
            top.setInt(original, -1)
            below.setInt(original, view.id)
            for (previous in predecessors) {
                field(type, "bottomToTop").setInt(previous.layoutParams, view.id)
                previous.layoutParams = previous.layoutParams
            }
            (original as? ViewGroup.MarginLayoutParams)?.topMargin = 0
            container.layoutParams = original
            headers[container] = Header(WeakReference(view), topValue, belowValue, predecessors.map { WeakReference(it) })
        } catch (error: Exception) {
            parent.removeView(view)
            top.setInt(original, topValue)
            below.setInt(original, belowValue)
            for (previous in predecessors) field(type, "bottomToTop").setInt(previous.layoutParams, container.id)
            (original as? ViewGroup.MarginLayoutParams)?.topMargin = headerParams.topMargin
            container.addView(view, nativeParams)
            throw error
        }
    }

    fun clear(container: ViewGroup) {
        val header = headers.remove(container) ?: return
        val view = header.view.get()
        val parameters = container.layoutParams
        field(parameters.javaClass, "topToTop").setInt(parameters, header.topToTop)
        field(parameters.javaClass, "topToBottom").setInt(parameters, header.topToBottom)
        for (previous in header.predecessors.mapNotNull { it.get() }) {
            field(previous.layoutParams.javaClass, "bottomToTop").setInt(previous.layoutParams, container.id)
            previous.layoutParams = previous.layoutParams
        }
        (parameters as? ViewGroup.MarginLayoutParams)?.topMargin = (view?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        (view?.parent as? ViewGroup)?.removeView(view)
        container.layoutParams = parameters
    }

    fun clearRow(root: ViewGroup) {
        // Snapshot because removing headers mutates this row's children.
        val children = (0 until root.childCount).map { root.getChildAt(it) }
        for (child in children) if (child is ViewGroup) {
            clear(child)
            clearRow(child)
        }
    }
}
