package dev.amenhancer.module.hook

import android.view.Menu
import android.view.View

/** Semantic region names; APK resource and member names live only in the host profile. */
enum class ChromeResource {
    BACKGROUND_LAYERS,
    PLAYER_TOP_SHADOW,
    ARTWORK_CONTAINER,
    BOTTOM_NAVIGATION,
    BOTTOM_NAVIGATION_ROOT_FLAT,
    BOTTOM_NAVIGATION_ROOT_STACKED,
    BOTTOM_NAVIGATION_TABS_FRAME,
    COLOR_PRIMARY,
    DIVIDER,
    FULLPLAYER_SONG_IMAGE,
    MINI_PLAYER,
    MINI_PLAYER_CONTENT,
    MINI_PLAYER_NEXT_BTN,
    MINI_PLAYER_PLAY_BTN,
    MINI_PLAYER_TOUCH_PANEL,
    MINIPLAYER_HEIGHT,
    MOTION_SWITCHER,
    NAV_TABS_TOP_SHADOW,
    NAVIGATION_HOST_GROUP,
    NAVIGATION_TABS_DIVIDER,
    NAVIGATION_TABS_HEIGHT,
    PLAYER_CONTAINER,
    PLAYER_FRAGMENTS_HOST,
    PLAYER_ROOT,
    PLAYER_SHEET_CONTAINER,
    SHADOW_HEIGHT,
    VIDEO_SURFACE_CONTAINER,
}
data class NativeSheetSnapshot(val state: Int, val collapsedTop: Int, val expandedTop: Int)
data class NativeNavigationSnapshot(val menu: Menu, val selectedId: Int)
interface ChromeHostBinding : PlayerSurfacePort, AutoCloseable {
    fun resourceId(role: ChromeResource): Int
    fun find(role: ChromeResource): View?
    fun dimension(role: ChromeResource): Int
    fun invalidateViews()
    fun playerBehavior(preferPlayerRuntime: Boolean): Any?
    fun sheetSnapshot(owner: Any): NativeSheetSnapshot
    fun writePeek(owner: Any, height: Int)
    fun navigation(view: View): NativeNavigationSnapshot
    fun selectNavigation(view: View, id: Int)
    fun isScrollContainer(view: View): Boolean
    fun isComposeScene(view: View): Boolean
    fun isPagerPageHost(view: View): Boolean
}
