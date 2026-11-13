package com.alvin.rewind.di

import android.content.ComponentName
import android.content.Context
import com.alvin.rewind.data.collect.ActivityNotificationListener
import com.alvin.rewind.data.collect.DataRetention
import com.alvin.rewind.data.collect.NotificationRecorder
import com.alvin.rewind.data.collect.PackageTracker
import com.alvin.rewind.data.collect.UsageIngestor
import com.alvin.rewind.data.local.CollectionPrefs
import com.alvin.rewind.data.local.RewindDatabase
import com.alvin.rewind.data.repo.DeviceAccessRepository
import com.alvin.rewind.data.repo.DeviceActivityRepository
import com.alvin.rewind.data.repo.SampleActivityRepository
import com.alvin.rewind.data.system.AppCatalog
import com.alvin.rewind.data.system.PermissionChecker
import com.alvin.rewind.data.system.UsageEventsSource
import com.alvin.rewind.domain.repository.AccessRepository
import com.alvin.rewind.domain.repository.ActivityRepository
import com.alvin.rewind.domain.usecase.CompleteOnboardingUseCase
import com.alvin.rewind.domain.usecase.GetAccessStatusUseCase
import com.alvin.rewind.domain.usecase.GetCurrentWeekUseCase
import com.alvin.rewind.domain.usecase.GetWeeklyDigestUseCase
import com.alvin.rewind.domain.usecase.GetWeeklyReportUseCase
import com.alvin.rewind.domain.usecase.IsSetupCompleteUseCase
import com.alvin.rewind.domain.usecase.RefreshActivityDataUseCase
import com.alvin.rewind.ui.MainViewModel
import java.time.Clock

/**
 * Composition root (manual dependency injection).
 *
 * This is the only place that knows every layer. It builds the data layer, binds it to the
 * domain's repository interfaces, and exposes use cases to the presentation layer.
 * With ~20 objects a hand-written graph is easy to read; each block maps 1:1 onto a Hilt module.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Injected everywhere instead of calling System.currentTimeMillis(), so time is testable. */
    val clock: Clock = Clock.systemDefaultZone()

    // ---- Data layer ---------------------------------------------------------------------------

    private val database by lazy { RewindDatabase.create(appContext) }
    private val permissionChecker by lazy { PermissionChecker(appContext) }
    private val catalog by lazy { AppCatalog(appContext) }

    /** Used directly by the notification listener (a data-layer component bound by the OS). */
    val prefs by lazy { CollectionPrefs(appContext) }
    val packageTracker by lazy { PackageTracker(catalog, database.appsDao(), clock) }
    val notificationRecorder by lazy { NotificationRecorder(database.notificationDao(), appContext.packageName) }

    private val usageIngestor by lazy {
        UsageIngestor(UsageEventsSource(appContext), database.usageDao(), catalog, permissionChecker, clock)
    }
    private val retention by lazy { DataRetention(database.usageDao(), database.notificationDao(), clock) }

    // ---- Repository bindings (domain interface <- data implementation) ------------------------

    private val deviceActivity: ActivityRepository by lazy {
        DeviceActivityRepository(
            usageDao = database.usageDao(),
            notificationDao = database.notificationDao(),
            appsDao = database.appsDao(),
            ingestor = usageIngestor,
            packageTracker = packageTracker,
            retention = retention,
            catalog = catalog,
            permissions = permissionChecker,
            prefs = prefs,
            clock = clock,
        )
    }
    private val sampleActivity: ActivityRepository by lazy { SampleActivityRepository(clock) }
    private val access: AccessRepository by lazy { DeviceAccessRepository(permissionChecker, prefs) }

    // ---- Use cases (what the presentation layer is allowed to see) ----------------------------

    val getWeeklyReport by lazy { GetWeeklyReportUseCase(deviceActivity, sampleActivity) }
    val refreshActivityData by lazy { RefreshActivityDataUseCase(deviceActivity) }
    val getCurrentWeek by lazy { GetCurrentWeekUseCase(clock) }
    val getAccessStatus by lazy { GetAccessStatusUseCase(access) }
    val isSetupComplete by lazy { IsSetupCompleteUseCase(access) }
    val completeOnboarding by lazy { CompleteOnboardingUseCase(access) }
    val getWeeklyDigest by lazy {
        GetWeeklyDigestUseCase(getAccessStatus, refreshActivityData, getWeeklyReport, getCurrentWeek)
    }

    /** Lets the UI deep-link to our row in notification-access settings without importing the data layer. */
    val notificationListenerComponent: ComponentName by lazy {
        ComponentName(appContext, ActivityNotificationListener::class.java)
    }

    // ---- Presentation -------------------------------------------------------------------------

    fun mainViewModel() = MainViewModel(
        getWeeklyReport = getWeeklyReport,
        refreshActivityData = refreshActivityData,
        getCurrentWeek = getCurrentWeek,
        getAccessStatus = getAccessStatus,
        isSetupComplete = isSetupComplete,
        completeOnboarding = completeOnboarding,
    )
}
