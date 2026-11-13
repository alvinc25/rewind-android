package com.alvin.rewind.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alvin.rewind.domain.time.DigestSchedule
import java.time.Duration
import java.time.ZonedDateTime
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Efficiency: no polling, no own foreground service. WorkManager batches our runs with other
 * apps' work and respects Doze. Usage data is history the OS already keeps, so a few hours of
 * latency costs nothing; the UI also refreshes on open, so the user always sees today's data.
 */
object WorkScheduler {

    private const val COLLECTION_WORK = "rewind-collection"
    private const val DIGEST_WORK = "rewind-weekly-digest"

    fun scheduleCollection(context: Context) {
        val request = PeriodicWorkRequestBuilder<CollectionWorker>(12, TimeUnit.HOURS, 2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(COLLECTION_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Weekly periodic work whose first run is aligned to Sunday 19:00.
     * Trade-off: periodic work may drift by up to the flex window (and by an hour across DST);
     * for a "your week is ready" nudge that's acceptable, and it avoids a self-rescheduling worker
     * (enqueueing unique work with REPLACE from inside itself cancels the running instance).
     */
    fun scheduleWeeklyDigest(context: Context, clock: Clock) {
        val now = ZonedDateTime.now(clock)
        val initialDelay = Duration.between(now, DigestSchedule.nextRun(now))
        val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS, 1, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DIGEST_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
