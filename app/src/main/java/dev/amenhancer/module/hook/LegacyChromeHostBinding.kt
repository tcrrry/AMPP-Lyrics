package dev.amenhancer.module.hook

import android.app.Activity
import android.view.Menu
import android.view.View
import dev.amenhancer.host.applemusic.AppleMusicHostProfiles
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** A binding owns all reflective discovery. Callbacks use the cached members. */
internal class LegacyChromeHostBinding(private val activity: Activity) : ChromeHostBinding {
    override val pageFamily = HostPageFamily.LEGACY_ACTIVITY
    override val viewSessionIdentity: Any get() = activity.window.decorView
    override fun regions() = PlayerRegions(find(ChromeResource.BOTTOM_NAVIGATION), NavigationPlacement.BOTTOM,
        find(ChromeResource.MINI_PLAYER) ?: find(ChromeResource.MINI_PLAYER_TOUCH_PANEL),
        find(ChromeResource.PLAYER_SHEET_CONTAINER), restrictedPlayerContainer = false)
    private val build = targetBuild(activity)
    private val profile = checkNotNull(AppleMusicHostProfiles.find(build.packageName, build.versionName, build.versionCode))
        .document.getJSONObject("chrome")
    private val ids = ChromeResource.entries.associateWith { role ->
        val resource = profile.getJSONObject("resources").getJSONObject(role.name)
        activity.resources.getIdentifier(resource.getString("name"),resource.getString("type"),build.packageName)
    }
    private val views = HashMap<ChromeResource, View?>()
    private var configuration = android.content.res.Configuration(activity.resources.configuration)
    private var dimensions = readDimensions()
    private val methods = HashMap<Pair<Class<*>,String>,Method>()
    private val nav = profile.getJSONObject("navigation")
    private val behavior = profile.getJSONObject("behavior")
    private val viewTypes = profile.getJSONObject("views")
    private val scrollTypes = viewTypes.getJSONArray("scrollTypes").let { array ->
        (0 until array.length()).map(array::getString).toSet()
    }
    private val scrollClasses = IdentityHashMap<Class<*>,Boolean>()
    private val base by lazy { activity.classLoader.loadClass(behavior.getString("baseClass")) }
    private val stateField by lazy { base.getDeclaredField(behavior.getString("stateField")).apply { isAccessible=true } }
    private val collapsedField by lazy { base.getDeclaredField(behavior.getString("collapsedTopField")).apply { isAccessible=true } }
    private val expandedMethod by lazy { method(base,behavior.getString("expandedTopMethod")) }
    override fun resourceId(role: ChromeResource): Int = ids.getValue(role)
    override fun find(role: ChromeResource): View? {
        if (views.containsKey(role)) return views[role]
        return resourceId(role).takeIf { it!=0 }?.let { activity.findViewById<View>(it) }
            .also { views[role]=it }
    }
    override fun invalidateViews() {
        views.clear()
        if (configuration != activity.resources.configuration) {
            configuration=android.content.res.Configuration(activity.resources.configuration)
            dimensions=readDimensions()
        }
    }
    private fun readDimensions(): Map<ChromeResource,Int> = ChromeResource.entries
        .filter { profile.getJSONObject("resources").getJSONObject(it.name).getString("type")=="dimen" }
        .associateWith { role -> resourceId(role).takeIf { it!=0 }?.let(activity.resources::getDimensionPixelSize) ?: 0 }
    override fun dimension(role: ChromeResource): Int = dimensions[role] ?: 0
    override fun playerBehavior(preferPlayerRuntime: Boolean): Any? {
        val fields=generateSequence(activity.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }.toList()
        val preferred=if (preferPlayerRuntime) fields.asSequence().mapNotNull { field ->
            runCatching { field.isAccessible=true; field.get(activity) }.getOrNull()
        }.firstOrNull { it.javaClass.name.contains(behavior.getString("runtimeTypeToken")) } else null
        val owner=preferred ?: fields.firstNotNullOfOrNull { field ->
            if (field.type.name.contains(behavior.getString("declaredTypeToken")))
                runCatching { field.isAccessible=true; field.get(activity) }.getOrNull() else null
        }
        if (owner!=null) {
            method(owner.javaClass,behavior.getString("peekMethod"),Int::class.javaPrimitiveType!!,Boolean::class.javaPrimitiveType!!)
            // Resolve the state contract during binding, before exposing a usable owner.
            stateField; collapsedField; expandedMethod
        }
        return owner
    }
    override fun sheetSnapshot(owner: Any)=NativeSheetSnapshot(stateField.getInt(owner),collapsedField.getInt(owner),
        (expandedMethod.invoke(owner) as Number).toInt())
    override fun writePeek(owner: Any,height: Int) {
        methods.getValue(owner.javaClass to behavior.getString("peekMethod")).invoke(owner,height,false)
    }
    override fun navigation(view: View)=NativeNavigationSnapshot(
        method(view.javaClass,nav.getString("menuMethod")).invoke(view) as Menu,
        (method(view.javaClass,nav.getString("selectedMethod")).invoke(view) as Number).toInt())
    override fun selectNavigation(view: View,id: Int) {
        method(view.javaClass,nav.getString("selectMethod"),Int::class.javaPrimitiveType!!).invoke(view,id)
    }
    override fun isScrollContainer(view: View): Boolean=scrollClasses.getOrPut(view.javaClass) {
        generateSequence(view.javaClass as Class<*>?) { it.superclass }.any { it.name in scrollTypes }
    }
    override fun isComposeScene(view: View): Boolean=view.javaClass.name==viewTypes.getString("composeType")
    override fun isPagerPageHost(view: View): Boolean=
        (view.parent as? View)?.javaClass?.name == viewTypes.getString("pagerType")
    private fun method(type: Class<*>,name: String,vararg parameters: Class<*>): Method =
        methods.getOrPut(type to name) {
            generateSequence(type as Class<*>?) { it.superclass }.firstNotNullOfOrNull {
                runCatching { it.getDeclaredMethod(name,*parameters).apply { isAccessible=true } }.getOrNull()
            } ?: throw NoSuchMethodException("${type.name}#$name")
        }
    override fun close() { views.clear();methods.clear();scrollClasses.clear() }
}
