package com.alvin.rewind.data.repo

import com.alvin.rewind.data.local.CollectionPrefs
import com.alvin.rewind.data.system.PermissionChecker
import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.repository.AccessRepository

class DeviceAccessRepository(
    private val permissions: PermissionChecker,
    private val prefs: CollectionPrefs,
) : AccessRepository {

    override fun status(): AccessStatus = permissions.current()

    override fun isOnboardingCompleted(): Boolean = prefs.onboardingDone

    override fun setOnboardingCompleted() {
        prefs.onboardingDone = true
    }
}
