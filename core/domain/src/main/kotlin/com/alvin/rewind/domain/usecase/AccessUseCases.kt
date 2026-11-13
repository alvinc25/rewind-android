package com.alvin.rewind.domain.usecase

import com.alvin.rewind.domain.access.AccessStatus
import com.alvin.rewind.domain.repository.AccessRepository

class GetAccessStatusUseCase(private val access: AccessRepository) {
    operator fun invoke(): AccessStatus = access.status()
}

/** Setup is complete when the user finished onboarding *and* the required access is still granted. */
class IsSetupCompleteUseCase(private val access: AccessRepository) {
    operator fun invoke(): Boolean = access.isOnboardingCompleted() && access.status().usageAccess
}

/** Finishes onboarding. Refuses (returns false) if the one required permission is missing. */
class CompleteOnboardingUseCase(private val access: AccessRepository) {
    operator fun invoke(): Boolean {
        if (!access.status().usageAccess) return false
        access.setOnboardingCompleted()
        return true
    }
}
