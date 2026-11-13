package com.alvin.rewind.data.collect

import com.alvin.rewind.data.local.AppChangeEntity
import com.alvin.rewind.data.local.AppsDao
import com.alvin.rewind.data.local.PackageSnapshotEntity
import com.alvin.rewind.data.system.AppCatalog
import com.alvin.rewind.domain.apps.AppChangeType
import com.alvin.rewind.domain.apps.InstalledApp
import com.alvin.rewind.domain.apps.PackageDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Tracks installs and uninstalls with two complementary mechanisms:
 *
 * 1. Snapshot diff ([sync]) on every collection run. Always works, needs no permission beyond
 *    package visibility. Install time is exact (OS firstInstallTime); uninstall time is "detected at".
 * 2. Live PACKAGE_ADDED / PACKAGE_REMOVED broadcasts, received while our notification listener is
 *    bound (see [ActivityNotificationListener]). Since Android 8 these implicit broadcasts can't
 *    be received by a manifest receiver, but a dynamically registered one in a long-lived,
 *    system-bound service can. Gives exact uninstall times when available.
 *
 * Both write through the same snapshot table, so an uninstall is never recorded twice.
 */
class PackageTracker(
    private val catalog: AppCatalog,
    private val dao: AppsDao,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun sync() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = dao.snapshot()
            val previous = snapshot.filter { it.installed }.associate {
                it.packageName to InstalledApp(it.packageName, it.label, it.firstInstallTimeMs)
            }
            val current = catalog.launchableApps().associateBy { it.packageName }
            val changes = PackageDiff.diff(previous, current, clock.millis())

            val nowInstalled = current.values.map { PackageSnapshotEntity(it.packageName, it.label, it.firstInstallTimeMs, installed = true) }
            val nowRemoved = (previous.keys - current.keys).map { pkg ->
                val app = previous.getValue(pkg)
                PackageSnapshotEntity(pkg, app.label, app.firstInstallTimeMs, installed = false)
            }
            dao.applySync(
                snapshot = nowInstalled + nowRemoved,
                changes = changes.map { AppChangeEntity(packageName = it.packageName, label = it.label, type = it.type.name, atMs = it.atMs, exactTime = it.isExactTime) },
            )
        }
    }

    /** Called from the live broadcast: exact uninstall time. */
    suspend fun onPackageRemoved(packageName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val row = dao.snapshotOf(packageName) ?: return@withLock // not a launchable app we tracked
            if (!row.installed) return@withLock
            dao.applySync(
                snapshot = listOf(row.copy(installed = false)),
                changes = listOf(
                    AppChangeEntity(
                        packageName = packageName,
                        label = row.label,
                        type = AppChangeType.UNINSTALLED.name,
                        atMs = clock.millis(),
                        exactTime = true,
                    ),
                ),
            )
        }
    }
}
