package dev.amenhancer.module.hook

import android.content.Context
import dev.amenhancer.module.ModuleConstants

fun targetBuild(context: Context): TargetBuild = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(ModuleConstants.TARGET_PACKAGE, 0)
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
    }
    TargetBuild(
        packageName = ModuleConstants.TARGET_PACKAGE,
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = versionCode,
    )
}.getOrDefault(TargetBuild.UNKNOWN)
