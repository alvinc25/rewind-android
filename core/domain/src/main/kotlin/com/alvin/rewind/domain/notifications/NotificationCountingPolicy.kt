package com.alvin.rewind.domain.notifications

/**
 * Metadata about one posted notification. We never look at title/text/extras:
 * counting doesn't need content, and not reading it is a privacy guarantee we can state plainly.
 */
data class NotificationPost(
    val packageName: String,
    val postedAtMs: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    /** The same notification key was already showing (an update rather than a new notification). */
    val isUpdateOfActive: Boolean,
    /** FLAG_ONLY_ALERT_ONCE: the app asked the system not to alert again on updates. */
    val onlyAlertOnce: Boolean,
)

/**
 * "How many notifications did I receive?" is ambiguous. A download progress bar can be re-posted
 * hundreds of times; a chat app posts one summary plus one child per conversation.
 *
 * Definition chosen: count notifications that could have interrupted the user.
 *  - ongoing (music player, navigation, foreground services): not counted
 *  - group summaries: not counted (the children are)
 *  - silent updates of a notification already on screen: not counted
 *  - an update that alerts again (new message in the same conversation): counted
 */
object NotificationCountingPolicy {

    fun shouldCount(post: NotificationPost, ownPackage: String): Boolean = when {
        post.packageName == ownPackage -> false
        post.isOngoing -> false
        post.isGroupSummary -> false
        post.isUpdateOfActive && post.onlyAlertOnce -> false
        else -> true
    }
}
