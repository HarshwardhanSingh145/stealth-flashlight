package com.qoder.stealthflash.vault

import android.app.Application
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qoder.stealthflash.R
import com.qoder.stealthflash.StealthFlashApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VaultPhase {
    /** Nothing shown yet; the gesture has not completed. */
    Idle,

    /** First run: choose a PIN. */
    PinSetup,

    /** Re-enter it once, so a mistyped digit is caught now rather than at the next lock. */
    PinConfirm,

    /** PIN exists; asking for it. */
    Unlock,

    /** Inside the private area. */
    Unlocked,
}

enum class VaultSort { Name, RecentlyAdded }

enum class VaultTab { Private, All }

data class VaultUiState(
    val phase: VaultPhase = VaultPhase.Idle,
    val pinEntry: String = "",
    val pinIsConfirm: Boolean = false,
    @StringRes val messageRes: Int? = null,
    val lockoutRemainingSeconds: Long = 0,
    val biometricOffered: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricSupported: Boolean = false,
    val isLoadingApps: Boolean = false,
    val apps: List<ManagedApp> = emptyList(),
    val privatePackages: Set<String> = emptySet(),
    val addedOrder: Map<String, Long> = emptyMap(),
    val query: String = "",
    val sort: VaultSort = VaultSort.Name,
    val tab: VaultTab = VaultTab.Private,
    val showSystemApps: Boolean = false,
) {
    val visibleApps: List<ManagedApp>
        get() {
            val base = when (tab) {
                VaultTab.Private -> apps.filter { it.packageName in privatePackages }
                VaultTab.All -> apps
            }
            val filtered = if (showSystemApps) base else base.filterNot { it.isSystemApp }
            val needle = query.trim().lowercase()
            val matched = if (needle.isEmpty()) {
                filtered
            } else {
                filtered.filter {
                    it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
                }
            }
            return when (sort) {
                VaultSort.Name -> matched.sortedBy { it.label.lowercase() }
                VaultSort.RecentlyAdded -> matched.sortedByDescending {
                    addedOrder[it.packageName] ?: 0L
                }
            }
        }

    val isPinAcceptedLength: Boolean get() = pinEntry.length >= PinHasher.MIN_LENGTH
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as StealthFlashApp
    private val store = container.vaultStore
    private val apps = container.installedApps
    private val biometrics = container.biometrics

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    private var pendingPin: String? = null
    private var lockoutJob: Job? = null

    /** Entry point from the completed five-second hold. */
    fun onGateOpened() {
        viewModelScope.launch {
            val snapshot = store.snapshot()
            val supported = biometrics.status() == BiometricStatus.Available
            _state.update {
                it.copy(
                    privatePackages = snapshot.privatePackages.toSet(),
                    addedOrder = snapshot.addedOrder,
                    biometricSupported = supported,
                    biometricEnabled = snapshot.biometricEnabled && supported,
                    biometricOffered = snapshot.biometricEnabled && supported,
                    messageRes = null,
                    pinEntry = "",
                    pinIsConfirm = false,
                )
            }
            val remaining = snapshot.lockedUntilMillis - System.currentTimeMillis()
            if (remaining > 0) {
                startLockout(remaining)
                _state.update { it.copy(phase = VaultPhase.Unlock) }
                return@launch
            }
            _state.update {
                it.copy(
                    phase = if (snapshot.pinConfigured) VaultPhase.Unlock else VaultPhase.PinSetup
                )
            }
            loadApps()
        }
    }

    fun onDigit(digit: Char) {
        val current = _state.value
        if (current.lockoutRemainingSeconds > 0) return
        if (current.pinEntry.length >= MAX_PIN_DIGITS) return
        _state.update { it.copy(pinEntry = it.pinEntry + digit, messageRes = null) }
    }

    fun onBackspace() {
        _state.update { it.copy(pinEntry = it.pinEntry.dropLast(1), messageRes = null) }
    }

    fun onSubmitPin() {
        val current = _state.value
        if (current.lockoutRemainingSeconds > 0) return
        if (!current.isPinAcceptedLength) {
            _state.update { it.copy(messageRes = R.string.pin_too_short) }
            return
        }
        viewModelScope.launch {
            when (current.phase) {
                VaultPhase.PinSetup -> {
                    pendingPin = current.pinEntry
                    _state.update {
                        it.copy(phase = VaultPhase.PinConfirm, pinEntry = "", pinIsConfirm = true)
                    }
                }

                VaultPhase.PinConfirm -> {
                    if (current.pinEntry == pendingPin) {
                        pendingPin?.let { store.setPin(it) }
                        pendingPin = null
                        _state.update {
                            it.copy(phase = VaultPhase.Unlocked, pinEntry = "", pinIsConfirm = false)
                        }
                        loadApps()
                    } else {
                        pendingPin = null
                        _state.update {
                            it.copy(
                                phase = VaultPhase.PinSetup,
                                pinEntry = "",
                                pinIsConfirm = false,
                                messageRes = R.string.pin_mismatch,
                            )
                        }
                    }
                }

                VaultPhase.Unlock -> {
                    if (store.verifyPin(current.pinEntry)) {
                        store.clearFailedAttempts()
                        _state.update {
                            it.copy(
                                phase = VaultPhase.Unlocked,
                                pinEntry = "",
                                messageRes = null,
                                lockoutRemainingSeconds = 0,
                            )
                        }
                        loadApps()
                    } else {
                        val penalty = store.registerFailedAttempt()
                        _state.update { it.copy(pinEntry = "", messageRes = R.string.pin_error) }
                        if (penalty > 0) startLockout(penalty)
                    }
                }

                else -> Unit
            }
        }
    }

    fun onBiometricSucceeded() {
        viewModelScope.launch {
            store.clearFailedAttempts()
            _state.update {
                it.copy(
                    phase = VaultPhase.Unlocked,
                    pinEntry = "",
                    messageRes = null,
                    biometricOffered = false,
                    lockoutRemainingSeconds = 0,
                )
            }
            loadApps()
        }
    }

    fun onBiometricDismissed() {
        _state.update { it.copy(biometricOffered = false) }
    }

    /**
     * Shows the system sheet. Must be handed a real [FragmentActivity]: BiometricPrompt hosts
     * itself in a fragment, and the composition's `LocalContext` is the activity that owns it.
     */
    fun requestBiometric(activity: FragmentActivity) {
        if (_state.value.lockoutRemainingSeconds > 0) return
        if (biometrics.status() != BiometricStatus.Available) {
            _state.update {
                it.copy(biometricOffered = false, biometricSupported = false, messageRes = R.string.biometric_unavailable)
            }
            return
        }
        biometrics.authenticate(
            activity = activity,
            onSuccess = { onBiometricSucceeded() },
            onFailed = { },
            onError = { onBiometricDismissed() },
        )
    }

    /** Re-runs the setup flow; the existing digest is overwritten only after confirmation. */
    fun startPinChange() {
        lockoutJob?.cancel()
        pendingPin = null
        _state.update {
            it.copy(
                phase = VaultPhase.PinSetup,
                pinEntry = "",
                pinIsConfirm = false,
                messageRes = null,
                lockoutRemainingSeconds = 0,
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val allowed = enabled && biometrics.status() == BiometricStatus.Available
            store.setBiometricEnabled(allowed)
            _state.update { it.copy(biometricEnabled = allowed) }
        }
    }

    /** Called when the user leaves the private area: forget everything the session revealed. */
    fun lock() {
        lockoutJob?.cancel()
        lockoutJob = null
        pendingPin = null
        _state.update {
            VaultUiState(
                biometricSupported = it.biometricSupported,
                biometricEnabled = it.biometricEnabled,
                privatePackages = it.privatePackages,
                addedOrder = it.addedOrder,
                apps = it.apps,
            )
        }
    }

    fun loadApps() {
        if (_state.value.isLoadingApps) return
        _state.update { it.copy(isLoadingApps = true) }
        viewModelScope.launch {
            val loaded = apps.load()
            _state.update {
                it.copy(apps = loaded, isLoadingApps = false)
            }
        }
    }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    fun onSortChanged(sort: VaultSort) = _state.update { it.copy(sort = sort) }

    fun onTabChanged(tab: VaultTab) = _state.update { it.copy(tab = tab) }

    fun setShowSystemApps(show: Boolean) = _state.update { it.copy(showSystemApps = show) }

    fun togglePrivate(app: ManagedApp) {
        viewModelScope.launch {
            val makePrivate = app.packageName !in _state.value.privatePackages
            if (store.setPrivate(app.packageName, makePrivate)) {
                _state.update { current ->
                    val set = current.privatePackages.toMutableSet()
                    if (makePrivate) set.add(app.packageName) else set.remove(app.packageName)
                    val order = current.addedOrder.toMutableMap()
                    if (makePrivate) {
                        order[app.packageName] = System.currentTimeMillis()
                    } else {
                        order.remove(app.packageName)
                    }
                    current.copy(privatePackages = set, addedOrder = order)
                }
            }
        }
    }

    fun launch(app: ManagedApp) {
        viewModelScope.launch {
            val started = this@VaultViewModel.apps.launch(app.packageName)
            if (!started) {
                apps.invalidate(app.packageName)
                _state.update {
                    it.copy(
                        privatePackages = it.privatePackages - app.packageName,
                        messageRes = R.string.app_unavailable,
                    )
                }
            }
        }
    }

    fun resetVault() {
        viewModelScope.launch {
            store.reset()
            lock()
        }
    }

    private fun startLockout(penaltyMillis: Long) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var remaining = (penaltyMillis / 1000L).coerceAtLeast(1L)
            while (remaining > 0) {
                _state.update { it.copy(lockoutRemainingSeconds = remaining) }
                delay(1000)
                remaining -= 1
            }
            _state.update { it.copy(lockoutRemainingSeconds = 0, messageRes = null) }
        }
    }

    private companion object {
        const val MAX_PIN_DIGITS = 12
    }
}
