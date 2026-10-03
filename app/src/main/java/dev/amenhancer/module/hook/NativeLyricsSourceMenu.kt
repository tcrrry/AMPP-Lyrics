package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import dev.amenhancer.module.ModuleConstants
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Extends only Apple's lyrics translations popup; native auxiliary rows remain owned by Apple. */
internal object NativeLyricsSourceMenu {
    private const val ROW_TAG = "tcrrry_native_lyrics_source_row"
    private const val DIVIDER_TAG = "tcrrry_native_lyrics_source_divider"
    private val anchors = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    internal fun registerAnchor(button: View) {
        anchors[button] = true
        button.visibility = View.VISIBLE
    }

    internal fun unregisterAnchor(button: View) { anchors.remove(button); anchorPages.remove(button) }

    internal fun requestedVisibility(view: Any?, requested: Int): Int =
        if (anchors.containsKey(view)) View.VISIBLE else requested

    private val anchorPages = Collections.synchronizedMap(WeakHashMap<View, WeakReference<Any>>())

    internal fun registerPage(button: View, fragment: Any) {
        anchorPages[button] = WeakReference(fragment)
    }

    internal fun publishAnchorPage(button: View) {
        anchorPages[button]?.get()?.let(CurrentLyricsSourceStatus::rememberVisiblePage)
    }

    fun install(loader: ClassLoader, currentId: () -> Long?, openSettings: (Activity) -> Unit, build: TargetBuild = TargetBuild.UNKNOWN) {
        NativeLyricsPronunciation.install(loader)
        NativeLyricsPronunciationSubtitle.install(loader, build)
        val fragment = loader.loadClass("com.apple.android.music.player.fragment.PlayerLyricsViewFragment")
        fun bind(root: View?, fragment: Any?) {
            root ?: return
            val id = root.resources.getIdentifier("translations_button", "id", ModuleConstants.RESOURCE_PACKAGE)
            val button = root.findViewById<View>(id) ?: return
            registerAnchor(button)
            if (fragment != null) {
                registerPage(button, fragment)
                publishAnchorPage(button)
            }
        }
        // Apple Music calls ImageView's override directly. Hook that entry too:
        // an optimized super-call need not pass through the View method hook.
        for (type in listOf(View::class.java, ImageView::class.java)) {
            ModernXposedRuntime.hookMethod(type.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType),
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = requestedVisibility(param.thisObject, param.args[0] as Int)
                    }
                })
        }
        ModernXposedRuntime.hookAllMethods(fragment, "onCreateView", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { bind(param.result as? View, param.thisObject) }
        })
        ModernXposedRuntime.hookAllMethods(fragment, "onResume", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                bind(runCatching { param.thisObject?.let { ModernXposedRuntime.callMethod(it, "getView") as? View } }.getOrNull(), param.thisObject)
            }
        })
        ModernXposedRuntime.hookAllMethods(fragment, "onDestroyView", object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val root = runCatching { param.thisObject?.let { ModernXposedRuntime.callMethod(it, "getView") as? View } }.getOrNull()
                    ?: return
                val id = root.resources.getIdentifier("translations_button", "id", ModuleConstants.RESOURCE_PACKAGE)
                root.findViewById<View>(id)?.let(::unregisterAnchor)
            }
        })
        ModernXposedRuntime.hookMethod(PopupWindow::class.java.getDeclaredMethod("showAsDropDown",
            View::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val anchor = param.args.firstOrNull() as? View ?: return
                    if (!anchors.containsKey(anchor)) return
                    val popup = param.thisObject as? PopupWindow ?: return
                    val menu = popup.contentView as? LinearLayout ?: return
                    val id = menu.resources.getIdentifier("translations_popup_menu", "id", ModuleConstants.RESOURCE_PACKAGE)
                    if (id == 0 || menu.id != id) return
                    runCatching {
                        publishAnchorPage(anchor)
                        appendRow(menu, popup, currentId, openSettings)
                    }
                        .onFailure { ModernXposedRuntime.log("lyrics source menu extension failed open", it) }
                }
            })
        ModernXposedRuntime.log("native lyrics source menu installed")
    }

    private fun appendRow(menu: LinearLayout, popup: PopupWindow, currentId: () -> Long?, openSettings: (Activity) -> Unit) {
        menu.findViewWithTag<View>(ROW_TAG)?.let { menu.removeView(it) }
        menu.findViewWithTag<View>(DIVIDER_TAG)?.let { menu.removeView(it) }
        val context = menu.context
        val app = context.applicationContext
        val songId = currentId() ?: 0L
        val popupRef = WeakReference(popup)
        fun applied() = CurrentLyricsSourceStatus.appliedSource(app, songId)
        fun toast(message: String) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        fun valid(): Boolean {
            if (LyricsSourceMenuPolicy.canAct(songId, currentId())) return true
            toast("歌曲已切换或尚未加载，请重新打开菜单")
            popupRef.get()?.dismiss()
            return false
        }
        fun refresh(source: String) {
            val started = CurrentLyricsSourceStatus.refresh(songId)
            toast(if (started) "正在匹配 · $source" else "请重新打开歌词页后生效")
            popupRef.get()?.dismiss()
        }
        val caption = if (songId > 0) LyricsSourceMenuPolicy.caption(applied()).substringBefore('／') else "暂无歌曲"
        val row = NativeLyricsMenuRowLayout(context, caption).apply {
            tag = ROW_TAG
            isClickable = true
            isFocusable = true
            contentDescription = "歌词源：${LyricsSourceMenuPolicy.caption(applied())}。单击切换，双击重新匹配，长按设置"
        }
        row.setOnClickListener {
            if (!valid()) return@setOnClickListener
            val next = LyricsSourceMenuPolicy.next(applied(), CurrentLyricsSourceStatus.selectedSource(app, songId))
            CurrentLyricsSourceStatus.selectSource(app, songId, next)
            refresh(next)
        }
        row.setOnLongClickListener {
            popupRef.get()?.dismiss()
            findActivity(context)?.let(openSettings) ?: toast("暂时无法打开歌词设置")
            true
        }
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Confirmation arrives only when Android has ruled out a double tap.
                if (row.isAttachedToWindow && popupRef.get()?.isShowing == true) row.performClick()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!valid()) return true
                val source = LyricsSourceMenuPolicy.provider(applied())
                    ?: CurrentLyricsSourceStatus.selectedSource(app, songId)?.takeIf { it in LyricsSourceMenuPolicy.sources }
                if (source == null) { toast("请先单击选择歌词源"); return true }
                // Reuse the existing management page's exclude-and-rematch semantics.
                CurrentLyricsSourceStatus.selectSource(app, songId, source)
                CurrentLyricsSourceStatus.excludeCurrentRecord(app, songId)
                refresh(source)
                return true
            }
            override fun onLongPress(e: MotionEvent) { row.performLongClick() }
        })
        row.setOnTouchListener { _, event -> detector.onTouchEvent(event); true }
        menu.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        repeat(16) {
            if (current is Activity) return current
            val next = (current as? ContextWrapper)?.baseContext ?: return null
            if (next === current) return null
            current = next
        }
        return null
    }
}
