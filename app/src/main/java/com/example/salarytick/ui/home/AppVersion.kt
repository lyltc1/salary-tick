package com.example.salarytick.ui.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

/**
 * 当前安装包的版本，形如 "v1.5（build 6）"。
 *
 * 从 PackageManager 里读，而不去开 buildConfig 构建特性 ——
 * 少一个开关，且永远跟实际装上的那个包一致（改了版本不用重新生成 BuildConfig）。
 * 万一读不到就给空串，调用方不显示即可：拿版本号这事不值得把界面搞崩。
 */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val name = info.versionName?.let { "v$it" }.orEmpty()
            val code = PackageInfoCompat.getLongVersionCode(info)
            "$name（build $code）".trim()
        }.getOrDefault("")
    }
}
