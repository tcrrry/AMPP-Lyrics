package dev.amenhancer.module.hook
import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import dev.amenhancer.module.ui.EmbeddedHostActivityRole

interface SettingsActivityMatcher {
    fun roleFor(activity: Activity): EmbeddedHostActivityRole?
    fun isPlayerActivity(activity: Activity): Boolean
    fun isMainContentActivity(activity: Activity): Boolean
}
interface SettingsViewBridge {
    /** Compose-native settings must never install the legacy decor/title-scan fallback. */
    val supportsViewFallback: Boolean get() = true
    fun fragmentView(fragment: Any): ViewGroup?
    fun findSettingsListOverlayContainer(root: View): ViewGroup?
    fun injectNativeSettingsPreference(fragment: Any, activity: Activity): Boolean
    fun findSettingsInsertionContainer(root: ViewGroup): ViewGroup?
    fun refreshNativePreferenceAdapter(root: ViewGroup?)
}
interface SettingsEntryObserver {
    fun onSettingsPreferencesReady(fragment: Any, activity: Activity)
    fun onSettingsFragmentViewCreated(fragment: Any, activity: Activity, view: View?)
    fun onSettingsFragmentResumed(fragment: Any, activity: Activity)
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean
}
