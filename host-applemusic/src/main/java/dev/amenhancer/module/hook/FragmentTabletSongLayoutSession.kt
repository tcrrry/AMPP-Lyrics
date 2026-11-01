package dev.amenhancer.module.hook

import android.view.View
import android.view.ViewGroup
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.host.OwnedHostProperty
import java.lang.reflect.Field

/** One view lifetime owns native property changes and any added metadata guidelines. */
internal class FragmentTabletSongLayoutSession(
    private val native: FragmentTabletSongLayoutContract,
    private val container: ViewGroup,
    private val registerControls: (View, Float) -> Unit,
    private val unregisterControls: (View) -> Unit,
    private val onClosed: (FragmentTabletSongLayoutSession) -> Unit,
) : AutoCloseable {
    private val owned = ArrayList<AutoCloseable>()
    private val addedGuidelines = ArrayList<View>()
    private var controlsRoot: View? = null
    private var closed = false
    private val ids = native.resources.mapValues { (role, name) ->
        container.resources.getIdentifier(name, "id", ModuleConstants.TARGET_PACKAGE).also {
            check(it != 0) { "songLayout.resources.$role: id/$name missing" }
        }
    }
    private val dimensions = native.dimensions.mapValues { (role, name) ->
        val id = container.resources.getIdentifier(name, "dimen", ModuleConstants.TARGET_PACKAGE)
        check(id != 0) { "songLayout.dimensions.$role: dimen/$name missing" }
        container.resources.getDimensionPixelSize(id)
    }

    private fun view(role: String): View = checkNotNull(container.findViewById<View>(ids.getValue(role))) {
        "songLayout.resources.$role: view missing from current song column"
    }
    private fun <T> own(read: () -> T, write: (T) -> Unit, value: T) {
        val property = OwnedHostProperty(read, write)
        owned += property
        property.set(value)
    }
    private fun params(view: View): ViewGroup.MarginLayoutParams =
        (view.layoutParams as ViewGroup.MarginLayoutParams).also { check(native.paramsType.isInstance(it)) }
    private fun field(view: View, field: Field, value: Int) = own(
        { field.getInt(params(view)) },
        { current -> params(view).also { field.setInt(it, current); view.layoutParams = it } }, value,
    )
    private fun startMargin(view: View, value: Int) = own(
        { params(view).marginStart }, { current -> params(view).also { it.marginStart = current; view.layoutParams = it } }, value,
    )
    private fun endMargin(view: View, value: Int) = own(
        { params(view).marginEnd }, { current -> params(view).also { it.marginEnd = current; view.layoutParams = it } }, value,
    )

    fun apply() {
        check(!closed && native.containerType.isInstance(container))
        // Find/validate required native views and binding before mutation.
        val start = view("artworkStart")
        val end = view("artworkEnd")
        val title = view("title")
        val subtitle = view("subtitle")
        val collaborator = view("collaborator")
        val more = view("more")
        val controls = view("controls")
        val barrier = view("barrier").also { check(native.barrierType.isInstance(it)) }
        val binding = checkNotNull(native.bindingForView.invoke(null, controls))
        check(native.setSeekbarPadding.declaringClass.isInstance(binding))
        listOf(start, end, title, subtitle, collaborator, more, controls).forEach(::params)
        val artworkInset = dimensions.getValue("artworkInset")
        val metadataInset = dimensions.getValue("metadataInset")
        val progressInset = dimensions.getValue("progressInset").toFloat()
        val metadataStart = guideline("metadataStart", start.layoutParams, metadataInset, true)
        val metadataEnd = guideline("metadataEnd", end.layoutParams, metadataInset, false)

        field(start, native.guideBegin, artworkInset)
        field(end, native.guideEnd, artworkInset)
        listOf(title, subtitle).forEach { field(it, native.startToStart, metadataStart.id); startMargin(it, metadataInset) }
        field(collaborator, native.startToStart, metadataStart.id)
        field(collaborator, native.endToEnd, metadataEnd.id)
        startMargin(collaborator, metadataInset); endMargin(collaborator, metadataInset)
        field(more, native.endToEnd, metadataEnd.id)
        own({ more.paddingEnd }, { more.setPaddingRelative(more.paddingStart, more.paddingTop, it, more.paddingBottom) }, metadataInset)
        field(controls, native.startToStart, -1); field(controls, native.endToEnd, -1)
        own({ controls.layoutParams.width }, { current -> controls.layoutParams.also { it.width = current; controls.layoutParams = it } },
            ViewGroup.LayoutParams.MATCH_PARENT)
        own({ native.getMargin.invoke(barrier) as Int }, { native.setMargin.invoke(barrier, it) }, 0)
        // Use value equality for arrays so ownership follows the shared property's contract.
        own({ (native.getReferences.invoke(barrier) as IntArray).toList() },
            { native.setReferences.invoke(barrier, it.toIntArray()) }, listOf(collaborator.id, title.id))
        own({ native.seekbarPadding.get(binding) as Float? }, { native.setSeekbarPadding.invoke(binding, it) }, progressInset)
        controlsRoot = checkNotNull(native.bindingRoot.get(binding) as? View).also { registerControls(it, progressInset) }
    }

    private fun guideline(role: String, template: ViewGroup.LayoutParams, inset: Int, begin: Boolean): View {
        container.findViewById<View>(ids.getValue(role))?.let {
            field(it, if (begin) native.guideBegin else native.guideEnd, inset)
            return it
        }
        val view = native.guideline.newInstance(container.context) as View
        val params = native.newParams.newInstance(template) as ViewGroup.LayoutParams
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT; params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        native.orientation.setInt(params, 1)
        native.guideBegin.setInt(params, if (begin) inset else -1)
        native.guideEnd.setInt(params, if (begin) -1 else inset)
        native.guidePercent.setFloat(params, -1f)
        view.id = ids.getValue(role)
        addedGuidelines += view
        container.addView(view, params)
        return view
    }

    override fun close() {
        if (closed) return
        closed = true
        controlsRoot?.let(unregisterControls); controlsRoot = null
        owned.asReversed().forEach { property ->
            runCatching { property.close() }.onFailure { ModernXposedRuntime.log("Song-column property restoration failed", it) }
        }
        owned.clear()
        addedGuidelines.forEach { view -> if (view.parent === container) container.removeView(view) }
        addedGuidelines.clear()
        onClosed(this)
    }
}
