package dev.amenhancer.module.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import dev.amenhancer.glass.GlassHostForm
import dev.amenhancer.glass.GlassPolicy
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.config.TargetConfigClient
import dev.amenhancer.module.model.FeatureHealth
import dev.amenhancer.module.model.FeatureState
import java.util.WeakHashMap

@RequiresApi(33)
internal object PhoneGlassRuntime {
    private val sessions = WeakHashMap<Activity, GlassSession>()
    private val failed = java.util.Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private var applicationRegistered = false
    private var hooksInstalled = false
    private var hooksAttempted = false

    fun discover(view: View, config: TargetConfigClient) {
        if (!view.isAttachedToWindow) {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { v.removeOnAttachStateChangeListener(this); discover(v, config) }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            return
        }
        val activity = activity(view.context) ?: return
        if (activity in failed || activity.isFinishing || activity.isDestroyed) return
        val build = targetBuild(activity)
        // Both host forms share one seam whitelist; the form itself is routed below.
        if (GlassHostForm.values().none { GlassPolicy.supports(android.os.Build.VERSION.SDK_INT, dev.amenhancer.host.applemusic.AppleMusicHostProfiles.supportsGlass(build.versionCode, build.versionName), it) }) return
        if (!config.settings().phoneLiquidGlassEnabled) return
        registerLifecycle(activity.application)
        view.post {
            if (activity in failed || activity.isDestroyed) return@post
            try {
                installHooks(activity.classLoader, build)
                sessions[activity]?.takeUnless { it.ownsCurrentHierarchy() }?.let { it.close(); sessions.remove(activity) }
                val desired = desiredSessionType(activity, config)
                if (desired == null) { sessions.remove(activity)?.close(); return@post }
                val session = sessions[activity]?.takeIf { it.javaClass == desired }
                    ?: (if (desired == TabletDualPaneGlassSession::class.java)
                        TabletDualPaneGlassSession(activity,config) { error -> fail(activity,config,error) }
                    else PhoneGlassSession(activity,config) { error -> fail(activity,config,error) })
                        .also { sessions.remove(activity)?.close(); sessions[activity] = it }
                session.attachAvailableViews()
            } catch (error: Throwable) { fail(activity, config, error) }
        }
    }

    /** Routes the host form; null means no session may exist for this activity right now. */
    private fun desiredSessionType(activity: Activity, config: TargetConfigClient): Class<out GlassSession>? {
        val build = targetBuild(activity)
        if (config.settings().phoneLiquidGlassEnabled &&
            !TabletModeQualifier.isOfficialTablet(activity) &&
            GlassPolicy.supports(android.os.Build.VERSION.SDK_INT, dev.amenhancer.host.applemusic.AppleMusicHostProfiles.supportsGlass(build.versionCode, build.versionName), GlassHostForm.PhoneStacked)
        ) {
            return PhoneGlassSession::class.java
        }
        if (config.settings().phoneLiquidGlassEnabled &&
            TabletModeQualifier.isEligible(activity) &&
            GlassPolicy.supports(android.os.Build.VERSION.SDK_INT, dev.amenhancer.host.applemusic.AppleMusicHostProfiles.supportsGlass(build.versionCode, build.versionName), GlassHostForm.TabletDualPane)
        ) {
            return TabletDualPaneGlassSession::class.java
        }
        return null
    }

    private fun fail(activity: Activity, config: TargetConfigClient, error: Throwable) {
        failed += activity
        sessions.remove(activity)?.close()
        ModernXposedRuntime.log("liquid glass restored native UI", error)
        config.reportHealth(FeatureHealth(ModuleConstants.FEATURE_PHONE_LIQUID_GLASS, FeatureState.FAILED,
            "玻璃接入失败，已恢复原生界面：${error.javaClass.simpleName}: ${error.message}", targetBuild(activity).displayName))
    }

    private fun installHooks(loader: ClassLoader, build: TargetBuild) {
        if (hooksInstalled) return
        check(!hooksAttempted) { "Glass hook installation previously failed; restart the host to retry" }
        hooksAttempted = true
        AppleMusicHostFactory.installChromeHooks(loader, build, object : ChromeHookObserver {
            override fun onSlide(activity: Activity, progress: Float) { sessions[activity]?.onSlide(progress) }
            override fun onTouch(view: View, event: MotionEvent): Boolean? {
                if (sessions.values.any { it.shouldPassThroughTouch(view,event) }) return false
                sessions.values.firstNotNullOfOrNull { it.dispatchCollapsedMiniTouch(view,event) }?.let { return it }
                sessions.values.firstOrNull { it.miniRoot === view }?.observeTouch(event)
                return null
            }
            override fun bypassIntercept(nativeOwner: Any?, event: MotionEvent): Boolean =
                sessions.values.any { it.playerBehavior === nativeOwner && it.shouldBypassPlayerIntercept(event) }
            override fun replacementPeek(nativeOwner: Any?, originalHeight: Int): Int? {
                val session=sessions.values.firstOrNull { it.playerBehavior === nativeOwner } ?: return null
                session.observeNativePeek(originalHeight)
                return if (session.activated) session.peekHeight() else null
            }
            override fun onArtworkSlide(artwork: View, progress: Float) {
                sessions.values.forEach { (it as? TabletDualPaneGlassSession)?.alignNativeArtworkStart(artwork,progress) }
            }
            override fun redirectedPadding(view: View?): Int? =
                sessions.values.firstNotNullOfOrNull { it.redirectedPadding(view) }
            override fun redirectedAlpha(view: View?, alpha: Float): Float? =
                sessions.values.firstNotNullOfOrNull { it.redirectedLayerAlpha(view,alpha) }
        })
        hooksInstalled=true
    }

    private fun registerLifecycle(application: Application) {
        if (applicationRegistered) return
        applicationRegistered = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) { sessions[activity]?.foreground(true) }
            override fun onActivityPaused(activity: Activity) { sessions[activity]?.foreground(false) }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) { sessions.remove(activity)?.close(); failed.remove(activity) }
        })
    }

    fun activity(context: Context): Activity? {
        var current = context
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Context, Boolean>())
        while (seen.add(current)) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }

}
