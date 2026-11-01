package dev.amenhancer.module.hook

import dev.amenhancer.host.applemusic.AppleMusicHostProfile
import java.util.WeakHashMap
import org.json.JSONObject

/**
 * Terminal sweep geometry adapted from Tcrrry-Lyrics-Plugin's
 * NativeTerminalGradientFix (GPL-3.0). Native durations and word grouping stay owned by Apple.
 */
internal class AppleMusicCjkTerminalGradientTarget(
    private val profile: AppleMusicHostProfile,
    private val loader: ClassLoader,
) {
    private data class Sweep(val word: Any, var compensated: Boolean = false)
    private val sweep = ThreadLocal<Sweep>()
    private val lastByReader = WeakHashMap<Any, MutableMap<Int, Set<Int>>>()
    private var registration: HookRegistrationScope? = null
    private var lastFailure: String? = null

    fun install(): TargetCapabilityInstall {
        val names = profile.document.optJSONObject("cjk")?.optJSONObject("terminalGradient")
            ?: return TargetCapabilityInstall.Unsupported("Terminal CJK sweep has no exact contract for ${profile.id}")
        if (registration?.isActive == true) return TargetCapabilityInstall.Active("Terminal CJK sweep already active")
        val scope = HookRegistrationScope()
        return try {
            val contract = TerminalGradientContract(profile, loader, names)
            check(ModernXposedRuntime.hookMethod(contract.method("margin"), object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = sweep.get() ?: return
                    if (param.throwable != null || param.args[3] !== current.word) return
                    val original = param.result as? Float ?: return
                    val feather = param.args[0] as? Float ?: return
                    if (!original.isFinite() || !feather.isFinite() || feather <= 0f || feather > 1f) return
                    val corrected = cjkTerminalGradientMargin(original, feather)
                    if (corrected != original) param.result = corrected
                    current.compensated = true
                }
            }, scope)) { "Terminal CJK margin hook was rejected" }
            check(ModernXposedRuntime.hookMethod(contract.method("build"), object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.extras[PREVIOUS_SWEEP] = sweep.get()
                    sweep.remove()
                    guarded {
                        val adapter = param.thisObject ?: return@guarded
                        val word = param.args[2] ?: return@guarded
                        // The alternate sweep branch and Latin readings retain their native margins.
                        if (param.args[7] == true) return@guarded
                        val text = contract.field("text").get(word) as? CharSequence ?: return@guarded
                        if (containsCjkKaraokeScript(text) && isTerminal(contract, adapter, word)) {
                            sweep.set(Sweep(word))
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        if (sweep.get()?.compensated != true || param.throwable != null) return
                        guarded {
                            val animations = param.result as? List<*> ?: return@guarded
                            // A delayed rush must not repaint a mask that now completes in its own sweep.
                            val retained = animations.filterNot { value ->
                                value != null && contract.isRushGradient(value)
                            }
                            if (retained.size != animations.size) param.result = ArrayList(retained)
                        }
                    } finally {
                        val previous = param.extras.remove(PREVIOUS_SWEEP) as? Sweep
                        if (previous == null) sweep.remove() else sweep.set(previous)
                    }
                }
            }, scope)) { "Terminal CJK sweep hook was rejected" }
            scope.onClose {
                sweep.remove()
                synchronized(lastByReader) { lastByReader.clear() }
            }
            scope.activate()
            registration = scope
            TargetCapabilityInstall.Active("Terminal CJK feather compensation installed; ${profile.id}")
        } catch (error: Throwable) {
            scope.close()
            ModernXposedRuntime.log("Terminal CJK sweep unavailable; kept native geometry", error)
            TargetCapabilityInstall.Degraded("Terminal CJK sweep contract/registration failed; ${profile.id}: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun isTerminal(contract: TerminalGradientContract, adapter: Any, word: Any): Boolean {
        val reader = contract.field("reader").get(adapter) ?: return false
        val row = contract.field("rowId").getInt(word)
        val expected = contract.field("wordId").getInt(word)
        return synchronized(lastByReader) {
            lastByReader[reader]?.get(row)?.let { return@synchronized expected in it }
            val pointer = contract.method("readerLine").invoke(reader, row) ?: return@synchronized false
            val line = contract.method("lineGet").invoke(pointer) ?: return@synchronized false
            val ids = mutableSetOf<Int>()
            // Original text and native pronunciation have independent word IDs.
            for (role in listOf("lineWords", "linePronunciationWords")) {
                val vector = contract.method(role).invoke(line) ?: continue
                val count = (contract.method("vectorSize").invoke(vector) as Number).toLong()
                if (count !in 2L..4096L) continue
                var last: Int? = null
                var latest = -1L
                for (index in 0 until count) {
                    val wordPointer = contract.method("vectorGet").invoke(vector, index) ?: continue
                    val nativeWord = contract.method("wordGet").invoke(wordPointer) ?: continue
                    val begin = (contract.method("wordBegin").invoke(nativeWord) as Number).toLong()
                    val end = (contract.method("wordEnd").invoke(nativeWord) as Number).toLong()
                    if (begin >= 0 && end > begin && end >= latest) {
                        latest = end
                        last = (contract.method("nativeWordId").invoke(nativeWord) as Number).toInt()
                    }
                }
                last?.let(ids::add)
            }
            lastByReader.getOrPut(reader) { mutableMapOf() }[row] = ids
            expected in ids
        }
    }

    private inline fun guarded(action: () -> Unit) {
        runCatching(action).onFailure { error ->
            val reason = error.javaClass.simpleName
            if (lastFailure != reason) {
                lastFailure = reason
                ModernXposedRuntime.log("Terminal CJK sweep callback kept native behavior: $reason")
            }
        }
    }

    private companion object {
        const val PREVIOUS_SWEEP = "cjkTerminalGradient.previous"
    }
}

/** Every private member is resolved from the selected build before either hook becomes active. */
private class TerminalGradientContract(profile: AppleMusicHostProfile, loader: ClassLoader, names: JSONObject) {
    private val members = FragmentProfileMembers(loader, names, "cjk.terminalGradient", FragmentMemberRequirements(
        methods = setOf("build", "margin", "readerLine", "lineGet", "lineWords", "linePronunciationWords",
            "vectorSize", "vectorGet", "wordGet", "wordBegin", "wordEnd", "nativeWordId", "animationTag"),
        fields = setOf("reader", "rowId", "wordId", "text"),
    ))
    private val taggedAnimator = method("animationTag").declaringClass

    init {
        check(profile.productionEnabled && names.getInt("contractVersion") == 1)
        check(taggedAnimator.isInterface)
        check(method("build").declaringClass == field("reader").declaringClass)
        check(method("build").parameterTypes[2] == field("text").declaringClass)
    }

    fun method(role: String) = members.method(role)
    fun field(role: String) = members.field(role)
    fun isRushGradient(value: Any): Boolean = taggedAnimator.isInstance(value) &&
        method("animationTag").invoke(value) == "KARAOKE_SPLIT_ON_CHARS_LANGUAGES_RUSH_GRADIENT_TAG"
}
