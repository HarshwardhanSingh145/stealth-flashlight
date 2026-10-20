package com.qoder.stealthflash.flashlight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.qoder.stealthflash.StealthFlashApp
import com.qoder.stealthflash.gesture.HoldGestureTracker
import com.qoder.stealthflash.gesture.HoldOutcome
import com.qoder.stealthflash.torch.TorchController
import com.qoder.stealthflash.torch.TorchUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Emitted when the five-second hold completes; the activity navigates to the private area. */
sealed interface FlashlightEvent {
    data object UnlockRequested : FlashlightEvent
}

class FlashlightViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as StealthFlashApp

    val torch: TorchController = container.torch
    val torchState: StateFlow<TorchUiState> = torch.state

    /**
     * One tracker per ViewModel, but the gesture itself is transient UI state. Reset on every
     * navigation return so a half-finished hold can never resume in the private area.
     */
    val tracker = HoldGestureTracker()

    private val _events = MutableSharedFlow<FlashlightEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FlashlightEvent> = _events.asSharedFlow()

    fun onOutcome(outcome: HoldOutcome) {
        when (outcome) {
            HoldOutcome.ToggleTorch -> torch.toggle()
            HoldOutcome.HoldCompleted -> _events.tryEmit(FlashlightEvent.UnlockRequested)
            HoldOutcome.HoldAborted, HoldOutcome.None -> Unit
        }
    }

    fun cancelHold() = tracker.onInterrupted()

    override fun onCleared() {
        tracker.reset()
        super.onCleared()
    }
}
