package com.alvin.rewind.data.collect

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.alvin.rewind.RewindApp
import com.alvin.rewind.domain.notifications.NotificationPost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Bound by the system once the user grants "Notification access". The OS keeps it alive,
 * so we don't need (and don't have) a foreground service of our own.
 *
 * We read only metadata: package, post time, key and flags. Never `notification.extras`.
 */
class ActivityNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container get() = (application as RewindApp).container

    /** Keys currently on screen, to tell a new notification from an update of an existing one. */
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var packageReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // app update, not install/uninstall
            scope.launch {
                runCatching {
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> container.packageTracker.sync()
                        Intent.ACTION_PACKAGE_REMOVED -> container.packageTracker.onPackageRemoved(packageName)
                    }
                }.onFailure { Log.w(TAG, "Package change handling failed", it) }
            }
        }
    }

    override fun onListenerConnected() {
        activeKeys.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach { activeKeys += it.key }
        container.prefs.markNotificationTrackingStarted(System.currentTimeMillis())

        if (!packageReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            // System broadcasts are delivered to non-exported receivers.
            ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            packageReceiverRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        unregisterPackageReceiver()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isUpdate = !activeKeys.add(sbn.key)
        val flags = sbn.notification.flags
        val post = NotificationPost(
            packageName = sbn.packageName,
            postedAtMs = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isUpdateOfActive = isUpdate,
            onlyAlertOnce = (flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0,
        )
        scope.launch {
            runCatching { container.notificationRecorder.record(post) }
                .onFailure { Log.w(TAG, "Failed to record notification", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        activeKeys.remove(sbn.key)
    }

    override fun onDestroy() {
        unregisterPackageReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private fun unregisterPackageReceiver() {
        if (packageReceiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            packageReceiverRegistered = false
        }
    }

    private companion object {
        const val TAG = "RewindNotifListener"
    }
}
