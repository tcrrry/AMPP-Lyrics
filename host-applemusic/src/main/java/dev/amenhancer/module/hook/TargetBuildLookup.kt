package dev.amenhancer.module.hook

import android.content.Context

fun targetBuild(context: Context): TargetBuild = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
    }
    TargetBuild(
        packageName = context.packageName,
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = versionCode,
    )
}.getOrElse { TargetBuild(context.packageName, "", -1) }
