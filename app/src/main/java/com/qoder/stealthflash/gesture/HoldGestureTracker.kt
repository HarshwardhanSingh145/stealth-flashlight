package com.qoder.stealthflash.gesture

/**
 * Pure state machine behind the "hold the switch for five seconds" gesture.
 *
 * Kept free of Compose and Android types so the timing rules can be unit tested against a
 * fake clock. The caller drives it from `pointerInput` and a frame ticker.
 */
sealed interface HoldOutcome {
    /** Nothing to report this tick. */
    data object None : HoldOutcome

    /** A short, clean tap: perform the normal flashlight toggle. */
    data object ToggleTorch : HoldOutcome

    /** Held longer than a tap but released before the threshold. Deliberately does nothing. */
    data object HoldAborted : HoldOutcome

    /** Threshold reached while still pressed. Fires at most once per press. */
    data object HoldCompleted : HoldOutcome
}

class HoldGestureTracker(
    private val holdDurationMillis: Long = DEFAULT_HOLD_MILLIS,
    private val tapMaxMillis: Long = DEFAULT_TAP_MILLIS,
    val dotCount: Int = DEFAULT_DOT_COUNT,
) {
    init {
        require(holdDurationMillis > tapMaxMillis) { "hold threshold must exceed the tap window" }
        require(dotCount > 0)
    }

    var isPressed: Boolean = false
        private set

    /** 0f..1f through the hold; used to light the dot meter. */
    var progress: Float = 0f
        private set

    /** True once the threshold has been crossed and until the finger lifts. */
    var isCompleted: Boolean = false
        private set

    /** Lit dot count for the meter, derived from [progress]. */
    val litDots: Int
        get() = when {
            !isPressed -> 0
            else -> (progress * dotCount).toInt().coerceIn(0, dotCount)
        }

    private var downAtMillis = 0L

    fun onPointerDown(nowMillis: Long) {
        if (isPressed) return // A second finger on the same pointer input must not restart timing.
        isPressed = true
        isCompleted = false
        downAtMillis = nowMillis
        progress = 0f
    }

    /** Drive from a frame callback so [progress] animates smoothly. */
    fun onTick(nowMillis: Long): HoldOutcome {
        if (!isPressed || isCompleted) return HoldOutcome.None
        val elapsed = nowMillis - downAtMillis
        if (elapsed < 0) return HoldOutcome.None
        progress = (elapsed.toFloat() / holdDurationMillis).coerceIn(0f, 1f)
        return if (elapsed >= holdDurationMillis) {
            progress = 1f
            isCompleted = true
            HoldOutcome.HoldCompleted
        } else {
            HoldOutcome.None
        }
    }

    fun onPointerUp(nowMillis: Long): HoldOutcome {
        if (!isPressed) return HoldOutcome.None
        val elapsed = nowMillis - downAtMillis
        isPressed = false
        val outcome = when {
            isCompleted -> HoldOutcome.None // Already unlocked; swallow the release.
            elapsed <= tapMaxMillis -> HoldOutcome.ToggleTorch
            else -> HoldOutcome.HoldAborted
        }
        isCompleted = false
        progress = 0f
        return outcome
    }

    /**
     * Interrupted touch: finger slid off the target, a system gesture stole the stream, or the
     * pointer was cancelled. Never toggles and never unlocks.
     */
    fun onInterrupted() {
        isPressed = false
        isCompleted = false
        progress = 0f
    }

    fun reset() = onInterrupted()

    companion object {
        const val DEFAULT_HOLD_MILLIS = 5_000L
        const val DEFAULT_TAP_MILLIS = 250L
        const val DEFAULT_DOT_COUNT = 18
    }
}
