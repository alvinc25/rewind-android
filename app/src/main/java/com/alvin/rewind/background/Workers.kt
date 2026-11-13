package com.alvin.rewind.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alvin.rewind.RewindApp
import com.alvin.rewind.ui.notification.DigestNotifier
import kotlinx.coroutines.CancellationException

/*
 * Workers are entry points, like an Activity: they receive use cases from the composition root
 * and contain no business rules themselves.
 */

/** Twice a day: copy OS usage history into our store, diff installed apps, purge old data. */
class CollectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val refreshActivityData = (applicationContext as RewindApp).container.refreshActivityData
        return try {
            refreshActivityData()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Collection failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "CollectionWorker"
        const val MAX_ATTEMPTS = 3
    }
}

/** Sunday evening: "your week in review is ready". The decision is made by GetWeeklyDigestUseCase. */
class WeeklyDigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val getWeeklyDigest = (applicationContext as RewindApp).container.getWeeklyDigest
        return try {
            getWeeklyDigest()?.let { report -> DigestNotifier.show(applicationContext, report) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WeeklyDigestWorker", "Digest failed", e)
            Result.success() // a missed digest isn't worth retrying into Monday
        }
    }
}
