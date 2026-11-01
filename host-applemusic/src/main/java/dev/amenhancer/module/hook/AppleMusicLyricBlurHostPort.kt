package dev.amenhancer.module.hook

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import io.github.proify.lyricon.amprovider.xposed.internal.WeakIdentityMap

/** Private host objects stay in this binding. Only copied IDs enter the reducer. */
internal class AppleMusicLyricBlurHostPort(
    owner: Any,
    private val contract: AppleMusicLyricBlurContract,
    private val observeModel: (Any) -> Unit,
    private val highlightAnimationChanged: () -> Unit,
) : LyricBlurHostPort, AutoCloseable {
    private data class TextAnimationWatch(val parent: Animator, val listener: AnimatorListenerAdapter)
    private val ownerRef = WeakReference(owner)
    private var ordinals: Map<Int, Int> = emptyMap()
    private var firstLineBeginMs: Long? = null
    private var lastLineEndMs: Long? = null
    private var frameAdapter: Any? = null
    private val models = WeakIdentityMap<Any, Boolean>()
    private val ignoredAnimations = WeakIdentityMap<Animator, Boolean>()
    private val textAnimationWatches = IdentityHashMap<Animator, TextAnimationWatch>()

    override fun recycler(): ViewGroup? = ownerRef.get()?.let { owner ->
        contract.field("binding").get(owner)?.let { binding ->
            (contract.field("recycler").get(binding) as? ViewGroup)?.takeIf(contract.recyclerClass::isInstance)
        }
    }

    fun adapter(): Any? = recycler()?.let { contract.method("getAdapter").invoke(it) }
        ?.takeIf(contract::isAdapter)

    fun beginSnapshot(adapter: Any?) { frameAdapter = adapter }

    fun ownsAdapter(adapter: Any): Boolean = this.adapter() === adapter
    fun ownsViewModel(viewModel: Any): Boolean = ownerRef.get()?.let {
        contract.field("viewModel").get(it) === viewModel
    } == true
    fun callbacks(): List<Pair<String, Any>> = ownerRef.get()?.let { owner ->
        AppleMusicLyricBlurContract.nativeRoles.mapNotNull { role ->
            contract.field(role).get(owner)?.let { role to it }
        }
    }.orEmpty()

    override fun document(): LyricDecodeResult<Map<Int, Int>> {
        stopObservingAnimations()
        models.clear()
        ignoredAnimations.clear()
        ordinals = emptyMap()
        firstLineBeginMs = null
        lastLineEndMs = null
        val values = linkedMapOf<Int, Int>()
        var firstBegin: Long? = null
        var lastEnd: Long? = null
        var failure: String? = null
        return try {
            val adapter = adapter() ?: return LyricDecodeResult.Pending("document:awaiting-adapter")
            if (contract.adapterMethod(adapter, "Token").invoke(adapter) == null) return LyricDecodeResult.Complete(emptyMap())
            val reader = contract.adapterMethod(adapter, "Reader").invoke(adapter)
                ?: return LyricDecodeResult.Complete(emptyMap())
            val count = (contract.method("readerCount").invoke(reader) as Number).toInt()
            check(count >= 0)
            for (ordinal in 0 until count) {
                runCatching {
                    val pointer = checkNotNull(contract.method("readerLine").invoke(reader, ordinal))
                    val line = checkNotNull(contract.method("lineGet").invoke(pointer))
                    val id = (contract.method("nativeLineId").invoke(line) as Number).toInt()
                    check(values.put(id, ordinal) == null) { "Duplicate native line ID" }
                    if (ordinal == 0) firstBegin = (contract.method("nativeLineBegin").invoke(line) as Number)
                        .toLong().takeIf { it >= 0 }
                    val end = (contract.method("nativeLineEnd").invoke(line) as Number).toLong()
                    check(end >= 0)
                    lastEnd = maxOf(lastEnd ?: end, end)
                }.onFailure { failure = it.javaClass.simpleName }
            }
            ordinals = values.toMap()
            if (failure == null) {
                firstLineBeginMs = firstBegin
                lastLineEndMs = lastEnd
            }
            failure?.let { LyricDecodeResult.Partial(ordinals, "document:$it") }
                ?: LyricDecodeResult.Complete(ordinals)
        } catch (error: Exception) {
            LyricDecodeResult.Failed("document:${error.javaClass.simpleName}")
        }
    }

    override fun row(view: View, bindingGeneration: Long): LyricBlurBoundRow {
        fun unknown() = LyricBlurBoundRow(view, LyricRowSnapshot(null, null, null, LyricRowKind.UNKNOWN, bindingGeneration))
        return try {
            val adapter = frameAdapter ?: return unknown()
            val position = (contract.method("rowPosition").invoke(null, view) as Number).toInt()
            if (position < 0) return unknown()
            val holder = contract.method("rowHolder").invoke(null, view) ?: return unknown()
            val type = (contract.adapterMethod(adapter, "Type").invoke(adapter, position) as Number).toInt()
            if ((contract.field("holderType").get(holder) as Number).toInt() != type) return unknown()
            val kind = when (type) {
                0 -> if (contract.lineAdapter.isInstance(adapter)) LyricRowKind.LYRIC else LyricRowKind.UNKNOWN
                3 -> if (contract.wordAdapter.isInstance(adapter)) LyricRowKind.LYRIC else LyricRowKind.UNKNOWN
                1 -> LyricRowKind.CREDITS
                2 -> LyricRowKind.INSTRUMENTAL
                else -> LyricRowKind.UNKNOWN
            }
            if (kind == LyricRowKind.LYRIC) {
                val ordinal = (contract.adapterMethod(adapter, "Map").invoke(adapter, position, true) as Number).toInt()
                val id = (contract.adapterMethod(adapter, "Id").invoke(adapter, position) as Number).toLong()
                if ((contract.field("holderId").get(holder) as Number).toLong() != id) return unknown()
                if (id !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || ordinals[id.toInt()] != ordinal) return unknown()
                LyricBlurBoundRow(view, LyricRowSnapshot(id.toInt(), ordinal, position, kind, bindingGeneration))
            } else LyricBlurBoundRow(view, LyricRowSnapshot(null,
                if (kind == LyricRowKind.CREDITS) ordinals.values.maxOrNull() else null,
                position, kind, bindingGeneration))
        } catch (_: Exception) { unknown() }
    }

    override fun displayed(rows: List<LyricBlurBoundRow>): LyricDecodeResult<LyricDisplaySnapshot> {
        return try {
        val adapter = frameAdapter ?: return LyricDecodeResult.Pending("display:awaiting-adapter")
        val raw = contract.adapterMethod(adapter, "Snapshot").invoke(adapter) as? Set<*>
            ?: return LyricDecodeResult.Failed("display:invalid-snapshot")
        check(raw.all { it is Int })
        val displayed = raw.map { it as Int }.toSet()
        val words = LyricWordSource.entries.associateWith { linkedSetOf<Int>() }
        if (contract.wordAdapter.isInstance(adapter)) {
            rows.filter { it.identity.kind == LyricRowKind.LYRIC }.forEach { row ->
                val holder = contract.method("rowHolder").invoke(null, row.view)
                check(holder != null && contract.wordHolder.isInstance(holder))
                contract.wordMaps.forEach maps@{ (source, field) ->
                    val map = field.get(holder) as? Map<*, *> ?: return@maps
                    map.values.forEach { model ->
                        check(model != null && contract.wordModel.isInstance(model))
                        if (models[model] != true) {
                            models[model] = true
                            observeModel(model)
                        }
                        val id = (contract.field("wordLineId").get(model) as Number).toInt()
                        if (hasLiveTextAnimation(model)) {
                            words.getValue(source).add(id)
                        }
                    }
                }
            }
        }
        val gap = (contract.field(if (contract.wordAdapter.isInstance(adapter)) "wordInstrumentalPosition"
            else "instrumentalPosition").get(adapter) as Number).toInt().takeIf { it in 0..ordinals.size }
        val position = if (displayed.isEmpty() && words.values.all { it.isEmpty() } && gap == null &&
            (firstLineBeginMs?.let { it > 0 } == true || lastLineEndMs?.let { it > 0 } == true)) runCatching {
            val owner = ownerRef.get() ?: return@runCatching null
            val browser = contract.method("mediaBrowser").invoke(owner) ?: return@runCatching null
            (contract.method("mediaPosition").invoke(browser) as Number).toLong().takeIf { it >= 0 }
        }.getOrNull() else null
        LyricDecodeResult.Complete(LyricDisplaySnapshot(
            displayedLineIds = displayed, words = words.mapValues { it.value.toSet() },
            instrumentalBeforeOrdinal = gap, firstLineBeginMs = firstLineBeginMs,
            playbackPositionMs = position, lastLineEndMs = lastLineEndMs,
            instrumentalRowVisible = rows.any { it.isVisibleInstrumental() && it.identity.adapterPosition == gap }))
    } catch (error: Exception) { LyricDecodeResult.Failed("display:${error.javaClass.simpleName}") }
    }

    private fun textAnimations(animation: Animator): List<Animator> {
        if (contract.isWordMovement(animation)) return emptyList()
        return if (animation is AnimatorSet) animation.childAnimations.flatMap(::textAnimations)
        else listOf(animation)
    }

    private fun hasLiveTextAnimation(model: Any): Boolean {
        val progress = contract.field("wordAnimator").get(model) as? Animator ?: return false
        if (ignoredAnimations[progress] == true) return false
        val text = textAnimations(progress)
        text.forEach { observeTextAnimation(progress, it) }
        // p contains glow/scale and spring movement, which can outlive the text highlight.
        return text.any { ignoredAnimations[it] != true && (it.isStarted || it.isRunning || it.isPaused) }
    }

    private fun observeTextAnimation(parent: Animator, animation: Animator) {
        val old = textAnimationWatches[animation]
        if (old?.parent === parent) return
        old?.let { animation.removeListener(it.listener) }
        val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) = highlightAnimationChanged()
            override fun onAnimationEnd(animation: Animator) = finished(animation)
            override fun onAnimationCancel(animation: Animator) = finished(animation)

            private fun finished(animation: Animator) {
                if (textAnimationWatches[animation]?.listener !== this) return
                textAnimationWatches.remove(animation)
                animation.removeListener(this)
                highlightAnimationChanged()
            }
        }
        textAnimationWatches[animation] = TextAnimationWatch(parent, listener)
        animation.addListener(listener)
    }

    fun stopObservingAnimations() {
        textAnimationWatches.entries.toList().forEach { (animation, watch) -> animation.removeListener(watch.listener) }
        textAnimationWatches.clear()
    }

    fun seek() {
        models.keys().forEach { model -> runCatching {
            (contract.field("wordAnimator").get(model) as? Animator)?.let { progress ->
                ignoredAnimations[progress] = true
                textAnimations(progress).forEach { ignoredAnimations[it] = true }
            }
        } }
        stopObservingAnimations()
    }

    fun wordStarted(model: Any, animation: Animator) {
        if (models[model] == true) {
            ignoredAnimations.remove(animation)
            textAnimations(animation).forEach { ignoredAnimations.remove(it) }
        }
    }

    fun wordFinished(animation: Animator) {
        textAnimationWatches.entries.filter { it.value.parent === animation }.toList().forEach { (child, watch) ->
            child.removeListener(watch.listener)
            textAnimationWatches.remove(child)
        }
    }

    override fun close() {
        stopObservingAnimations()
        models.clear()
        ignoredAnimations.clear()
        frameAdapter = null
        ownerRef.clear()
    }
}

internal fun LyricBlurBoundRow.isVisibleInstrumental(): Boolean =
    identity.kind == LyricRowKind.INSTRUMENTAL && view.isShown && view.width > 0 && view.height > 0
