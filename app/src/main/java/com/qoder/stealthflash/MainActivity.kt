package com.qoder.stealthflash

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qoder.stealthflash.flashlight.FlashlightEvent
import com.qoder.stealthflash.flashlight.FlashlightViewModel
import com.qoder.stealthflash.ui.flashlight.FlashlightScreen
import com.qoder.stealthflash.ui.theme.StealthFlashTheme
import com.qoder.stealthflash.ui.vault.PinScreen
import com.qoder.stealthflash.ui.vault.VaultScreen
import com.qoder.stealthflash.vault.VaultPhase
import com.qoder.stealthflash.vault.VaultViewModel

private enum class Screen { Flashlight, Vault }

/**
 * A [FragmentActivity] rather than a plain ComponentActivity because BiometricPrompt attaches a
 * hidden fragment to host the system sheet.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StealthFlashTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val flashlight: FlashlightViewModel = viewModel()
    val vault: VaultViewModel = viewModel()

    val torchState by flashlight.torchState.collectAsStateWithLifecycle()
    val vaultState by vault.state.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(Screen.Flashlight) }

    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // The LED must never outlive the visible UI. The OS revokes torch ownership as soon as
    // another client opens the camera, so a remembered "on" flag would be a lie.
    DisposableEffect(flashlight, vault) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> flashlight.torch.resume()

                Lifecycle.Event.ON_STOP -> {
                    flashlight.torch.pause()
                    flashlight.cancelHold()
                    if (screen == Screen.Vault) {
                        vault.lock()
                        screen = Screen.Flashlight
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(flashlight) {
        flashlight.events.collect { event ->
            if (event == FlashlightEvent.UnlockRequested) {
                screen = Screen.Vault
                vault.onGateOpened()
            }
        }
    }

    // Offer the biometric sheet while the PIN gate is asking, if the user enabled it.
    LaunchedEffect(vaultState.phase, vaultState.biometricOffered, vaultState.lockoutRemainingSeconds) {
        if (vaultState.phase == VaultPhase.Unlock &&
            vaultState.biometricOffered &&
            vaultState.lockoutRemainingSeconds == 0L &&
            activity != null
        ) {
            vault.requestBiometric(activity)
        }
    }

    BackHandler(enabled = screen == Screen.Vault) {
        vault.lock()
        screen = Screen.Flashlight
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState == Screen.Vault
            (slideInHorizontally(tween(280)) { if (forward) it / 6 else -it / 6 } +
                fadeIn(tween(220)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { if (forward) -it / 6 else it / 6 } +
                        fadeOut(tween(220)),
                )
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Flashlight -> FlashlightScreen(
                torchState = torchState,
                tracker = flashlight.tracker,
                onOutcome = flashlight::onOutcome,
                onAccessibilityUnlock = {
                    screen = Screen.Vault
                    vault.onGateOpened()
                },
                modifier = Modifier.fillMaxSize(),
            )

            Screen.Vault -> if (vaultState.phase == VaultPhase.Unlocked) {
                VaultScreen(
                    state = vaultState,
                    onBack = {
                        vault.lock()
                        screen = Screen.Flashlight
                    },
                    onQueryChanged = vault::onQueryChanged,
                    onSortChanged = vault::onSortChanged,
                    onTabChanged = vault::onTabChanged,
                    onShowSystemChanged = vault::setShowSystemApps,
                    onTogglePrivate = vault::togglePrivate,
                    onLaunch = vault::launch,
                    onBiometricChanged = vault::setBiometricEnabled,
                    onChangePin = vault::startPinChange,
                    onResetVault = {
                        vault.resetVault()
                        screen = Screen.Flashlight
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PinScreen(
                    state = vaultState,
                    onDigit = vault::onDigit,
                    onBackspace = vault::onBackspace,
                    onSubmit = vault::onSubmitPin,
                    onUseBiometric = { activity?.let { vault.requestBiometric(it) } },
                    onExit = {
                        vault.lock()
                        screen = Screen.Flashlight
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * `LocalContext` under Compose is usually the activity already, but wrapping contexts (and the
 * preview context) are not, so unwrap rather than cast.
 */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
