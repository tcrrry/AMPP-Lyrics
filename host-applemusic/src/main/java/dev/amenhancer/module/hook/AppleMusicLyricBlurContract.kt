package dev.amenhancer.module.hook

import android.animation.Animator
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.host.applemusic.AppleMusicHostProfile
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Every private role is selected by an exact production build, resolved before registration. */
internal class AppleMusicLyricBlurContract(val profile: AppleMusicHostProfile, loader: ClassLoader) {
    companion object {
        private val commonMethods = setOf("createView", "destroyView", "resume", "installDocument",
            "readerCount", "readerLine", "lineGet", "nativeLineId", "lineVectorSize", "lineVectorGet",
            "wordVectorSize", "wordVectorGet", "wordGet", "wordLine", "nativeWordId", "getAdapter", "getScrollState",
            "setScrollState", "onTouchEvent", "onInterceptTouchEvent", "rowPosition", "rowHolder",
            "registerChildListener", "childAttach", "childDetach", "vmWord", "vmLine", "seek", "seekItem",
            "nativeLineBegin", "nativeLineEnd", "mediaBrowser", "mediaPosition", "wordAnimationTag")
        private val adapterRoles = listOf("Commit", "Snapshot", "Map", "Type", "Id", "Reader", "Token", "Document", "Bind", "Payload", "Recycle")
        val nativeRoles = listOf("nativeLine", "nativeWord", "nativeBackground", "nativePronunciation", "nativePronunciationBackground")
        val wordRoles = listOf("wordProgress")
        private val commonFields = setOf("binding", "recycler", "activeAdapter", "viewModel", "holderView", "holderId", "holderType",
            "wordLineId", "wordAnimator", "childListeners", "words", "backgroundWords",
            "pronunciationWords", "pronunciationBackgroundWords", "instrumentalPosition", "wordInstrumentalPosition")
        private val inventory = FragmentMemberRequirements(
            methods = commonMethods + adapterRoles.flatMap { listOf("line$it", "word$it") } + nativeRoles +
                wordRoles.flatMap { listOf("${it}Start", "${it}End") },
            fields = commonFields + nativeRoles + wordRoles.map { "${it}Model" },
        )
    }

    private val names = profile.document.getJSONObject("lyricBlur")
    private val members = FragmentProfileMembers(loader, names, "lyricBlur", inventory)
    val fragment: Class<*> = type("fragment")
    val lineAdapter: Class<*> = type("lineAdapter")
    val wordAdapter: Class<*> = type("wordAdapter")
    val wordHolder: Class<*> = type("wordHolder")
    val wordModel: Class<*> = type("wordModel")
    val recyclerClass: Class<*> = type("recycler")
    val childListener: Class<*> = method("childAttach").declaringClass
    private val taggedAnimator: Class<*> = type("taggedAnimator")
    val wordMaps: List<Pair<LyricWordSource, Field>> = listOf(
        LyricWordSource.WORD to field("words"), LyricWordSource.BACKGROUND to field("backgroundWords"),
        LyricWordSource.PRONUNCIATION to field("pronunciationWords"),
        LyricWordSource.PRONUNCIATION_BACKGROUND to field("pronunciationBackgroundWords"),
    )

    init {
        check(profile.productionEnabled && names.getInt("contractVersion") == 1)
        check(type("adapterBase").isAssignableFrom(lineAdapter) && type("adapterBase").isAssignableFrom(wordAdapter))
        check(ViewGroup::class.java.isAssignableFrom(recyclerClass))
        check(field("recycler").declaringClass == field("binding").type)
        check(field("activeAdapter").type == type("adapterBase"))
        check(method("registerChildListener").parameterTypes.contentEquals(arrayOf(childListener)) && childListener.isInterface)
        check(field("wordLineId").type == Int::class.javaPrimitiveType)
        check(field("wordAnimator").type == Animator::class.java)
        check(wordMaps.all { it.second.declaringClass == wordHolder && Map::class.java.isAssignableFrom(it.second.type) })
        check(wordRoles.all { field("${it}Model").type == wordModel })
        listOf("line", "word").forEach { prefix ->
            val id = method("${prefix}Id")
            check(id.declaringClass.isAssignableFrom(if (prefix == "line") lineAdapter else wordAdapter) &&
                id.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                id.returnType == Long::class.javaPrimitiveType)
            val nativeId = method(if (prefix == "line") "nativeLineId" else "nativeWordId")
            check(nativeId.declaringClass == method("${prefix}Get").returnType &&
                nativeId.parameterTypes.isEmpty() && nativeId.returnType == Int::class.javaPrimitiveType)
        }
        listOf("nativeLineBegin", "nativeLineEnd").forEach { role ->
            val timing = method(role)
            check(timing.declaringClass.isAssignableFrom(method("lineGet").returnType) &&
                timing.parameterTypes.isEmpty() && timing.returnType == Int::class.javaPrimitiveType)
        }
        val tag = method("wordAnimationTag")
        check(taggedAnimator.isInterface && tag.declaringClass.isAssignableFrom(taggedAnimator) &&
            tag.parameterTypes.isEmpty() && tag.returnType == Any::class.java)
        val browserClass = type("mediaBrowser")
        val browser = method("mediaBrowser")
        val position = method("mediaPosition")
        check(browser.declaringClass.isAssignableFrom(fragment) && browser.parameterTypes.isEmpty() &&
            browser.returnType.isAssignableFrom(browserClass) && position.declaringClass.isAssignableFrom(browserClass) &&
            position.parameterTypes.isEmpty() && position.returnType == Long::class.javaPrimitiveType)
        val rowTypes = names.getJSONObject("rowTypes")
        check(rowTypes.getInt("line") == 0 && rowTypes.getInt("word") == 3 &&
            rowTypes.getInt("instrumental") == 2 && rowTypes.getInt("credits") == 1)
    }

    private fun type(role: String): Class<*> = members.type(names.getJSONObject("classes").getString(role))
    fun method(role: String): Method = members.method(role)
    fun field(role: String): Field = members.field(role)
    fun adapterMethod(adapter: Any, suffix: String): Method = method(
        if (wordAdapter.isInstance(adapter)) "word$suffix" else "line$suffix")
    fun isAdapter(value: Any?): Boolean = value != null && (lineAdapter.isInstance(value) || wordAdapter.isInstance(value))

    fun isWordMovement(animation: Animator): Boolean = taggedAnimator.isInstance(animation) &&
        method("wordAnimationTag").invoke(animation) == "KARAOKE_WORD_LIFT_TAG"

    fun decodeVector(vector: Any, word: Boolean): LyricDecodeResult<Set<Int>> {
        val values = linkedSetOf<Int>()
        var failure: String? = null
        return try {
            val prefix = if (word) "wordVector" else "lineVector"
            val size = (method("${prefix}Size").invoke(vector) as Number).toLong()
            check(size in 0..Int.MAX_VALUE.toLong()) { "Invalid vector size" }
            for (index in 0 until size) {
                runCatching {
                    val pointer = checkNotNull(method("${prefix}Get").invoke(vector, index))
                    val line = if (word) {
                        val value = checkNotNull(method("wordGet").invoke(pointer))
                        val linePointer = checkNotNull(method("wordLine").invoke(value))
                        checkNotNull(method("lineGet").invoke(linePointer))
                    } else checkNotNull(method("lineGet").invoke(pointer))
                    values += (method("nativeLineId").invoke(line) as Number).toInt()
                }.onFailure { failure = it.javaClass.simpleName }
            }
            failure?.let { LyricDecodeResult.Partial(values.toSet(), it) }
                ?: LyricDecodeResult.Complete(values.toSet())
        } catch (error: Exception) {
            LyricDecodeResult.Failed(error.javaClass.simpleName)
        }
    }

    fun holderView(holder: Any): View? = field("holderView").get(holder) as? View
}
