package dev.amenhancer.module.hook

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.google.mlkit.common.internal.CommonComponentRegistrar
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.nl.translate.NaturalLanguageTranslateRegistrar

/** NPatch embeds dex but neither the ML Kit manifest service nor its raw resources in the host. */
internal object EmbeddedMlKit {
    @Synchronized fun initialize(context: Context) {
        val host = context.applicationContext
        val moduleInfo = checkNotNull(ModernXposedRuntime.activeModule()?.moduleApplicationInfo) {
            "无法定位嵌入模块的模型资源"
        }
        val packageResources = host.packageManager.getResourcesForApplication(moduleInfo)
        val resources = Resources(packageResources.assets, host.resources.displayMetrics,
            Configuration(host.resources.configuration))
        require(resources.getResourceEntryName(com.google.mlkit.nl.translate.R.raw.translate_models_metadata) ==
            "translate_models_metadata") { "翻译模型元数据未装入" }
        val mlKitContext = object : ContextWrapper(host) {
            override fun getApplicationContext(): Context = this
            override fun getResources(): Resources = resources
            override fun getAssets() = resources.assets
        }
        MlKitContext.initializeIfNeeded(
            mlKitContext,
            listOf(
                CommonComponentRegistrar(),
                NaturalLanguageTranslateRegistrar(),
            ),
        )
    }
}
