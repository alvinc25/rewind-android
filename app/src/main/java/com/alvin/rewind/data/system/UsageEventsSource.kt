package com.alvin.rewind.data.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.alvin.rewind.domain.usage.UsageEvent
import com.alvin.rewind.domain.usage.UsageEventType

/**
 * Thin adapter over [UsageStatsManager.queryEvents]. The only place that knows Android event codes.
 * Requires "Usage access"; without it the OS simply returns no events.
 */
class UsageEventsSource(context: Context) {

    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    fun events(startMs: Long, endMs: Long): List<UsageEvent> {
        val events = usageStatsManager.queryEvents(startMs, endMs) ?: return emptyList()
        val result = ArrayList<UsageEvent>()
        val event = UsageEvents.Event() // reused: queryEvents is an iterator over a parcel
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.APP_FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.APP_BACKGROUND
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventType.DEVICE_UNLOCK
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_SHUTDOWN
                else -> null
            } ?: continue
            result += UsageEvent(event.packageName, type, event.timeStamp)
        }
        return result
    }
}
