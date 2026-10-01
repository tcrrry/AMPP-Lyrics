package dev.amenhancer.module.hook

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.TextView
import dev.amenhancer.module.lyrics.DesktopLyricsPresentation
import java.util.Collections
import java.util.WeakHashMap

/** Keep managed romanization in Apple's non-karaoke subtitle TextView. */
internal object NativeLyricsPronunciationSubtitle {
    private val managed = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    internal data class Selection(val owner: Any, val lineId: Int, val pronunciation: Boolean, val translation: Boolean) {
        companion object {
            fun resolve(owner: Any, lineId: Int, pronunciation: Boolean, translation: Boolean, parent: Selection?): Selection {
                val inherited = parent?.takeIf { it.owner === owner }
                return Selection(owner, lineId, inherited?.pronunciation ?: pronunciation, inherited?.translation ?: translation)
            }
        }
    }
    // This ID belongs to the verified host APK, not the module's resource table.
    @SuppressLint("ResourceType")
    private fun auxiliaryTextColor(view: TextView): Int =
        runCatching { view.context.getColor(0x7f060301) }.getOrDefault(0x59ffffff)

    private val nativeLine = ThreadLocal<Any?>()
    private val selection = ThreadLocal<Selection?>()

    fun remember(pointer: Any, ttml: String) {
        if (DesktopLyricsPresentation.fromTtml(ttml)?.pronunciation == true && TtmlTimingPolicy.isWord(ttml)) {
            managed[pointer] = true
        } else managed.remove(pointer)
    }

    internal fun isManaged(pointer: Any?): Boolean = pointer != null && managed.containsKey(pointer)

    fun install(loader: ClassLoader) {
        runCatching {
            val adapter = loader.loadClass("com.apple.android.music.player.A")
            val base = loader.loadClass("com.apple.android.music.player.i1")
            val holder = loader.loadClass("androidx.recyclerview.widget.RecyclerView\$D")
            val line = loader.loadClass("com.apple.android.music.ttml.javanative.model.LyricsLine\$LyricsLineNative")
            val constraints = loader.loadClass("androidx.constraintlayout.widget.ConstraintLayout\$b")
            constraints.getConstructor(android.view.ViewGroup.LayoutParams::class.java)
            for (name in listOf("i", "j", "k", "t", "v")) constraints.getField(name)
            val flexbox = loader.loadClass("com.apple.android.music.common.views.FullWidthAlphaGradientFlexboxLayout")
            val subtitle = adapter.getDeclaredMethod("Z", String::class.java, flexbox,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val rebuild = adapter.declaredMethods.single { it.name == "g0" && it.parameterTypes.size == 7 }
            val rowView = holder.getDeclaredField("a").apply { isAccessible = true }
            val pointer = adapter.getMethod("D")
            val type = adapter.getMethod("k", Int::class.javaPrimitiveType)
            val index = adapter.getMethod("J", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val processor = adapter.getDeclaredField("p").apply { isAccessible = true }
            val lineAt = processor.type.getMethod("a", Int::class.javaPrimitiveType)
            val dereference = lineAt.returnType.getMethod("get")
            val id = line.getMethod("getLineId")
            val pronunciation = line.getMethod("getHtmlPronunciationLineText")
            val backgroundPronunciation = line.getMethod("getHtmlPronunciationBackgroundVocalsLineText")
            val translationFlag = base.getDeclaredField("d").apply { isAccessible = true }
            val pronunciationFlag = base.getDeclaredField("e").apply { isAccessible = true }
            val fullBind = adapter.getDeclaredMethod("p", holder, Int::class.javaPrimitiveType).apply { isAccessible = true }
            val partialBind = adapter.getDeclaredMethod("q", holder, Int::class.javaPrimitiveType, List::class.java).apply { isAccessible = true }
            val pending = loader.loadClass("com.apple.android.music.player.i1\$b").getDeclaredField("y").apply { isAccessible = true }
            val cachedText = loader.loadClass("com.apple.android.music.player.i1\$b").getDeclaredField("v").apply { isAccessible = true }
            val payloads = listOf("h", "i").map { base.getDeclaredField(it).apply { isAccessible = true }.get(null) }
            var enabled = false
            fun owns(value: Any?): Boolean = enabled && value != null && isManaged(pointer.invoke(value))

            // A.g0 finishes the primary word map before we create a separate native
            // subtitle. Detaching only that appended subtitle preserves word indices.
            ModernXposedRuntime.hookMethod(rebuild, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching { PronunciationHeaderLayout.clear(param.args[2] as ViewGroup) }
                        .onFailure { ModernXposedRuntime.log("pronunciation header cleanup failed open", it) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val selected = selection.get() ?: return
                    if (!selected.pronunciation || param.throwable != null) return
                    runCatching {
                        if (selected.owner !== param.thisObject) return@runCatching
                        val value = nativeLine.get() ?: return@runCatching
                        val container = param.args[2] as ViewGroup
                        if (!PronunciationHeaderLayout.supports(container)) return@runCatching
                        // Native 6.5.3 main/background word and subtitle layout IDs.
                        val background = when (param.args[3]) {
                            0x7f0d0439 -> false
                            0x7f0d043a -> true
                            else -> return@runCatching
                        }
                        val text = (if (background) backgroundPronunciation else pronunciation).invoke(value) as? String
                        if (text.isNullOrBlank()) return@runCatching
                        subtitle.invoke(param.thisObject, text, container,
                            if (background) 0x7f0d042a else 0x7f0d0438, param.args[5], 1)
                        val pronunciationView = container.getChildAt(container.childCount - 1)
                        // r24 moved this subtitle outside the native gradient, leaving it
                        // opaque white. Use the same 35% white resource as auxiliary lyrics.
                        (pronunciationView as? TextView)?.let { it.setTextColor(auxiliaryTextColor(it)) }
                        PronunciationHeaderLayout.moveAbove(container, pronunciationView)
                    }.onFailure { ModernXposedRuntime.log("pronunciation header rendering failed open", it) }
                }
            })
            // Main karaoke words always remain the original lyrics for these pointers.
            ModernXposedRuntime.hookMethod(base.getDeclaredMethod("G"), object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && runCatching { adapter.isInstance(param.thisObject) && owns(param.thisObject) }.getOrDefault(false)) {
                        param.result = true
                    }
                }
            })
            val binding = object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        val target = param.thisObject ?: return@runCatching
                        if (param.method == fullBind) {
                            (rowView.get(param.args[0]) as? ViewGroup)?.let { PronunciationHeaderLayout.clearRow(it) }
                        }
                        if (!owns(target)) return@runCatching
                        val position = param.args[1] as Int
                        if (type.invoke(target, position) != 3) return@runCatching
                        // Native p skips word rebuilding when its three text caches match.
                        // A full auxiliary rebind must rebuild even if only visibility changed.
                        if (param.method == fullBind) cachedText.set(param.args[0], null)
                        val changes = if (param.method == partialBind) param.args[2] as List<*> else null
                        if (changes != null && (changes.isEmpty() || changes.any { it in payloads })) {
                            // A pronunciation toggle normally updates only ruby views.
                            // Rebind this row for either auxiliary toggle, never for word events.
                            @Suppress("UNCHECKED_CAST")
                            val tracked = pending.get(param.args[0]) as MutableSet<Any?>
                            if (changes.isEmpty()) tracked.clear() else tracked.addAll(changes.filter { it in payloads })
                            fullBind.invoke(target, param.args[0], position)
                            param.result = null
                            return@runCatching
                        }
                        val data = processor.get(target) ?: return@runCatching
                        val currentLine = dereference.invoke(lineAt.invoke(data, index.invoke(target, position, true))) ?: return@runCatching
                        val previous = selection.get()
                        val translated = translationFlag.getBoolean(target)
                        val pronounced = pronunciationFlag.getBoolean(target)
                        // Some partial updates invoke full binding internally;
                        // inherit real choices rather than their temporary masked flags.
                        val selected = Selection.resolve(target, id.invoke(currentLine) as Int, pronounced, translated, previous)
                        param.extras["subtitle-state"] = Triple(previous, translated, pronounced)
                        param.extras["header-previous-line"] = nativeLine.get()
                        nativeLine.set(currentLine)
                        selection.set(selected)
                        // Native translation bookkeeping must reflect actual translation;
                        // pronunciation is now outside the word/translation Flexbox.
                        translationFlag.setBoolean(target, selected.translation)
                        pronunciationFlag.setBoolean(target, false)
                    }.onFailure { error ->
                        restore(param)
                        ModernXposedRuntime.log("pronunciation subtitle binding failed open", error)
                    }
                }
                private fun restore(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    val state = param.extras.remove("subtitle-state") as? Triple<Selection?, Boolean, Boolean> ?: return
                    try {
                        translationFlag.setBoolean(param.thisObject, state.second)
                        pronunciationFlag.setBoolean(param.thisObject, state.third)
                    } finally {
                        val previousLine = param.extras.remove("header-previous-line")
                        if (previousLine == null) nativeLine.remove() else nativeLine.set(previousLine)
                        if (state.first == null) selection.remove() else selection.set(state.first)
                    }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { restore(param) }.onFailure { ModernXposedRuntime.log("pronunciation subtitle restore failed", it) }
                }
            }
            ModernXposedRuntime.hookMethod(fullBind, binding)
            ModernXposedRuntime.hookMethod(partialBind, binding)
            enabled = true
            ModernXposedRuntime.log("managed pronunciation subtitle layout installed")
        }.onFailure { ModernXposedRuntime.log("pronunciation subtitle layout unavailable; kept native layout", it) }
    }
}
