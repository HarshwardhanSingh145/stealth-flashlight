package com.qoder.stealthflash.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TorchAvailability { Available, NoFlashUnit, NoCameraManager }

enum class TorchFailure { Unsupported, CameraInUse, CameraDisabled, PermissionDenied, Hardware }

data class TorchUiState(
    val isOn: Boolean = false,
    val availability: TorchAvailability = TorchAvailability.Available,
    val failure: TorchFailure? = null,
) {
    val operable: Boolean get() = availability == TorchAvailability.Available
}

/**
 * Owns the rear LED through [CameraManager.setTorchMode].
 *
 * Torch mode needs no runtime permission, so none is requested. The LED is forced off in
 * [pause] because the OS silently drops torch ownership when another app opens the camera;
 * trusting a remembered "on" flag would leave the switch showing a state the hardware is not
 * in. [CameraManager.TorchCallback] is the authority while the screen is visible.
 */
class TorchController(context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager: CameraManager? =
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cameraId: String? = findTorchCamera(cameraManager)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TorchUiState> = _state.asStateFlow()

    private var callbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != this@TorchController.cameraId) return
            _state.update { it.copy(isOn = enabled, failure = null) }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != this@TorchController.cameraId) return
            // Another client owns the camera now; our torch cannot be lit.
            _state.update { it.copy(isOn = false, failure = TorchFailure.CameraInUse) }
        }
    }

    fun resume() {
        if (callbackRegistered || cameraManager == null || cameraId == null) return
        @Suppress("DEPRECATION") // Handler overload is the only one available below API 30.
        runCatching { cameraManager.registerTorchCallback(mainHandler, torchCallback) }
            .onSuccess { callbackRegistered = true }
    }

    /** Leaves the hardware in a known state: LED off, no live camera client registered. */
    fun pause() {
        if (callbackRegistered) {
            runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
            callbackRegistered = false
        }
        if (_state.value.isOn) {
            runCatching { cameraId?.let { id -> cameraManager?.setTorchMode(id, false) } }
            _state.update { it.copy(isOn = false) }
        }
    }

    fun toggle() = setOn(!_state.value.isOn)

    /** @return true when the LED actually changed state. */
    fun setOn(on: Boolean): Boolean {
        val manager = cameraManager
        val id = cameraId
        if (manager == null) {
            _state.update { it.copy(availability = TorchAvailability.NoCameraManager, isOn = false) }
            return false
        }
        if (id == null) {
            _state.update { it.copy(availability = TorchAvailability.NoFlashUnit, isOn = false) }
            return false
        }
        return try {
            manager.setTorchMode(id, on)
            _state.update { it.copy(isOn = on, failure = null) }
            true
        } catch (e: CameraAccessException) {
            _state.update { it.copy(isOn = !on, failure = classify(e)) }
            false
        } catch (e: IllegalArgumentException) {
            // The id stopped existing (profile change, multi-user). Resolve once, then give up.
            cameraId = findTorchCamera(manager)
            _state.update {
                it.copy(
                    isOn = false,
                    availability = if (cameraId == null) TorchAvailability.NoFlashUnit else it.availability,
                    failure = TorchFailure.Unsupported,
                )
            }
            false
        } catch (e: SecurityException) {
            _state.update { it.copy(isOn = false, failure = TorchFailure.PermissionDenied) }
            false
        } catch (e: RuntimeException) {
            // Some OEM stacks throw bare RuntimeExceptions when the HAL is busy.
            _state.update { it.copy(isOn = !on, failure = TorchFailure.Hardware) }
            false
        }
    }

    private fun initialState(): TorchUiState {
        val availability = when {
            cameraManager == null -> TorchAvailability.NoCameraManager
            cameraId == null &&
                !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) ->
                TorchAvailability.NoFlashUnit
            cameraId == null -> TorchAvailability.NoFlashUnit
            else -> TorchAvailability.Available
        }
        return TorchUiState(availability = availability)
    }

    private fun classify(e: CameraAccessException): TorchFailure {
        // getReason() only exists from API 29; on 26-28 the bare exception is all we get.
        val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) e.reason else -1
        return when (reason) {
            CameraAccessException.CAMERA_IN_USE,
            CameraAccessException.CAMERA_CONCURRENT_VIDEO_STREAMS_DISABLING,
            -> TorchFailure.CameraInUse

            CameraAccessException.CAMERA_DISABLED -> TorchFailure.CameraDisabled
            else -> TorchFailure.Hardware
        }
    }

    private companion object {
        fun findTorchCamera(manager: CameraManager?): String? {
            if (manager == null) return null
            return runCatching {
                val ids = manager.cameraIdList
                val withFlash = ids.filter { id ->
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                // Prefer the rear LED; a front flash is rarer and would be the wrong light.
                withFlash.firstOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: withFlash.firstOrNull()
            }.getOrNull()
        }
    }
}
