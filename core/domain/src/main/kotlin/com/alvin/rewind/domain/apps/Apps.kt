package com.alvin.rewind.domain.apps

data class AppInfo(
    val packageName: String,
    val label: String,
    val isVideo: Boolean,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val firstInstallTimeMs: Long,
)

enum class AppChangeType { INSTALLED, UNINSTALLED }

data class AppChange(
    val packageName: String,
    val label: String,
    val type: AppChangeType,
    val atMs: Long,
    /** false when we only know "it disappeared between two snapshots", not the exact moment. */
    val isExactTime: Boolean,
)

/**
 * Detects installs/uninstalls by comparing two snapshots of launchable apps.
 *
 * - Install time comes from the OS (`firstInstallTime`), so it is exact even if we notice late.
 * - Uninstall time is unknown from a snapshot alone: we stamp it with [nowMs] and mark it inexact.
 *   (The notification listener also catches PACKAGE_REMOVED live, which gives exact timestamps
 *   whenever it is running; see PackageTracker in the app module.)
 * - A changed firstInstallTime for the same package means it was removed and reinstalled in between.
 */
object PackageDiff {

    fun diff(
        previous: Map<String, InstalledApp>,
        current: Map<String, InstalledApp>,
        nowMs: Long,
    ): List<AppChange> {
        val installs = current.values
            .filter { app ->
                val before = previous[app.packageName]
                before == null || before.firstInstallTimeMs != app.firstInstallTimeMs
            }
            .map { AppChange(it.packageName, it.label, AppChangeType.INSTALLED, it.firstInstallTimeMs, isExactTime = true) }

        val removals = (previous.keys - current.keys)
            .map { pkg -> previous.getValue(pkg) }
            .map { AppChange(it.packageName, it.label, AppChangeType.UNINSTALLED, nowMs, isExactTime = false) }

        return (installs + removals).sortedBy { it.atMs }
    }
}

/**
 * Apps whose foreground time we count as "video watched".
 *
 * Primary signal is the developer-declared `ApplicationInfo.CATEGORY_VIDEO`; this list fills gaps for
 * popular apps that don't declare it. Deliberately an *estimate*: we measure time the app was open,
 * not frames played. See README, "Video consumption".
 */
object KnownVideoApps {
    val packages: Set<String> = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.netflix.mediaclient",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "tv.twitch.android.app",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient", // Prime Video
    )
}
