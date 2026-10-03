package dev.amenhancer.module.hook

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import dev.amenhancer.module.hook.ModernMethodHook as XC_MethodHook
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.R
import dev.amenhancer.module.config.TargetConfigClient
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Mirrors the modified APK's landscape resource overlays without replacing
 * the target application's player root. The target owns the bottom-sheet
 * lifecycle; the resource overlays only replace its static constraints.
 */
internal class AppleMusicDualPaneTarget(
    private val symbols: TargetSymbolResolver,
    private val targetBuild: TargetBuild = TargetBuild.UNKNOWN,
) : DualPaneTarget {
    private val anchorResizeListeners = WeakHashMap<View, View.OnLayoutChangeListener>()

    override fun install(): TargetCapabilityInstall {
        val controllerInitializeResolution = symbols.resolve(AppleMusicSymbols.PlayerControllerInitialize)
        val controllerCreateViewResolution = symbols.resolve(AppleMusicSymbols.PlayerControllerCreateView)
        val controllerSelectPaneResolution = symbols.resolve(AppleMusicSymbols.PlayerControllerSelectPane)
        val controllerHooks = installControllerHooks(
            controllerInitializeResolution.valueOrNull(),
            controllerCreateViewResolution.valueOrNull(),
            controllerSelectPaneResolution.valueOrNull(),
        )
        val navigationMenuResolution = symbols.resolve(AppleMusicSymbols.StackedNavigationMenuOnMeasure)
        val navigationMenuMeasureHooks = installStackedBottomNavigationMenuMeasureHook(
            navigationMenuResolution.valueOrNull(),
        )
        val activityResolution = symbols.resolve(AppleMusicSymbols.PlayerActivityCreateStackedNavigationHolder)
        val activityRootResolution = symbols.resolve(AppleMusicSymbols.PlayerActivityRoot)
        val behaviorFieldResolution = symbols.resolve(AppleMusicSymbols.PlayerActivityBehaviorField)
        val chromeHooks = installNativeStackedNavigationHolderHook(
            activityResolution.valueOrNull(),
            activityRootResolution.valueOrNull(),
            behaviorFieldResolution.valueOrNull(),
        )
        val staticCollapsedInterceptApplicable =
            StaticCollapsedInterceptGuard.isSupportedBuild(targetBuild)
        val staticCollapsedInterceptResolution = staticCollapsedInterceptApplicable
            .takeIf { it }
            ?.let { symbols.resolve(AppleMusicSymbols.StaticCollapsedInterceptMethod) }
        val staticCollapsedInterceptHook = if (staticCollapsedInterceptApplicable) {
            StaticCollapsedInterceptGuard.install(staticCollapsedInterceptResolution?.valueOrNull())
        } else {
            true
        }
        val lyricsFragmentResolution = symbols.resolve(AppleMusicSymbols.LyricsFragment)
        val lyricsFragmentClass = lyricsFragmentResolution.valueOrNull()
        val lyricsChromeResolution = symbols.resolve(AppleMusicSymbols.LyricsChromeAnimate)
        val lyricsChromeHooks = installLandscapeLyricsChromeHook(
            lyricsChromeResolution.valueOrNull(),
            lyricsFragmentClass,
        )
        val lyricsMetricsResolution = symbols.resolve(AppleMusicSymbols.LyricsFragmentUpdateMetrics)
        val lyricsMetricsHooks = installLandscapeLyricsMetricsHook(lyricsMetricsResolution.valueOrNull())
        val lyricsTypographyResolution = symbols.resolve(AppleMusicSymbols.LyricsFragmentOnResume)
        val lyricsTypographyHooks = installLandscapeLyricsTypographyHook(
            lyricsTypographyResolution.valueOrNull(),
        )
        val failures = listOfNotNull(
            controllerInitializeResolution,
            controllerCreateViewResolution,
            controllerSelectPaneResolution,
            navigationMenuResolution,
            activityResolution,
            activityRootResolution,
            behaviorFieldResolution,
            staticCollapsedInterceptResolution,
            lyricsFragmentResolution,
            lyricsChromeResolution,
            lyricsMetricsResolution,
            lyricsTypographyResolution,
        ).filterNot { it is TargetResolution.Found<*> }
        val failureSummary = failures.takeIf(List<*>::isNotEmpty)
            ?.joinToString(prefix = "; ") { it.summary }
            .orEmpty()
        if (
            controllerHooks != 3 ||
            navigationMenuMeasureHooks == 0 ||
            chromeHooks == 0 ||
            !staticCollapsedInterceptHook ||
            lyricsChromeHooks == 0 ||
            lyricsMetricsHooks == 0 ||
            lyricsTypographyHooks == 0 ||
            failures.isNotEmpty()
        ) {
            val staticInterceptStatus = when {
                !staticCollapsedInterceptApplicable -> "skipped"
                staticCollapsedInterceptHook -> "1"
                else -> "0"
            }
            return TargetCapabilityInstall.Degraded(
                "Installed controller=$controllerHooks navigationMeasure=$navigationMenuMeasureHooks " +
                    "chrome=$chromeHooks lyricsChrome=$lyricsChromeHooks " +
                    "lyricsMetrics=$lyricsMetricsHooks " +
                    "lyricsTypography=$lyricsTypographyHooks " +
                    "staticIntercept=$staticInterceptStatus hook(s)" +
                    failureSummary,
            )
        }
        return TargetCapabilityInstall.Active(
            "Installed player controller and chrome hooks; " +
                "stackedNavigationMenuMeasure=$navigationMenuMeasureHooks; " +
                controllerInitializeResolution.summary,
        )
    }

    /**
     * The modified resource XML gives the Material menu its 56dp height on
     * its first measure. At the resource-hook boundary this app has already
     * constructed the menu from the stock flat XML, so its direct child keeps
     * the cached 40dp spec even when the public parent becomes full-width.
     * Feed the exact same 56dp height into that direct menu on every measure.
     */
    private fun installStackedBottomNavigationMenuMeasureHook(onMeasure: Method?): Int {
        onMeasure ?: return 0
        return if (hook(onMeasure, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val menu = param.thisObject as? View ?: return
                    val navigation = menu.parent as? View ?: return
                    if (!TabletModeQualifier.isEligible(navigation.context)) return
                    val resources = navigation.resources
                    val bottomNavigationId = resources.getIdentifier(
                        "bottom_navigation",
                        "id",
                        ModuleConstants.RESOURCE_PACKAGE,
                    )
                    if (bottomNavigationId == 0) return
                    if (navigation.id != bottomNavigationId) return
                    val tabsHeightId = resources.getIdentifier(
                        "navigation_tabs_height",
                        "dimen",
                        ModuleConstants.RESOURCE_PACKAGE,
                    )
                    if (tabsHeightId == 0) return
                    val tabsHeight = resources.getDimensionPixelSize(tabsHeightId)
                    param.args[1] = View.MeasureSpec.makeMeasureSpec(
                        tabsHeight,
                        View.MeasureSpec.EXACTLY,
                    )
                }
            })
        ) {
            1
        } else {
            0
        }
    }

    /**
     * The modified APK obtains its 35sp value through a w640dp resource.
     * Our module leaves target resources untouched, so install the equivalent
     * row-scoped runtime typography when the lyrics view has resumed. This is
     * owned by dual-pane rather than future-blur: disabling blur must not
     * shrink the tablet player back to the stock 24sp size.
     */
    private fun installLandscapeLyricsTypographyHook(onResume: Method?): Int {
        onResume ?: return 0
        return if (hook(onResume, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let { fragment ->
                        installHighlightAnchorResizeSync(fragment)
                        TabletLyricTypography.attach(fragment)
                    }
                }
            })
        ) {
            1
        } else {
            0
        }
    }

    /**
     * Mirrors the modified e.a2(int, int[]) prefix exactly: on the dedicated
     * lyrics fragment in tablet landscape, hide f2() and skip the stock
     * chrome animation. This removes the duplicate controls from the right
     * lyrics pane without touching the left player pane.
     */
    private fun installLandscapeLyricsChromeHook(
        animateChrome: Method?,
        lyricsFragmentClass: Class<*>?,
    ): Int {
        animateChrome ?: return 0
        lyricsFragmentClass ?: return 0
        return if (hook(animateChrome, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val fragment = param.thisObject ?: return
                    if (!lyricsFragmentClass.isInstance(fragment)) return
                    val chrome = runCatching {
                        ModernXposedRuntime.callMethod(fragment, "f2") as? View
                    }.getOrNull() ?: return
                    if (!TabletModeQualifier.isEligible(chrome.context)) return
                    chrome.visibility = View.GONE
                    param.result = null
                    dualPaneDebug("suppressed duplicate lyrics pane chrome")
                }
            })
        ) {
            1
        } else {
            0
        }
    }

    /**
     * Mirrors the landscape-only tail inserted into PlayerLyricsViewFragment
     * j2(): stock code stores round(anchor) + f2().height into both x.c
     * bounds, while the modified APK stores round(anchor). Let stock code do
     * all of its normal calculations, then remove the same control height and
     * refresh the same RecyclerView it refreshes in j2().
     */
    private fun installLandscapeLyricsMetricsHook(updateMetrics: Method?): Int {
        updateMetrics ?: return 0
        return if (hook(updateMetrics, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val fragment = param.thisObject ?: return
                    val controls = runCatching {
                        ModernXposedRuntime.callMethod(fragment, "f2") as? View
                    }.getOrNull() ?: return
                    if (!TabletModeQualifier.isEligible(controls.context)) return
                    val profile = LyricsLayoutFieldProfiles.resolve(fragment.javaClass) ?: return
                    installHighlightAnchorResizeSync(fragment)
                    val highlightAnchorAligned = alignSynchronizedLyricsHighlightAnchor(fragment, profile)
                    val controlsHeight = controls.height
                    val endPaddingCorrected = controlsHeight == 0 ||
                        profile.synchronizedMetrics.all { fieldName ->
                            subtractControlsHeightFromLyricsBoundary(fragment, fieldName, controlsHeight)
                        }
                    if (!endPaddingCorrected) return
                    if (!highlightAnchorAligned && controlsHeight == 0) {
                        dualPaneDebug("landscape lyrics metrics unchanged")
                        return
                    }
                    refreshLyricsRecycler(fragment, profile)
                    reapplyVerticalLyricsGradient(fragment, profile)
                    dualPaneDebug(
                        "corrected landscape lyrics metrics highlightAnchorAligned=" +
                            highlightAnchorAligned + " controlsHeight=" + controlsHeight,
                    )
                }
            })
        ) {
            1
        } else {
            0
        }
    }

    private fun installHighlightAnchorResizeSync(fragment: Any) {
        val profile = LyricsLayoutFieldProfiles.resolve(fragment.javaClass) ?: return
        val binding = dualPaneField(fragment.javaClass, profile.binding)?.get(fragment) ?: return
        val container = dualPaneField(binding.javaClass, profile.container)?.get(binding) as? View ?: return
        synchronized(anchorResizeListeners) {
            if (anchorResizeListeners.containsKey(container)) return
            val fragmentReference = WeakReference(fragment)
            val listener = View.OnLayoutChangeListener {
                    changedView,
                    _,
                    top,
                    _,
                    bottom,
                    _,
                    oldTop,
                    _,
                    oldBottom,
                ->
                if (bottom - top == oldBottom - oldTop || bottom <= top) return@OnLayoutChangeListener
                refreshHighlightAnchor(changedView, fragmentReference)
            }
            anchorResizeListeners[container] = listener
            container.addOnLayoutChangeListener(listener)
            refreshHighlightAnchor(container, fragmentReference)
        }
    }

    private fun refreshHighlightAnchor(container: View, fragmentReference: WeakReference<Any>) {
        container.post {
            val currentFragment = fragmentReference.get() ?: return@post
            if (!container.isAttachedToWindow || container.height <= 0) return@post
            if (!TabletModeQualifier.isEligible(container.context)) return@post
            runCatching {
                ModernXposedRuntime.callMethod(currentFragment, "j2")
            }.onFailure {
                dualPaneDebug("lyrics anchor resize sync failed: $it")
            }
        }
    }

    private fun alignSynchronizedLyricsHighlightAnchor(
        fragment: Any,
        profile: LyricsLayoutFieldProfile,
    ): Boolean = runCatching {
        val binding = dualPaneField(fragment.javaClass, profile.binding)?.get(fragment)
            ?: return@runCatching false
        val container = dualPaneField(binding.javaClass, profile.container)?.get(binding) as? View
            ?: return@runCatching false
        if (container.height <= 0) return@runCatching false
        val metrics = dualPaneField(fragment.javaClass, profile.synchronizedMetrics.first())?.get(fragment)
            ?: return@runCatching false
        val highlightOffset = dualPaneField(metrics.javaClass, "a")
            ?: return@runCatching false
        highlightOffset.setInt(
            metrics,
            TabletLyricAnchorPolicy.highlightOffset(
                currentOffset = highlightOffset.getInt(metrics),
                containerHeight = container.height,
            ),
        )
        true
    }.getOrDefault(false)

    private fun subtractControlsHeightFromLyricsBoundary(
        fragment: Any,
        fieldName: String,
        controlsHeight: Int,
    ): Boolean = runCatching {
        val bounds = dualPaneField(fragment.javaClass, fieldName)?.get(fragment) ?: return@runCatching false
        val lowerBoundary = dualPaneField(bounds.javaClass, "c") ?: return@runCatching false
        lowerBoundary.setInt(bounds, lowerBoundary.getInt(bounds) - controlsHeight)
        true
    }.getOrDefault(false)

    private fun refreshLyricsRecycler(fragment: Any, profile: LyricsLayoutFieldProfile) {
        val recycler = runCatching {
            val binding = dualPaneField(fragment.javaClass, profile.binding)?.get(fragment)
                ?: return@runCatching null
            dualPaneField(binding.javaClass, profile.recycler)?.get(binding)
        }.getOrNull() as? View ?: return
        if (runCatching { ModernXposedRuntime.callMethod(recycler, "S") }.isFailure) {
            recycler.requestLayout()
        }
    }

    private fun reapplyVerticalLyricsGradient(fragment: Any, profile: LyricsLayoutFieldProfile) {
        val gradients = runCatching {
            val binding = dualPaneField(fragment.javaClass, profile.binding)?.get(fragment)
                ?: return@runCatching null
            dualPaneField(binding.javaClass, profile.gradients)?.get(binding)
        }.getOrNull() as? View ?: return
        RightLyricsPaneLayout.reapplyVerticalGradientEdges(gradients)
    }

    private fun installControllerHooks(
        initialize: Method?,
        createView: Method?,
        selectPane: Method?,
    ): Int {
        var hooks = 0
        if (initialize != null && hook(initialize, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val controllerInstance = param.thisObject ?: return
                    installForControllerRoot(controllerInstance, controllerRoot(controllerInstance), "w1")
                }
            })
        ) {
            hooks += 1
        }

        // On this release w1() runs before Fragment#getView() is available.
        // The modified APK's transaction is still the model; onCreateView's
        // result is the first deterministic moment at which its two target
        // containers can be resolved by the child FragmentManager.
        if (createView != null && hook(createView, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val controllerInstance = param.thisObject ?: return
                    installForControllerRoot(controllerInstance, param.result as? View, "onCreateView")
                }
            })
        ) {
            hooks += 1
        }

        if (selectPane != null && hook(selectPane, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val requested = param.args.firstOrNull() as? Enum<*> ?: return
                    val controllerInstance = param.thisObject ?: return
                    val state = stateFor(controllerInstance) ?: return
                    if (!TabletModeQualifier.isEligible(state.root.context)) {
                        state.root.setTag(R.id.am_enhancer_dual_pane_state, null)
                        return
                    }
                    if (requested.name == DUAL_PANE_LYRICS_STATE) {
                        param.result = null
                    }
                }
            })
        ) {
            hooks += 1
        }
        return hooks
    }

    /**
     * Return Apple's native stacked holder for the transformed landscape
     * root. The transformed flat resource still relies on the stacked
     * holder's mini-player peek, navigation translation, colors and system-bar
     * transitions; allowing the native flat holder here loses that lifecycle.
     */
    private fun installNativeStackedNavigationHolderHook(
        method: Method?,
        rootMethod: Method?,
        behaviorField: Field?,
    ): Int {
        method ?: return 0
        rootMethod ?: return 0
        behaviorField ?: return 0
        val activityClass = method.declaringClass
        val behaviorType = behaviorField.type
        fun isHolderConstructor(parameters: Array<Class<*>>): Boolean {
            if (parameters.size != 3) return false
            val activityCompatible = parameters[0].isAssignableFrom(activityClass)
            val viewCompatible = parameters[1].isAssignableFrom(View::class.java) ||
                View::class.java.isAssignableFrom(parameters[1])
            val behaviorCompatible = parameters[2].isAssignableFrom(behaviorType) ||
                behaviorType.isAssignableFrom(parameters[2])
            return activityCompatible && viewCompatible && behaviorCompatible
        }
        val holderClasses = activityClass.declaredClasses.filter { nested ->
            nested.declaredConstructors.any { constructor ->
                isHolderConstructor(constructor.parameterTypes)
            }
        }
        val holderClass = when {
            holderClasses.size == 1 -> holderClasses.single()
            else -> holderClasses.singleOrNull { it.simpleName == "StackedBottomNavigationHolder" }
                ?: return 0
        }
        val constructor = holderClass.declaredConstructors
            .filter { constructor -> isHolderConstructor(constructor.parameterTypes) }
            .singleOrNull()
            ?.apply { isAccessible = true }
            ?: return 0
        return if (hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (!TabletModeQualifier.isEligible(activity)) return
                    val root = DualPaneShell.activityRoot(activity, rootMethod) ?: return
                    val resources = activity.resources
                    val stackedRootId = resources.getIdentifier(
                        "bottom_navigation_root_stacked",
                        "id",
                        ModuleConstants.RESOURCE_PACKAGE,
                    )
                    val flatRootId = resources.getIdentifier(
                        "bottom_navigation_root_flat",
                        "id",
                        ModuleConstants.RESOURCE_PACKAGE,
                    )
                    val flatRoot = if (flatRootId != 0) {
                        root.findViewById<View>(flatRootId)
                            ?: activity.findViewById<View>(flatRootId)
                    } else {
                        null
                    }
                    val navigationRoot = sequenceOf(stackedRootId, flatRootId)
                        .filter { it != 0 }
                        .mapNotNull(root::findViewById)
                        .firstOrNull()
                        ?: sequenceOf(stackedRootId, flatRootId)
                            .filter { it != 0 }
                            .mapNotNull { id -> activity.findViewById<View>(id) }
                            .firstOrNull()
                    if (navigationRoot == null) return
                    val behavior = runCatching {
                        behaviorField.apply { isAccessible = true }.get(activity)
                    }.getOrNull()
                    if (behavior == null) return
                    if (!constructor.parameterTypes[2].isInstance(behavior)) return
                    val stackedHolder = runCatching {
                        constructor.newInstance(activity, navigationRoot, behavior)
                    }.onFailure {
                        dualPaneDebug("native stacked holder construction failed: $it")
                    }.getOrNull() ?: return
                    param.result = stackedHolder
                }
            })
        ) {
            1
        } else {
            0
        }
    }

    private fun hook(method: Method, callback: XC_MethodHook): Boolean = runCatching {
        ModernXposedRuntime.hookMethod(method, callback)
        true
    }.onFailure {
        dualPaneDebug("hook registration failed for " + method.name + ": " + it)
    }.getOrDefault(false)

    private fun attachLyricsPane(controller: Any, root: View) {
        val state = stateFor(root)
        if (state == null) {
            dualPaneDebug("lyrics attachment skipped: no synchronous dual-pane state on w0 root")
            return
        }
        if (!TabletModeQualifier.isEligible(state.root.context)) return
        synchronized(state) {
            if (state.lyricsAttached || state.lyricsAttachRequested) return
            state.lyricsAttachRequested = true
        }

        val attached = runCatching { attachPairedFragments(controller, state) }
            .onFailure {
                dualPaneDebug("paired player fragment attachment failed: " + it)
            }
            .getOrDefault(false)
        synchronized(state) {
            state.lyricsAttachRequested = false
            state.lyricsAttached = attached
        }
        if (attached) {
            dualPaneDebug("lyrics fragment attached to right host=" + state.lyricsHost.id + "; awaiting target expand transition")
        }
    }

    private fun attachPairedFragments(controller: Any, state: DualPaneState): Boolean {
        val playerStateClass = controller.javaClass.declaredClasses.firstOrNull {
            it.isEnum && it.enumConstants.orEmpty().any { constant -> (constant as? Enum<*>)?.name == DUAL_PANE_SONG_STATE }
        } ?: return false
        val song = enumConstant(playerStateClass, DUAL_PANE_SONG_STATE) ?: return false
        val lyrics = enumConstant(playerStateClass, DUAL_PANE_LYRICS_STATE) ?: return false

        forceSongState(controller, playerStateClass, song)
        val manager = ModernXposedRuntime.callMethod(controller, "getChildFragmentManager") ?: return false
        if (isTargetStateSaved(manager)) return false

        // 6.5.2 declares the fragment accessor as f() and 6.5.3 as e(); both declare the tag
        // accessor as g(). Resolving by shape keeps the attachment working across the rename.
        val fragmentAccessor = DualPaneStateAccessors.fragment(playerStateClass)
        val tagAccessor = DualPaneStateAccessors.tag(playerStateClass)
        if (fragmentAccessor == null || tagAccessor == null) {
            dualPaneDebug(
                "lyrics attachment skipped: state accessors fragment=" + (fragmentAccessor != null) +
                    " tag=" + (tagAccessor != null),
            )
            return false
        }
        dualPaneDebug(
            "state accessors resolved fragment=" + fragmentAccessor.name + " tag=" + tagAccessor.name,
        )

        val songFragment = fragmentAccessor.invoke(song) ?: return false
        val songTag = tagAccessor.invoke(song) as? String ?: return false
        val lyricsFragment = fragmentAccessor.invoke(lyrics) ?: return false
        val lyricsTag = tagAccessor.invoke(lyrics) as? String ?: return false
        val transaction = createTargetTransaction(manager)
        invokeCompatible(transaction, listOf("e", "replace"), state.playerHost.id, songFragment, songTag)
        invokeCompatible(transaction, listOf("e", "replace"), state.lyricsHost.id, lyricsFragment, lyricsTag)
        invokeCompatible(transaction, listOf("h", "commit"), false)
        // FragmentManager executes the commit on the main queue. Keep this
        // post so the callback is requested after that transaction, while the
        // callback itself waits for a measured pre-draw before applying layout.
        state.playerHost.post {
            state.artworkLayoutReapply?.invoke()
        }
        return true
    }

    /**
     * Mirrors the modified w0.w1 bytecode: its shaded FragmentManager is E,
     * its concrete transaction is a(E), and its state-saved method is P().
     * The public-name fallbacks keep the hook usable on unshaded releases.
     */
    private fun isTargetStateSaved(manager: Any): Boolean = runCatching {
        invokeCompatible(manager, listOf("P", "isStateSaved")) as? Boolean
    }.getOrNull() == true

    private fun createTargetTransaction(manager: Any): Any {
        val loader = manager.javaClass.classLoader ?: error("child FragmentManager class loader was null")
        val transactionClass = Class.forName("androidx.fragment.app.a", false, loader)
        val constructor = transactionClass.declaredConstructors.firstOrNull { candidate ->
            candidate.parameterTypes.size == 1 &&
                candidate.parameterTypes[0].isAssignableFrom(manager.javaClass)
        } ?: error("androidx.fragment.app.a(E) constructor was unavailable")
        constructor.isAccessible = true
        return constructor.newInstance(manager)
    }

    private fun invokeCompatible(receiver: Any, names: List<String>, vararg args: Any?): Any? {
        var lastFailure: Throwable? = null
        for (name in names) {
            try {
                return ModernXposedRuntime.callMethod(receiver, name, *args)
            } catch (failure: Throwable) {
                lastFailure = failure
            }
        }
        throw lastFailure ?: NoSuchMethodError(receiver.javaClass.name + "#" + names.joinToString("/"))
    }

    private fun forceSongState(controller: Any, stateClass: Class<*>, song: Any) {
        dualPaneField(controller.javaClass, "a")
            ?.takeIf { stateClass.isAssignableFrom(it.type) }
            ?.let { setDualPaneField(it, controller, song) }
            ?: controller.javaClass.declaredFields
                .firstOrNull { !Modifier.isStatic(it.modifiers) && stateClass.isAssignableFrom(it.type) }
                ?.let { setDualPaneField(it, controller, song) }

        runCatching { ModernXposedRuntime.callMethod(controller, "C1", false) }
        // 6.5.2 declares this state LiveData as field S and 6.5.3 as Q (both builds shift the
        // controller fields after the two media items), so the field is located by its type.
        dualPaneFieldByType(controller.javaClass) { field -> field.type.name == DUAL_PANE_MUTABLE_LIVE_DATA }
            ?.let { field ->
                runCatching {
                    val liveData = field.apply { isAccessible = true }.get(controller)
                    if (liveData != null) ModernXposedRuntime.callMethod(liveData, "setValue", song)
                }
            }
    }

    private fun enumConstant(stateClass: Class<*>, name: String): Any? =
        stateClass.enumConstants.orEmpty().firstOrNull { (it as? Enum<*>)?.name == name }

    private fun stateFor(controller: Any): DualPaneState? = controllerRoot(controller)?.let { root -> stateFor(root) }

    private fun stateFor(root: View): DualPaneState? =
        root.getTag(R.id.am_enhancer_dual_pane_state) as? DualPaneState

    private fun controllerRoot(controller: Any): View? = runCatching {
        ModernXposedRuntime.callMethod(controller, "getView") as? View
    }.getOrNull()

    private fun installForControllerRoot(controller: Any, root: View?, source: String) {
        val rootGroup = root as? ViewGroup
        if (rootGroup == null) {
            dualPaneDebug("controller root unavailable source=$source")
            return
        }
        dualPaneDebug(
            "controller root observed source=$source identity=" +
                System.identityHashCode(rootGroup) +
                " attached=" + rootGroup.isAttachedToWindow,
        )
        if (DualPaneShell.installImmediately(rootGroup) != null) attachLyricsPane(controller, rootGroup)
    }
}
