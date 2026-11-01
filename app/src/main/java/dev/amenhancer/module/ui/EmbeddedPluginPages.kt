package dev.amenhancer.module.ui

import dev.amenhancer.module.i18n.ModuleText

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.amenhancer.plugin.runtime.PluginRunState
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

internal fun EmbeddedSettingsHost.renderEmbeddedPluginManagementPage(
    activity: Activity,
    parent: LinearLayout,
    scroll: ScrollView,
    onInitialContentReady: () -> Unit,
): () -> Unit {
    parent.removeAllViews()
    val manager = plugins
    if (manager == null) {
        parent.addView(
            embeddedInfoCard(activity, localizedText(ModuleText.PLUGIN_RUNTIME_UNAVAILABLE)),
            matchWidthWrapContent(),
        )
        onInitialContentReady()
        return {}
    }

    val active = AtomicBoolean(true)
    val refreshing = AtomicBoolean()
    var initialContentReady = false
    fun action(block: () -> Unit) {
        manager.execute {
            val result = runCatching(block)
            mainHandler.post {
                if (!active.get() || activity.isFinishing || activity.isDestroyed) return@post
                currentActivity()?.let { Toast.makeText(it, result.exceptionOrNull()?.message ?: localizedText(ModuleText.SAVED_RESTART), Toast.LENGTH_LONG).show() }
            }
        }
    }
    val refresh = object : Runnable {
        override fun run() {
            if (!active.get() || !refreshing.compareAndSet(false, true)) return
            manager.execute {
                val statuses = runCatching { manager.statuses() }
                mainHandler.post {
                    if (!active.get() || activity.isFinishing || activity.isDestroyed) return@post
                    refreshing.set(false)
                    val scrollPosition = scroll.scrollY
                    parent.removeAllViews()
                    parent.addView(
                        embeddedInfoCard(activity, localizedText(ModuleText.PLUGIN_RESTART_NOTICE)),
                        matchWidthWrapContent(),
                    )
                    manager.startupError?.let {
                        parent.addView(embeddedSpacer(activity, 12))
                        parent.addView(
                            embeddedInfoCard(activity, localizedText(ModuleText.LOAD_ERROR, it)),
                            matchWidthWrapContent(),
                        )
                    }
                    statuses.onFailure {
                        parent.addView(embeddedSpacer(activity, 12))
                        parent.addView(
                            embeddedInfoCard(activity, localizedText(ModuleText.PLUGINS_READ_FAILED, it.message)),
                            matchWidthWrapContent(),
                        )
                    }
                    if (statuses.getOrNull()?.isEmpty() == true) {
                        parent.addView(embeddedSpacer(activity, 12))
                        parent.addView(
                            embeddedInfoCard(activity, localizedText(ModuleText.NO_PLUGINS)),
                            matchWidthWrapContent(),
                        )
                    }
                    statuses.getOrDefault(emptyList()).forEach { status ->
                        val manifest = status.installed.manifest
                        val state = when (status.state) {
                            PluginRunState.ACTIVE -> localizedText(ModuleText.PLUGIN_ACTIVE)
                            PluginRunState.DISABLED -> localizedText(ModuleText.PLUGIN_DISABLED)
                            PluginRunState.LOADING -> localizedText(ModuleText.PLUGIN_LOADING)
                            PluginRunState.BLOCKED -> localizedText(ModuleText.PLUGIN_BLOCKED)
                            PluginRunState.UNSUPPORTED -> localizedText(ModuleText.PLUGIN_UNSUPPORTED)
                            PluginRunState.FAILED -> localizedText(ModuleText.PLUGIN_FAILED)
                        }
                        parent.addView(embeddedSpacer(activity, 12))
                        parent.addView(embeddedCard(activity, manifest.name) {
                            addView(TextView(activity).apply {
                                text = localizedText(ModuleText.PLUGIN_STATUS_DETAILS, manifest.author, manifest.versionName, manifest.id, state, status.runningVersion?.let { localizedText(ModuleText.PLUGIN_RUNNING_VERSION, it) }.orEmpty(), status.message)
                                textSize = 12f
                                setEmbeddedTextColor(activity) { it.secondary }
                                setSingleLine(false)
                                setPadding(0, 0, 0, dp(activity, 12))
                            }, matchWidthWrapContent())
                            addView(embeddedSettingRow(activity, localizedText(ModuleText.ENABLE_NEXT_START), if (status.pendingRestart) localizedText(ModuleText.RESTART_PENDING) else "", status.installed.enabled) { enabled ->
                                action { manager.store.setEnabled(manifest.id, enabled) }
                            })
                            if (status.conflicts.isNotEmpty()) addView(embeddedNavigationRow(activity, localizedText(ModuleText.CONFLICT_DETAILS), localizedText(ModuleText.ITEM_COUNT, status.conflicts.size)) {
                                val details = status.conflicts.joinToString("\n\n") {
                                    localizedText(ModuleText.PLUGIN_CONFLICT_DETAILS,
                                        localizedText(if (it.blocking) ModuleText.CONFLICT_BLOCKING else ModuleText.CONFLICT_WARNING),
                                        it.reason, it.target, it.owners.joinToString(" / "))
                                }
                                val info = embeddedDialogBuilder(activity).setTitle(localizedText(ModuleText.CONFLICT_DETAILS)).setMessage(details).setPositiveButton(localizedText(ModuleText.CLOSE), null).create()
                                pluginDialogs += WeakReference(info); info.show()
                            })
                            if (status.state == PluginRunState.ACTIVE) addView(embeddedNavigationRow(activity, localizedText(ModuleText.PLUGIN_SETTINGS), "") { openPluginSettings(activity, manifest.id) })
                            addView(embeddedSpacer(activity, 8))
                            addView(embeddedActionButton(activity, localizedText(ModuleText.DELETE)) {
                                val confirm = embeddedDialogBuilder(activity).setTitle(localizedText(ModuleText.DELETE_NAMED_ITEM, manifest.name))
                                    .setMessage(localizedText(ModuleText.PLUGIN_DELETE_NOTICE))
                                    .setPositiveButton(localizedText(ModuleText.DELETE)) { _, _ -> action { manager.store.delete(manifest.id) } }.setNegativeButton(localizedText(ModuleText.CANCEL), null).create()
                                pluginDialogs += WeakReference(confirm); confirm.show()
                            }, matchWidthWrapContent())
                        }, matchWidthWrapContent())
                    }
                    if (!initialContentReady) {
                        initialContentReady = true
                        onInitialContentReady()
                    } else scroll.post { if (active.get()) scroll.scrollTo(0, scrollPosition) }
                    mainHandler.postDelayed(this, 1500)
                }
            }
        }
    }
    mainHandler.post(refresh)
    return {
        if (active.compareAndSet(true, false)) {
            mainHandler.removeCallbacks(refresh)
            refreshing.set(false)
        }
    }
}

internal fun EmbeddedSettingsHost.importPluginZip(uri: Uri) {
    val manager = plugins ?: return
    manager.execute {
        val result = runCatching {
            val input = application.contentResolver.openInputStream(uri) ?: error(localizedText(ModuleText.FILE_UNREADABLE))
            manager.store.prepare(input, Build.VERSION.SDK_INT)
        }
        mainHandler.post {
            val activity = currentActivity()
            if (activity == null || activity.isDestroyed) { manager.execute { result.getOrNull()?.close() }; return@post }
            result.onFailure { Toast.makeText(activity, localizedText(ModuleText.IMPORT_FAILED, it.message), Toast.LENGTH_LONG).show() }
            val prepared = result.getOrNull() ?: return@post
            // Read the installed version off the UI thread before showing replacement details.
            manager.execute {
                val oldResult = runCatching { manager.store.installed().firstOrNull { it.manifest.id == prepared.manifest.id } }
                mainHandler.post confirm@{
                    val current = currentActivity()
                    if (current == null || current.isDestroyed || oldResult.isFailure) {
                        if (current != null && oldResult.isFailure) Toast.makeText(current, localizedText(ModuleText.INSTALLED_PLUGIN_READ_FAILED, oldResult.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                        manager.execute { prepared.close() }; return@confirm
                    }
                    val old = oldResult.getOrNull()
                    val committed = AtomicBoolean()
                    val dialog = embeddedDialogBuilder(current).setTitle(if (old == null) localizedText(ModuleText.IMPORT_PLUGIN) else localizedText(ModuleText.REPLACE_PLUGIN))
                        .setMessage(localizedText(ModuleText.PLUGIN_AUTHOR_DETAILS, prepared.manifest.name, prepared.manifest.author) +
                            (old?.let { "${it.manifest.versionName} → " } ?: "") + prepared.manifest.versionName +
                            "\n${prepared.manifest.description}\n" + if (old == null) localizedText(ModuleText.PLUGIN_IMPORTED_DISABLED) else localizedText(ModuleText.PLUGIN_REPLACEMENT_NOTICE))
                        .setPositiveButton(if (old == null) localizedText(ModuleText.IMPORT) else localizedText(ModuleText.REPLACE)) { _, _ ->
                            committed.set(true)
                            manager.execute {
                                val saved = runCatching { manager.store.commit(prepared) }
                                prepared.close()
                                mainHandler.post { currentActivity()?.let { Toast.makeText(it, saved.exceptionOrNull()?.message ?: localizedText(ModuleText.PLUGIN_IMPORTED), Toast.LENGTH_LONG).show() } }
                            }
                        }.setNegativeButton(localizedText(ModuleText.CANCEL), null).create()
                    dialog.setOnDismissListener { if (!committed.get()) manager.execute { prepared.close() } }
                    pluginDialogs += WeakReference(dialog); dialog.show()
                }
            }
        }
    }
}

internal fun EmbeddedSettingsHost.openPluginSettings(activity: Activity, id: String) {
    val session = plugins?.openSettings(id, embeddedThemedContext(activity)) ?: return Toast.makeText(activity, localizedText(ModuleText.PLUGIN_NO_SETTINGS), Toast.LENGTH_SHORT).show()
    try {
        excludeEmbeddedTheme(session.view)
        lateinit var dialog: AlertDialog
        val panel = embeddedPluginPageContent(activity, localizedText(ModuleText.PLUGIN_SETTINGS), session.view) { dialog.dismiss() }
        dialog = embeddedDialogBuilder(activity).setPositiveButton(localizedText(ModuleText.CLOSE), null).create()
            .apply { setView(panel, 0, 0, 0, 0) }
        dialog.setOnDismissListener { session.close() }
        pluginDialogs += WeakReference(dialog); dialog.show()
    } catch (error: Throwable) {
        session.close(); Toast.makeText(activity, localizedText(ModuleText.SETTINGS_OPEN_FAILED, error.message), Toast.LENGTH_LONG).show()
    }
}

private fun EmbeddedSettingsHost.embeddedPluginPageContent(
    activity: Activity,
    title: String,
    content: View,
    onBack: () -> Unit,
): LinearLayout = LinearLayout(activity).apply {
    orientation = LinearLayout.VERTICAL
    val hostInset = dp(activity, if (isEmbeddedPhone(activity)) 0 else 8)
    setPadding(hostInset, 0, hostInset, 0)
    addView(LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val headerInset = dp(activity, if (isEmbeddedPhone(activity)) 8 else 0)
        setPadding(headerInset, 0, headerInset, 0)
        addView(ImageView(activity).apply {
            setEmbeddedImageDrawable(activity, embeddedSvgDrawable(EmbeddedSvgIcon.Back, activity) ?: loadEmbeddedArrowIcon(activity))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = localizedText(ModuleText.BACK)
            isClickable = true
            isFocusable = true
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            bindEmbeddedClickFeedback(activity, cornerRadiusDp = 12)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(activity, 44), embeddedTopBarHeight(activity)))
        addView(TextView(activity).apply {
            text = title
            textSize = embeddedTextSize(activity, 19f, 18f)
            setEmbeddedTextColor(activity) { colors -> colors.onSurface }
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, embeddedTopBarHeight(activity)))
    addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
}
