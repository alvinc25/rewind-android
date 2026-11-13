package com.alvin.rewind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailyAppUsageEntity::class,
        DailyDeviceEntity::class,
        NotificationPostEntity::class,
        PackageSnapshotEntity::class,
        AppChangeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RewindDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appsDao(): AppsDao

    companion object {
        fun create(context: Context): RewindDatabase =
            Room.databaseBuilder(context, RewindDatabase::class.java, "rewind.db").build()
    }
}
