package dev.amenhancer.module.hook

import android.os.Bundle
import org.json.JSONObject

/** All private names and argument/resource identities belong to the exact host profile. */
internal class StartupPagePrewarmContract(loader: ClassLoader, val names: JSONObject) {
    private val members = FragmentProfileMembers(loader, names.getJSONObject("members"),
        "startupPagePrewarm.members", FragmentMemberRequirements(
            methods = setOf("viewCreated", "destroyView", "activity", "view", "childManager", "resumed",
                "viewModel", "tabs", "value", "selectedTab", "kindLabel", "kindId", "menu", "selectedId",
                "find", "stateSaved", "arguments", "add", "hide", "onCommit", "commit", "tag", "hidden",
                "observePlayContext", "containerView", "parent", "libraryViewCreated", "notifyEvent"),
            fields = setOf("modelKind", "primary", "reordering"),
            constructors = setOf("container", "transaction"),
        ))
    val viewCreated = members.method("viewCreated")
    val destroyView = members.method("destroyView")
    val activity = members.method("activity")
    val view = members.method("view")
    val childManager = members.method("childManager")
    val resumed = members.method("resumed")
    val viewModel = members.method("viewModel")
    val tabs = members.method("tabs")
    val value = members.method("value")
    val selectedTab = members.method("selectedTab")
    val kindLabel = members.method("kindLabel")
    val kindId = members.method("kindId")
    val menu = members.method("menu")
    val selectedId = members.method("selectedId")
    val tabletMenu = if (names.optString("tabletNavigationResource").isNotBlank())
        members.method("tabletMenu") else null
    val find = members.method("find")
    val stateSaved = members.method("stateSaved")
    val arguments = members.method("arguments")
    val add = members.method("add")
    val hide = members.method("hide")
    val onCommit = members.method("onCommit")
    val commit = members.method("commit")
    val tag = members.method("tag")
    val hidden = members.method("hidden")
    val parent = members.method("parent")
    val observePlayContext = members.method("observePlayContext")
    val containerView = members.method("containerView")
    val libraryViewCreated = members.method("libraryViewCreated")
    val notifyEvent = members.method("notifyEvent")
    val modelKind = members.field("modelKind")
    val primary = members.field("primary")
    val reordering = members.field("reordering")
    val container = members.constructor("container")
    val transaction = members.constructor("transaction")

    init {
        check(names.getInt("schemaVersion") == 1)
        check(selectedTab.returnType.isEnum && modelKind.type == selectedTab.returnType)
        check(primary.type.isAssignableFrom(container.declaringClass))
        check(childManager.returnType == transaction.parameterTypes.single())
        check(add.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType,
            primary.type, String::class.java, Int::class.javaPrimitiveType)))
        check(arguments.parameterTypes.contentEquals(arrayOf(Bundle::class.java)))
        check(reordering.type == Boolean::class.javaPrimitiveType)
        check(notifyEvent.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Any::class.java)))
    }
}
