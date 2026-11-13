package com.alvin.rewind.domain.apps

import com.alvin.rewind.domain.notifications.NotificationCountingPolicy
import com.alvin.rewind.domain.notifications.NotificationPost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoliciesTest {

    private fun post(
        pkg: String = "chat",
        ongoing: Boolean = false,
        summary: Boolean = false,
        update: Boolean = false,
        alertOnce: Boolean = false,
    ) = NotificationPost(pkg, 0, ongoing, summary, update, alertOnce)

    @Test
    fun `notification policy counts only notifications that could interrupt`() {
        val own = "me"
        assertTrue(NotificationCountingPolicy.shouldCount(post(), own))
        assertFalse(NotificationCountingPolicy.shouldCount(post(pkg = own), own))
        assertFalse(NotificationCountingPolicy.shouldCount(post(ongoing = true), own))
        assertFalse(NotificationCountingPolicy.shouldCount(post(summary = true), own))
        assertFalse(NotificationCountingPolicy.shouldCount(post(update = true, alertOnce = true), own))
        // new message in the same conversation re-alerts: counted
        assertTrue(NotificationCountingPolicy.shouldCount(post(update = true, alertOnce = false), own))
    }

    @Test
    fun `package diff detects installs with exact time and removals with detection time`() {
        val previous = mapOf(
            "keep" to InstalledApp("keep", "Keep", 100),
            "gone" to InstalledApp("gone", "Gone", 200),
        )
        val current = mapOf(
            "keep" to InstalledApp("keep", "Keep", 100),
            "new" to InstalledApp("new", "New", 5_000),
        )
        val changes = PackageDiff.diff(previous, current, nowMs = 9_000)

        assertEquals(
            listOf(
                AppChange("new", "New", AppChangeType.INSTALLED, 5_000, isExactTime = true),
                AppChange("gone", "Gone", AppChangeType.UNINSTALLED, 9_000, isExactTime = false),
            ),
            changes,
        )
    }

    @Test
    fun `reinstall is detected through a changed first install time`() {
        val changes = PackageDiff.diff(
            previous = mapOf("a" to InstalledApp("a", "A", 100)),
            current = mapOf("a" to InstalledApp("a", "A", 7_000)),
            nowMs = 9_000,
        )
        assertEquals(listOf(AppChangeType.INSTALLED), changes.map { it.type })
    }
}
