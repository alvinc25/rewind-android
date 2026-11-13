package com.alvin.rewind.domain.usage

import com.alvin.rewind.domain.usage.UsageEventType.APP_BACKGROUND
import com.alvin.rewind.domain.usage.UsageEventType.APP_FOREGROUND
import com.alvin.rewind.domain.usage.UsageEventType.DEVICE_UNLOCK
import com.alvin.rewind.domain.usage.UsageEventType.SCREEN_OFF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionReducerTest {

    private val reducer = SessionReducer(mergeGapMs = 2_000)
    private fun ev(pkg: String, type: UsageEventType, t: Long) = UsageEvent(pkg, type, t)

    @Test
    fun `simple open and close produces one session`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_FOREGROUND, 1_000), ev("a", APP_BACKGROUND, 61_000)),
            windowStartMs = 0, windowEndMs = 100_000,
        )
        assertEquals(listOf(AppSession("a", 1_000, 61_000)), sessions)
    }

    @Test
    fun `switching app closes the previous one even without its pause event`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_FOREGROUND, 0), ev("b", APP_FOREGROUND, 10_000), ev("b", APP_BACKGROUND, 30_000)),
            0, 100_000,
        )
        assertEquals(listOf(AppSession("a", 0, 10_000), AppSession("b", 10_000, 30_000)), sessions)
    }

    @Test
    fun `screen off ends the current session`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_FOREGROUND, 0), ev("android", SCREEN_OFF, 5_000)),
            0, 100_000,
        )
        assertEquals(listOf(AppSession("a", 0, 5_000)), sessions)
    }

    @Test
    fun `app already open at window start is counted from the window start`() {
        val sessions = reducer.reduce(listOf(ev("a", APP_BACKGROUND, 20_000)), windowStartMs = 0, windowEndMs = 100_000)
        assertEquals(listOf(AppSession("a", 0, 20_000)), sessions)
    }

    @Test
    fun `stray background after other transitions is ignored`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_FOREGROUND, 0), ev("a", APP_BACKGROUND, 1_000), ev("b", APP_BACKGROUND, 5_000)),
            0, 100_000,
        )
        assertEquals(listOf(AppSession("a", 0, 1_000)), sessions)
    }

    @Test
    fun `session still open is closed at window end`() {
        val sessions = reducer.reduce(listOf(ev("a", APP_FOREGROUND, 90_000)), 0, 100_000)
        assertEquals(listOf(AppSession("a", 90_000, 100_000)), sessions)
    }

    @Test
    fun `moving between activities of the same app is one launch`() {
        val sessions = reducer.reduce(
            listOf(
                ev("a", APP_FOREGROUND, 0),
                ev("a", APP_BACKGROUND, 10_000), // activity 1 paused
                ev("a", APP_FOREGROUND, 10_300), // activity 2 resumed
                ev("a", APP_BACKGROUND, 20_000),
            ),
            0, 100_000,
        )
        assertEquals(listOf(AppSession("a", 0, 20_000)), sessions)
    }

    @Test
    fun `reopening the same app after the gap is a second launch`() {
        val sessions = reducer.reduce(
            listOf(
                ev("a", APP_FOREGROUND, 0), ev("a", APP_BACKGROUND, 10_000),
                ev("a", APP_FOREGROUND, 40_000), ev("a", APP_BACKGROUND, 50_000),
            ),
            0, 100_000,
        )
        assertEquals(2, sessions.size)
    }

    @Test
    fun `events outside the window are ignored and unlocks do not affect sessions`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_FOREGROUND, -5_000), ev("android", DEVICE_UNLOCK, 1_000), ev("b", APP_FOREGROUND, 200_000)),
            0, 100_000,
        )
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `unsorted input is handled`() {
        val sessions = reducer.reduce(
            listOf(ev("a", APP_BACKGROUND, 5_000), ev("a", APP_FOREGROUND, 1_000)),
            0, 100_000,
        )
        assertEquals(listOf(AppSession("a", 1_000, 5_000)), sessions)
    }
}
