package dev.amenhancer.module.hook

import android.util.Log

/** Uses the existing opt-in log tag; no lyric text, files or background polling. */
internal class LyricBlurDiagnostics(private val build: String) {
    private var emitted = 0
    private var lastFrame: String? = null
    private val failures = linkedSetOf<String>()
    val enabled: Boolean get() = runCatching { Log.isLoggable(LyricHighlightProbe.TAG, Log.DEBUG) }.getOrDefault(false)

    fun event(source: String, detail: () -> String) {
        if (!enabled || emitted >= LyricHighlightProbe.MAX_EVENTS) return
        emitted += 1
        runCatching { Log.d(LyricHighlightProbe.TAG, "${LyricHighlightProbe.PREFIX} build=$build source=$source ${detail()}") }
    }

    fun failure(role: String) {
        if (failures.size >= LyricHighlightProbe.MAX_EVENTS || !failures.add(role)) return
        runCatching { ModernXposedRuntime.log("Lyric blur degraded: $build $role") }
    }

    fun frame(state: LyricBlurState, renderer: LyricBlurViewRenderer, nowMs: Long, running: Boolean) {
        if (!enabled) return
        val key = "${state.epoch}|${state.protectedLineIds}|${state.focusAnchor}|${state.interaction}|$running|${renderer.unavailableBaselines}"
        if (key == lastFrame) return
        lastFrame = key
        val anchor = when (val focus = state.focusAnchor) {
            is LyricFocusAnchor.Lines -> "lines:${focus.ordinals.take(LyricHighlightProbe.MAX_IDS)} count=${focus.ordinals.size}"
            is LyricFocusAnchor.Instrumental -> "instrumental:${focus.beforeOrdinal}"
            is LyricFocusAnchor.Ending -> "ending:${focus.ordinals.take(LyricHighlightProbe.MAX_IDS)} count=${focus.ordinals.size}"
            LyricFocusAnchor.None -> "none"
            LyricFocusAnchor.Prelude -> "prelude:0"
        }
        event("display") { "epoch=${state.epoch} interaction=${state.interaction}/${state.interactionGeneration}" +
            " protected=${state.protectedLineIds.take(LyricHighlightProbe.MAX_IDS)} anchor=$anchor" +
            " rows=id:ordinal:position:binding:target:actual:elapsed ${renderer.observations(nowMs)}" +
            " unavailableBaselines=${renderer.unavailableBaselines}" }
    }
}
