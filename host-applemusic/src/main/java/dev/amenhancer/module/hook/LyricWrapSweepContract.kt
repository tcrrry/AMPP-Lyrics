package dev.amenhancer.module.hook

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.host.applemusic.AppleMusicHostProfile
import org.json.JSONObject

/** Dependencies are code-owned and resolved once, before any callback is activated. */
internal class LyricWrapSweepContract(profile: AppleMusicHostProfile, loader: ClassLoader, names: JSONObject) {
    private val members = FragmentProfileMembers(loader, names, "lyricWrapSweep", FragmentMemberRequirements(
        methods = setOf("build", "update", "rushUpdate", "fadeUpdate", "staticMask", "rebind", "bind", "recycle", "document",
            "clearMasks", "textView", "maskUpdate", "animationTag", "mediaBrowser", "mediaPosition",
            "seek", "pause", "pauseRequested", "resume"),
        fields = setOf("topMasks", "bottomMasks", "maskFirst", "maskLast", "wordBegin", "wordDuration",
            "bindingRoot", "holderBinding", "adapterCallback", "callbackFragment", "featherPixels",
            "listenerMask", "listenerColors", "listenerFeather", "listenerRtl", "wordText", "wordAnimator",
            "words", "backgroundWords", "pronunciationWords", "pronunciationBackgroundWords",
            "foregroundInactiveColors", "backgroundInactiveColors", "fadeMask"),
    ))
    val build = members.method("build")
    val update = members.method("update")
    val rushUpdate = members.method("rushUpdate")
    val fadeUpdate = members.method("fadeUpdate")
    val staticMask = members.method("staticMask")
    val rebind = members.method("rebind")
    val bind = members.method("bind")
    val recycle = members.method("recycle")
    val document = members.method("document")
    val clearMasks = members.method("clearMasks")
    val textView = members.method("textView")
    val maskUpdate = members.method("maskUpdate")
    val animationTag = members.method("animationTag")
    val mediaBrowser = members.method("mediaBrowser")
    val mediaPosition = members.method("mediaPosition")
    val seek = members.method("seek")
    val pause = members.method("pause")
    val pauseRequested = members.method("pauseRequested")
    val resume = members.method("resume")
    val topMasks = members.field("topMasks")
    val bottomMasks = members.field("bottomMasks")
    val maskFirst = members.field("maskFirst")
    val maskLast = members.field("maskLast")
    val wordBegin = members.field("wordBegin")
    val wordDuration = members.field("wordDuration")
    val bindingRoot = members.field("bindingRoot")
    val holderBinding = members.field("holderBinding")
    val adapterCallback = members.field("adapterCallback")
    val callbackFragment = members.field("callbackFragment")
    val featherPixels = members.field("featherPixels")
    val listenerMask = members.field("listenerMask")
    val listenerColors = members.field("listenerColors")
    val listenerFeather = members.field("listenerFeather")
    val listenerRtl = members.field("listenerRtl")
    val wordText = members.field("wordText")
    val wordAnimator = members.field("wordAnimator")
    val foregroundInactiveColors = members.field("foregroundInactiveColors")
    val backgroundInactiveColors = members.field("backgroundInactiveColors")
    val fadeMask = members.field("fadeMask")
    val wordMaps = listOf("words", "backgroundWords", "pronunciationWords", "pronunciationBackgroundWords").map(members::field)

    init {
        check(profile.productionEnabled && names.getInt("contractVersion") == 1)
        check(build.declaringClass == rebind.declaringClass && build.declaringClass == adapterCallback.declaringClass)
        check(build.parameterTypes[2] == wordBegin.declaringClass && wordBegin.declaringClass == wordDuration.declaringClass)
        check(ViewGroup::class.java.isAssignableFrom(clearMasks.declaringClass))
        check(topMasks.declaringClass == clearMasks.declaringClass && bottomMasks.declaringClass == clearMasks.declaringClass)
        check(maskUpdate.declaringClass == maskFirst.declaringClass && maskFirst.declaringClass == maskLast.declaringClass)
        check(maskUpdate.parameterTypes.contentEquals(arrayOf(IntArray::class.java, FloatArray::class.java,
            java.lang.Float::class.java)))
        check(listenerMask.declaringClass == update.declaringClass && listenerMask.type == maskUpdate.declaringClass)
        check(update.parameterTypes.contentEquals(arrayOf(ValueAnimator::class.java)))
        check(rushUpdate.parameterTypes.contentEquals(arrayOf(ValueAnimator::class.java)))
        check(fadeUpdate.parameterTypes.contentEquals(arrayOf(ValueAnimator::class.java)))
        check(fadeMask.declaringClass == fadeUpdate.declaringClass && fadeMask.type == maskUpdate.declaringClass)
        check(listOf(foregroundInactiveColors, backgroundInactiveColors).all {
            it.declaringClass == build.declaringClass && it.type == IntArray::class.java
        })
        check(wordText.declaringClass == wordBegin.declaringClass && wordAnimator.declaringClass == wordBegin.declaringClass)
        check(wordAnimator.type == android.animation.Animator::class.java)
        check(wordMaps.all { it.declaringClass == rebind.parameterTypes[0] && Map::class.java.isAssignableFrom(it.type) })
        check(View::class.java.isAssignableFrom(bindingRoot.type))
        check(callbackFragment.declaringClass == adapterCallback.type &&
            mediaBrowser.declaringClass.isAssignableFrom(callbackFragment.type))
        check(mediaPosition.declaringClass.isAssignableFrom(mediaBrowser.returnType))
    }

    fun browser(adapter: Any): Any? {
        val callback = adapterCallback.get(adapter) ?: return null
        val fragment = callbackFragment.get(callback) ?: return null
        return mediaBrowser.invoke(fragment)
    }

    fun position(browser: Any): Long? = (mediaPosition.invoke(browser) as? Long)?.takeIf { it >= 0L }

    /** Match the host's W branch, which treats both native inactive palettes as unsung/dim. */
    fun isInactiveStatic(adapter: Any, colors: IntArray): Boolean =
        colors.contentEquals(foregroundInactiveColors.get(adapter) as IntArray) ||
            colors.contentEquals(backgroundInactiveColors.get(adapter) as IntArray)

    fun isRush(animation: Any): Boolean = animationTag.declaringClass.isInstance(animation) &&
        animationTag.invoke(animation) == "KARAOKE_SPLIT_ON_CHARS_LANGUAGES_RUSH_GRADIENT_TAG"
}
