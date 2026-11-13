package com.alvin.rewind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class UsageDao {

    @Query("SELECT * FROM daily_device WHERE day = :day")
    abstract suspend fun device(day: String): DailyDeviceEntity?

    @Query("SELECT * FROM daily_device WHERE day >= :fromDay AND day < :toDay ORDER BY day")
    abstract suspend fun devices(fromDay: String, toDay: String): List<DailyDeviceEntity>

    @Query("SELECT * FROM daily_app_usage WHERE day >= :fromDay AND day < :toDay")
    abstract suspend fun appUsage(fromDay: String, toDay: String): List<DailyAppUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDevice(row: DailyDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAppUsage(rows: List<DailyAppUsageEntity>)

    @Query("DELETE FROM daily_app_usage WHERE day = :day")
    abstract suspend fun deleteAppUsage(day: String)

    /** Idempotent: re-ingesting a day replaces it entirely (today is re-ingested on every refresh). */
    @Transaction
    open suspend fun replaceDay(device: DailyDeviceEntity, apps: List<DailyAppUsageEntity>) {
        deleteAppUsage(device.day)
        insertAppUsage(apps)
        insertDevice(device)
    }

    @Query("DELETE FROM daily_app_usage WHERE day < :beforeDay")
    abstract suspend fun deleteAppUsageBefore(beforeDay: String)

    @Query("DELETE FROM daily_device WHERE day < :beforeDay")
    abstract suspend fun deleteDevicesBefore(beforeDay: String)
}

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(row: NotificationPostEntity)

    @Query("SELECT * FROM notification_posts WHERE postedAtMs >= :fromMs AND postedAtMs < :toMs")
    suspend fun between(fromMs: Long, toMs: Long): List<NotificationPostEntity>

    @Query("DELETE FROM notification_posts WHERE postedAtMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}

@Dao
abstract class AppsDao {

    @Query("SELECT * FROM package_snapshot")
    abstract suspend fun snapshot(): List<PackageSnapshotEntity>

    @Query("SELECT * FROM package_snapshot WHERE packageName = :packageName")
    abstract suspend fun snapshotOf(packageName: String): PackageSnapshotEntity?

    @Query("SELECT * FROM app_changes WHERE atMs >= :fromMs AND atMs < :toMs ORDER BY atMs")
    abstract suspend fun changesBetween(fromMs: Long, toMs: Long): List<AppChangeEntity>

    @Upsert
    abstract suspend fun upsertSnapshot(rows: List<PackageSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChanges(rows: List<AppChangeEntity>)

    @Transaction
    open suspend fun applySync(snapshot: List<PackageSnapshotEntity>, changes: List<AppChangeEntity>) {
        upsertSnapshot(snapshot)
        insertChanges(changes)
    }
}
