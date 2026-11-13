package com.alvin.rewind.domain.usage

/**
 * Turns a stream of raw foreground/background events into app sessions.
 *
 * Why not just use `UsageStatsManager.queryUsageStats()`? Its buckets are aligned to the system's own
 * intervals (not the user's local midnight), they are coarse, and they don't expose "how many times was
 * this app opened". Rebuilding sessions from events gives us exact day boundaries, launch counts and
 * hour-of-day distribution from a single source of truth, and it is pure logic we can unit test.
 *
 * Model: a phone has at most one foreground app at a time (split-screen is deliberately out of scope).
 *
 * Rules
 *  1. FOREGROUND(p): if another app is open it ends now; p becomes the current app.
 *  2. BACKGROUND(p): if p is the current app, its session ends now. Otherwise ignored.
 *  3. SCREEN_OFF / DEVICE_SHUTDOWN: the current session (if any) ends now.
 *  4. If the very first transition in the window is BACKGROUND(p) with nothing open, p was already
 *     in the foreground when the window started (e.g. user was in an app at midnight).
 *  5. A session still open at the end of the window is closed at the window end.
 *  6. Consecutive sessions of the same app separated by <= [mergeGapMs] are merged, because moving
 *     between two activities of the same app produces PAUSED(A) -> RESUMED(B) and must not count as
 *     a new "open".
 */
class SessionReducer(private val mergeGapMs: Long = DEFAULT_MERGE_GAP_MS) {

    fun reduce(events: List<UsageEvent>, windowStartMs: Long, windowEndMs: Long): List<AppSession> {
        require(windowEndMs >= windowStartMs) { "Window end before start" }

        val inWindow = events
            .filter { it.timestampMs in windowStartMs until windowEndMs }
            .sortedBy { it.timestampMs } // stable: keeps OS order for equal timestamps

        val sessions = mutableListOf<AppSession>()
        var current: String? = null
        var currentStart = 0L
        var sawTransition = false

        fun closeCurrent(at: Long) {
            val pkg = current ?: return
            if (at > currentStart) sessions += AppSession(pkg, currentStart, at)
            current = null
        }

        for (event in inWindow) {
            when (event.type) {
                UsageEventType.APP_FOREGROUND -> {
                    if (current != event.packageName) {
                        closeCurrent(event.timestampMs)
                        current = event.packageName
                        currentStart = event.timestampMs
                    }
                    sawTransition = true
                }

                UsageEventType.APP_BACKGROUND -> {
                    if (current == event.packageName) {
                        closeCurrent(event.timestampMs)
                    } else if (current == null && !sawTransition && event.timestampMs > windowStartMs) {
                        // Rule 4: carried over from before the window.
                        sessions += AppSession(event.packageName, windowStartMs, event.timestampMs)
                    }
                    sawTransition = true
                }

                UsageEventType.SCREEN_OFF, UsageEventType.DEVICE_SHUTDOWN -> {
                    closeCurrent(event.timestampMs)
                    sawTransition = true
                }

                UsageEventType.DEVICE_UNLOCK -> Unit
            }
        }
        closeCurrent(windowEndMs)

        return merge(sessions)
    }

    private fun merge(sessions: List<AppSession>): List<AppSession> {
        if (sessions.isEmpty()) return sessions
        val merged = ArrayList<AppSession>(sessions.size)
        var acc = sessions.first()
        for (next in sessions.drop(1)) {
            acc = if (next.packageName == acc.packageName && next.startMs - acc.endMs <= mergeGapMs) {
                acc.copy(endMs = maxOf(acc.endMs, next.endMs))
            } else {
                merged += acc
                next
            }
        }
        merged += acc
        return merged
    }

    companion object {
        const val DEFAULT_MERGE_GAP_MS = 2_000L
    }
}
