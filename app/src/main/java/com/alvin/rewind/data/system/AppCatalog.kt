package com.alvin.rewind.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.alvin.rewind.domain.apps.AppInfo
import com.alvin.rewind.domain.apps.InstalledApp
import com.alvin.rewind.domain.apps.KnownVideoApps
import java.util.concurrent.ConcurrentHashMap

/** Everything we need from PackageManager: labels, categories, launchable apps, the home app. */
class AppCatalog(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val cache = ConcurrentHashMap<String, AppInfo>()

    /** Label + "is this a video app". Falls back to the package name for apps no longer installed. */
    fun info(packageName: String): AppInfo = cache.getOrPut(packageName) { load(packageName) }

    private fun load(packageName: String): AppInfo = try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        AppInfo(
            packageName = packageName,
            label = pm.getApplicationLabel(appInfo).toString(),
            isVideo = appInfo.category == ApplicationInfo.CATEGORY_VIDEO || packageName in KnownVideoApps.packages,
        )
    } catch (e: PackageManager.NameNotFoundException) {
        AppInfo(packageName, packageName, packageName in KnownVideoApps.packages)
    }

    /** Apps with a launcher icon: what users think of as "my apps". */
    @Suppress("DEPRECATION") // getPackageInfo(String, Int) is fine for our minSdk/targetSdk
    fun launchableApps(): List<InstalledApp> =
        queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                try {
                    InstalledApp(pkg, info(pkg).label, pm.getPackageInfo(pkg, 0).firstInstallTime)
                } catch (e: PackageManager.NameNotFoundException) {
                    null // uninstalled between the two calls
                }
            }

    /**
     * Packages whose foreground time is not "using an app": the home screen, System UI, ourselves.
     * They are still used to *close* sessions (see SessionReducer), just not reported.
     */
    fun excludedFromScreenTime(): Set<String> =
        queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .map { it.activityInfo.packageName }
            .toSet() + setOf(context.packageName, "com.android.systemui")

    private fun queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
}
