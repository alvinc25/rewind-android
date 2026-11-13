package com.alvin.rewind.data.collect

import com.alvin.rewind.data.local.NotificationDao
import com.alvin.rewind.data.local.NotificationPostEntity
import com.alvin.rewind.domain.notifications.NotificationCountingPolicy
import com.alvin.rewind.domain.notifications.NotificationPost

class NotificationRecorder(
    private val dao: NotificationDao,
    private val ownPackage: String,
) {
    suspend fun record(post: NotificationPost) {
        if (NotificationCountingPolicy.shouldCount(post, ownPackage)) {
            dao.insert(NotificationPostEntity(packageName = post.packageName, postedAtMs = post.postedAtMs))
        }
    }
}
