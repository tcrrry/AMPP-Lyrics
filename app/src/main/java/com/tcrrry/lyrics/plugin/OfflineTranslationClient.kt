package com.tcrrry.lyrics.plugin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.internal.CommonComponentRegistrar
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.nl.translate.NaturalLanguageTranslateRegistrar
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.amenhancer.module.hook.ModernXposedRuntime
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer

/** Deliberate parent-loader adapter for our bundled plugin's public offline-client surface.
 * Standalone ZIPs keep the companion implementation; the integrated module owns ML Kit/native libs.
 */
object OfflineTranslationClient {
    val languages = "af ar be bg bn ca cs cy da de el en eo es et fa fi fr ga gl gu he hi hr ht hu id is it ja ka kn ko lt lv mk mr ms mt nl no pl pt ro ru sk sl sq sv sw ta te th tl tr uk ur vi zh".split(" ")
    private val slots = Semaphore(2)
    @Synchronized private fun initialize(context: Context) {
        val host = context.applicationContext
        val info = checkNotNull(ModernXposedRuntime.activeModule()?.moduleApplicationInfo)
        val packageResources = host.packageManager.getResourcesForApplication(info)
        @Suppress("DEPRECATION")
        val resources = Resources(packageResources.assets, host.resources.displayMetrics, Configuration(host.resources.configuration))
        check(resources.getResourceEntryName(com.google.mlkit.nl.translate.R.raw.translate_models_metadata) == "translate_models_metadata")
        val wrapper = object : ContextWrapper(host) {
            override fun getApplicationContext(): Context = this
            override fun getResources() = resources
            override fun getAssets() = resources.assets
        }
        MlKitContext.initializeIfNeeded(wrapper, listOf(CommonComponentRegistrar(), NaturalLanguageTranslateRegistrar()))
    }
    fun installed(context: Context) = true
    fun statusText(context: Context) = "离线机翻已内置，无需伴随 APK"
    fun requireInstalled(context: Context) { initialize(context) }
    fun translate(context: Context, language: String, text: String): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        require(language in languages && text.isNotBlank() && text.length <= 4000)
        requireInstalled(context)
        check(slots.tryAcquire()) { "翻译正在忙，请稍后重试" }
        try {
            val installed = Tasks.await(RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java), 15, TimeUnit.SECONDS).map { it.language }.toSet()
            require(installed.containsAll(setOf(language, "zh").filterNot { it == "en" })) { "请在语言包管理中下载原语言与中文模型" }
            val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(language).setTargetLanguage("zh").build())
            try { return Tasks.await(translator.translate(text), 30, TimeUnit.SECONDS) }
            finally { translator.close() }
        } finally { slots.release() }
    }
    fun queryModels(context: Context, complete: BiConsumer<Set<String>?, String?>) {
        try {
            initialize(context)
            RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) complete.accept(task.result.map { it.language }.toSet(), null)
                    else complete.accept(null, "读取语言包失败，请稍后重试")
                }
        } catch (_: Exception) { complete.accept(null, "无法初始化内置离线翻译") }
    }
    fun downloadModels(context: Context, language: String, complete: Consumer<String?>) {
        try {
            val models = OfflineModelPolicy.downloads(language, languages)
            initialize(context)
            val manager = RemoteModelManager.getInstance()
            val tasks = models.map { manager.download(TranslateRemoteModel.Builder(it).build(), DownloadConditions.Builder().build()) }
            Tasks.whenAll(tasks).addOnCompleteListener { task ->
                complete.accept(if (task.isSuccessful) null else "下载失败，请检查网络后重试")
            }
        } catch (_: Exception) { complete.accept("无法下载所选语言模型") }
    }
    fun deleteModel(context: Context, language: String, complete: Consumer<String?>) {
        try {
            require(OfflineModelPolicy.canDelete(language, languages))
            initialize(context)
            RemoteModelManager.getInstance().deleteDownloadedModel(TranslateRemoteModel.Builder(language).build())
                .addOnCompleteListener { task -> complete.accept(if (task.isSuccessful) null else "删除失败，请稍后重试") }
        } catch (_: Exception) { complete.accept("无法删除所选语言模型") }
    }
    fun openManager(activity: Activity) {
        try { initialize(activity) } catch (error: Exception) {
            android.widget.Toast.makeText(activity, "无法初始化离线翻译：${error.message}", android.widget.Toast.LENGTH_LONG).show(); return
        }
        val modelManager = RemoteModelManager.getInstance()
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 20, 32, 20) }
        val locale = java.util.Locale.getDefault()
        val names = languages.map { "${java.util.Locale.forLanguageTag(it).getDisplayName(locale)} ($it)" }
        val spinner = Spinner(activity).apply { adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, names) }
        val status = TextView(activity).apply { setPadding(0, 24, 0, 24) }
        val download = Button(activity).apply { text = "下载所选语言与中文模型" }
        val delete = Button(activity).apply { text = "删除所选语言模型" }
        body.addView(spinner); body.addView(status); body.addView(download); body.addView(delete)
        val dialog = android.app.AlertDialog.Builder(activity).setTitle("内置离线翻译语言包").setView(body).setPositiveButton("关闭", null).create()
        fun refresh() {
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnCompleteListener { task ->
                if (dialog.isShowing && !activity.isDestroyed) status.text = if (task.isSuccessful)
                    "已下载：" + task.result.map { java.util.Locale.forLanguageTag(it.language).getDisplayName(locale) }.sorted().joinToString("、").ifBlank { "暂无" }
                    else "读取模型失败，请稍后重试"
            }
        }
        fun busy(value: Boolean) { download.isEnabled = !value; delete.isEnabled = !value }
        download.setOnClickListener {
            busy(true); status.text = "正在下载语言模型"
            val models = setOf(languages[spinner.selectedItemPosition], "zh").filterNot { it == "en" }
            val tasks = models.map { modelManager.download(TranslateRemoteModel.Builder(it).build(), DownloadConditions.Builder().build()) }
            Tasks.whenAll(tasks).addOnCompleteListener { task ->
                if (dialog.isShowing && !activity.isDestroyed) { busy(false); if (task.isSuccessful) refresh() else status.text = "下载失败，请检查网络后重试" }
            }
        }
        delete.setOnClickListener {
            val language = languages[spinner.selectedItemPosition]
            if (language == "en") return@setOnClickListener
            busy(true)
            modelManager.deleteDownloadedModel(TranslateRemoteModel.Builder(language).build()).addOnCompleteListener {
                if (dialog.isShowing && !activity.isDestroyed) { busy(false); refresh() }
            }
        }
        dialog.show(); refresh()
    }
}
