package dev.amenhancer.module.hook
import android.app.Activity
import android.view.View
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.ui.EmbeddedHostActivityRole
import dev.amenhancer.module.ui.EmbeddedSettingsTextPolicy
internal class LegacySettingsActivityMatcher(
    private val playerActivityClass: Class<*>? = null,
    private val playerActivityName: String,
    private val mainName: String,
) : SettingsActivityMatcher {
    override fun roleFor(activity: Activity): EmbeddedHostActivityRole? {
        if (activity.packageName != ModuleConstants.TARGET_PACKAGE) return null
        if (isMainContentActivity(activity)) return EmbeddedHostActivityRole.MainContent
        if (isPlayerActivity(activity)) return EmbeddedHostActivityRole.Player
        if (EmbeddedSettingsTextPolicy.isSettingsClassName(activity.javaClass.name)) {
            return EmbeddedHostActivityRole.Settings
        }
        val decor = activity.window?.decorView
            ?: activity.findViewById<View>(android.R.id.content)
            ?: return null
        return if (EmbeddedSettingsTextPolicy.containsSettingsTitle(decor)) {
            EmbeddedHostActivityRole.Settings
        } else {
            null
        }
    }

    override fun isPlayerActivity(activity: Activity): Boolean {
        if (playerActivityClass?.isAssignableFrom(activity.javaClass) == true) return true
        var current: Class<*>? = activity.javaClass
        while (current != null) {
            if (current.name == playerActivityName) return true
            current = current.superclass
        }
        return false
    }

    override fun isMainContentActivity(activity: Activity): Boolean {
        var current: Class<*>? = activity.javaClass
        while (current != null) {
            if (current.name == mainName) return true
            current = current.superclass
        }
        return false
    }
}

