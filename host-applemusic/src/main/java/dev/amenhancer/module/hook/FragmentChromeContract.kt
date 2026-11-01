package dev.amenhancer.module.hook

import org.json.JSONObject

/** The complete native chrome inventory is resolved before any callback is registered. */
internal class FragmentChromeContract(loader: ClassLoader, val names: JSONObject) {
    private val members = FragmentProfileMembers(loader, names.getJSONObject("members"), "fragmentChrome.members",
        FragmentChromeInventory.required)
    val content = members.type(names.getString("contentClass"))
    val player = members.type(names.getString("playerClass"))
    val model = members.type(names.getString("navigationModelClass"))
    val kind = members.type(names.getString("navigationKindClass"))
    val renderTab = members.method("renderTab")
    val viewModel = members.method("navigationViewModel")
    val modelKind = members.field("modelKind")
    val kindId = members.method("kindId")
    val kindLabel = members.method("kindLabel")
    val kindIcon = members.method("kindIcon")
    val invokeCallback = members.method("invokeCallback")
    val slide = members.method("slide")
    val slidePlayer = members.field("slidePlayer")
    val progress = members.method("progress")
    val playerBehavior = members.field("playerBehavior")
    val behaviorState = members.field("behaviorState")
    val playerSlide = members.field("playerSlide")
    val slideProgress = members.field("slideProgress")
    val playerOf = members.method("playerOf")
    val libraryModel = members.method("libraryViewModel")
    val libraryTitle = members.method("libraryTitle")
    val drawerOf = members.method("drawerOf")
    val drawerOpen = members.method("drawerOpen")
    val nativeMenuListener = members.field("menuListener")
    val selectMenuItem = members.method("selectMenuItem")
    val activityTouch = members.method("activityTouch")
    val tabletMiniMargins = members.method("miniMargins")
    val blurDraw = members.method("blurDraw")
    val activityOf = members.method("getActivity")
    val createView = members.method("createView")
    val viewCreated = members.method("viewCreated")
    val destroyView = members.method("destroyView")
    val view = members.method("getView")
    val selectedTab = members.method("selectedTab")
    val navigationTabs = members.method("navigationTabs")
    val liveDataValue = members.method("liveDataValue")
    val navigationMenu = members.method("navigationMenu")
    val navigationSelected = members.method("navigationSelected")
    val navigationSelect = members.method("navigationSelect")
    val drawerMenu = members.method("drawerMenu")
    val collapsedSheetTop = members.field("collapsedSheetTop")
    val peek = members.field("peek")
    val autoPeek = members.field("autoPeek")
    val contentBackdropNode = if (names.getJSONObject("members").getJSONObject("fields").has("contentBackdropNode"))
        members.field("contentBackdropNode") else null
    val expandedSheetTop = members.method("expandedSheetTop")
    val writePeek = members.method("writePeek")
    val settleSheet = members.method("settleSheet")
    val refreshCollapsedOffset = members.method("refreshCollapsedOffset")
    val browser = members.method("browser")
    val currentItem = members.method("currentItem")
    val accountReady = members.method("accountReady")
    val miniVisible = members.method("miniVisible")
    val resources = names.getJSONObject("resources")

    init {
        // Descriptors must also fit the role's caller, not merely exist somewhere in the APK.
        fun shape(role: String, returns: Class<*>, vararg parameters: Class<*>, static: Boolean = false) {
            val method = members.method(role)
            check(method.returnType == returns && method.parameterTypes.contentEquals(parameters) &&
                java.lang.reflect.Modifier.isStatic(method.modifiers) == static) { "Chrome role signature mismatch: $role" }
        }
        val integer = Integer.TYPE
        val floating = java.lang.Float.TYPE
        val boolean = java.lang.Boolean.TYPE
        val void = Void.TYPE
        shape("renderTab", void, model, java.lang.Long.TYPE, members.type(names.getString("navigationCallbackClass")),
            members.type(names.getString("composerClass")), integer)
        shape("kindId", integer); shape("kindIcon", integer); shape("kindLabel", String::class.java)
        shape("invokeCallback", Any::class.java, Any::class.java)
        shape("slide", void, android.view.View::class.java, floating)
        shape("progress", void, floating)
        shape("playerOf", player)
        shape("selectedTab", kind)
        shape("liveDataValue", Any::class.java)
        shape("drawerOpen", void)
        shape("selectMenuItem", void, android.view.MenuItem::class.java)
        shape("miniMargins", void, android.view.View::class.java, floating, floating, static = true)
        shape("blurDraw", void, android.graphics.Canvas::class.java)
        shape("createView", android.view.View::class.java, android.view.LayoutInflater::class.java,
            android.view.ViewGroup::class.java, android.os.Bundle::class.java)
        shape("viewCreated", void, android.view.View::class.java, android.os.Bundle::class.java)
        shape("destroyView", void); shape("getView", android.view.View::class.java)
        shape("navigationMenu", android.view.Menu::class.java); shape("drawerMenu", android.view.Menu::class.java)
        shape("navigationSelected", integer); shape("navigationSelect", void, integer)
        shape("expandedSheetTop", integer); shape("writePeek", void, integer, boolean)
        shape("settleSheet", void, android.view.View::class.java, integer, boolean)
        shape("refreshCollapsedOffset", void)
        shape("accountReady", boolean, static = true)
        shape("activityTouch", boolean, android.view.MotionEvent::class.java)
        val staticMini = java.lang.reflect.Modifier.isStatic(miniVisible.modifiers)
        if (staticMini) shape("miniVisible", void, content, boolean, static = true)
        else shape("miniVisible", void, boolean)
        check(android.app.Activity::class.java.isAssignableFrom(activityOf.returnType)) { "Chrome activity getter mismatch" }
        check(liveDataValue.declaringClass.isAssignableFrom(navigationTabs.returnType) &&
            liveDataValue.declaringClass.isAssignableFrom(libraryTitle.returnType)) { "Chrome LiveData roles mismatch" }
        check(navigationTabs.declaringClass.isAssignableFrom(viewModel.returnType) &&
            libraryTitle.declaringClass.isAssignableFrom(libraryModel.returnType) &&
            drawerOpen.declaringClass.isAssignableFrom(drawerOf.returnType) &&
            currentItem.declaringClass.isAssignableFrom(browser.returnType)) { "Chrome getter receiver mismatch" }
        check(modelKind.type == kind && slidePlayer.type == player && playerSlide.type == slide.declaringClass &&
            progress.declaringClass == slide.declaringClass &&
            selectMenuItem.declaringClass.isAssignableFrom(nativeMenuListener.type)) { "Chrome owner fields mismatch" }
        listOf(behaviorState, collapsedSheetTop, peek).forEach { check(it.type == integer) }
        check(autoPeek.type == boolean && slideProgress.type == floating)
        contentBackdropNode?.let {
            check(it.type.name == "android.graphics.RenderNode" &&
                android.view.ViewGroup::class.java.isAssignableFrom(it.declaringClass)) { "Chrome content recording field mismatch" }
        }
        check(playerBehavior.type == members.type("com.google.android.material.bottomsheet.BottomSheetBehavior"))
        if (names.optJSONObject("phone")?.optJSONObject("behavior")?.optString("collapsedPeekMode") == "offset") {
            check(listOf(behaviorState, collapsedSheetTop, peek, autoPeek).all {
                it.declaringClass.isAssignableFrom(playerBehavior.type)
            } && listOf(writePeek, refreshCollapsedOffset).all {
                it.declaringClass.isAssignableFrom(playerBehavior.type)
            }) { "Chrome collapsed peek receiver mismatch" }
        }
        listOf(viewModel, libraryModel, libraryTitle, drawerOf, navigationTabs, activityOf, browser, currentItem).forEach {
            check(!java.lang.reflect.Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType != void)
        }
    }
}

internal object FragmentChromeInventory {
    val required = FragmentMemberRequirements(
        methods = setOf("renderTab", "navigationViewModel", "kindId", "kindLabel", "kindIcon", "invokeCallback",
            "slide", "progress", "playerOf", "libraryViewModel", "libraryTitle", "drawerOf", "drawerOpen",
            "selectMenuItem", "miniMargins", "blurDraw", "getActivity", "createView", "viewCreated", "destroyView",
            "getView", "selectedTab", "navigationTabs", "liveDataValue", "navigationMenu", "navigationSelected",
            "navigationSelect", "drawerMenu", "expandedSheetTop", "writePeek", "browser", "currentItem",
            "accountReady", "miniVisible", "activityTouch", "settleSheet", "refreshCollapsedOffset"),
        fields = setOf("modelKind", "slidePlayer", "playerBehavior", "behaviorState", "playerSlide", "slideProgress",
            "menuListener", "collapsedSheetTop", "peek", "autoPeek"),
    )
}
