package com.qoder.stealthflash.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five-second gesture is the security boundary of the whole app, so every branch of its
 * timing is pinned down here against a fake clock rather than left to on-device poking.
 */
class HoldGestureTrackerTest {

    private fun tracker() = HoldGestureTracker(holdDurationMillis = 5_000, tapMaxMillis = 250)

    @Test
    fun `short tap toggles the torch and never unlocks`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.ToggleTorch, t.onPointerUp(120))
    }

    @Test
    fun `full five second hold unlocks`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.None, t.onTick(4_999))
        assertEquals(HoldOutcome.HoldCompleted, t.onTick(5_000))
        assertTrue(t.isCompleted)
        // Lifting after the unlock must not also flip the torch.
        assertEquals(HoldOutcome.None, t.onPointerUp(5_400))
    }

    @Test
    fun `release before the threshold cancels without toggling`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.None, t.onTick(3_000))
        assertEquals(HoldOutcome.HoldAborted, t.onPointerUp(3_200))
        assertFalse(t.isPressed)
        assertEquals(0f, t.progress, 0.0001f)
    }

    @Test
    fun `completion fires once per press`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.HoldCompleted, t.onTick(5_000))
        assertEquals(HoldOutcome.None, t.onTick(6_000))
        assertEquals(HoldOutcome.None, t.onTick(9_000))
    }

    @Test
    fun `repeated taps each toggle, never accumulate into an unlock`() {
        val t = tracker()
        var now = 0L
        repeat(20) {
            t.onPointerDown(now)
            assertEquals(HoldOutcome.ToggleTorch, t.onPointerUp(now + 60))
            now += 200
        }
        assertFalse(t.isCompleted)
    }

    @Test
    fun `several short taps with a pause in the middle still do not unlock`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.ToggleTorch, t.onPointerUp(100))
        t.onPointerDown(500)
        assertEquals(HoldOutcome.HoldAborted, t.onPointerUp(2_500))
        t.onPointerDown(3_000)
        assertEquals(HoldOutcome.HoldAborted, t.onPointerUp(6_500))
        assertFalse(t.isCompleted)
    }

    @Test
    fun `interrupted touch resets elapsed time`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(HoldOutcome.None, t.onTick(4_900))
        t.onInterrupted()
        assertFalse(t.isPressed)

        // A fresh press starts from zero, so the earlier 4.9 s cannot be resumed.
        t.onPointerDown(5_000)
        assertEquals(HoldOutcome.None, t.onTick(9_000))
        assertEquals(HoldOutcome.HoldCompleted, t.onTick(10_000))
    }

    @Test
    fun `a second finger does not restart the timer`() {
        val t = tracker()
        t.onPointerDown(0)
        t.onPointerDown(4_000)
        assertEquals(HoldOutcome.HoldCompleted, t.onTick(5_000))
    }

    @Test
    fun `progress is monotonic and clamped`() {
        val t = tracker()
        t.onPointerDown(0)
        t.onTick(0)
        assertEquals(0f, t.progress, 0.0001f)
        t.onTick(2_500)
        assertEquals(0.5f, t.progress, 0.0001f)
        t.onTick(5_000)
        assertEquals(1f, t.progress, 0.0001f)
        t.onTick(50_000)
        assertEquals(1f, t.progress, 0.0001f)
    }

    @Test
    fun `meter fills bottom to top across the hold`() {
        val t = tracker()
        t.onPointerDown(0)
        assertEquals(0, t.litDots)
        t.onTick(1_250)
        assertEquals(4, t.litDots)
        t.onTick(2_500)
        assertEquals(9, t.litDots)
        t.onTick(5_000)
        assertEquals(18, t.litDots)
    }

    @Test
    fun `meter is dark when nothing is pressed`() {
        val t = tracker()
        t.onTick(1_000)
        assertEquals(0, t.litDots)
        assertEquals(0f, t.progress, 0.0001f)
    }

    @Test
    fun `out of order clock readings are ignored`() {
        val t = tracker()
        t.onPointerDown(1_000)
        assertEquals(HoldOutcome.None, t.onTick(500))
        assertEquals(0f, t.progress, 0.0001f)
        assertEquals(HoldOutcome.HoldCompleted, t.onTick(6_000))
    }

    @Test
    fun `release without a press is inert`() {
        val t = tracker()
        assertEquals(HoldOutcome.None, t.onPointerUp(1_000))
    }

    @Test
    fun `reset clears every flag`() {
        val t = tracker()
        t.onPointerDown(0)
        t.onTick(5_000)
        t.reset()
        assertFalse(t.isPressed)
        assertFalse(t.isCompleted)
        assertEquals(0f, t.progress, 0.0001f)
    }
}
