package com.alvin.rewind.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Storage design: keep aggregates, not raw events.
 *  - A week of raw usage events can be tens of thousands of rows; a day aggregate is ~30 rows.
 *  - Aggregates are all the report needs, and they hold far less personal detail.
 * Days are ISO strings ("2026-11-09"): sortable, readable in a DB inspector, no converters needed.
 */

@Entity(tableName = "daily_app_usage", primaryKeys = ["day", "packageName"])
data class DailyAppUsageEntity(
    val day: String,
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
)

@Entity(tableName = "daily_device")
data class DailyDeviceEntity(
    @PrimaryKey val day: String,
    val unlocks: Int,
    /** 24 comma-separated longs. */
    val hourlyMsCsv: String,
    /** The day is over and was ingested after it ended: never needs recomputing. */
    val isComplete: Boolean,
    val updatedAtMs: Long,
)

/** One row per *counted* notification. Metadata only: never title or text. */
@Entity(tableName = "notification_posts", indices = [Index("postedAtMs")])
data class NotificationPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val postedAtMs: Long,
)

/** Last known state of each launchable app, so we can diff and still name apps after uninstall. */
@Entity(tableName = "package_snapshot")
data class PackageSnapshotEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val firstInstallTimeMs: Long,
    val installed: Boolean,
)

@Entity(
    tableName = "app_changes",
    indices = [Index(value = ["packageName", "type", "atMs"], unique = true), Index("atMs")],
)
data class AppChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    /** AppChangeType name */
    val type: String,
    val atMs: Long,
    val exactTime: Boolean,
)
