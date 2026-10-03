package dev.amenhancer.module.hook

import android.view.View

/** Semantic native regions for the reference tablet session. Host IDs stay in the adapter. */
enum class TabletChromeRegion { NAVIGATION, NAVIGATION_MATERIAL, MINI, MINI_MATERIAL, BACKDROP, SHEET, PLAYER }

interface FragmentTabletChromePort {
    fun view(region: TabletChromeRegion): View?
    fun replaceNavigation(ready: Boolean)
    fun replaceMini(ready: Boolean)
    fun playerLayers(frame: FragmentTabletGlassFrame, slide: Float)
    fun allowOverflow()
    fun transformMini(sx: Float, sy: Float, x: Float, y: Float)
    fun opacity(view: View): Float
    fun navigationOpacity(): Float
    fun restore()
}
