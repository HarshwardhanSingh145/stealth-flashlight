package com.qoder.stealthflash.ui.flashlight

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qoder.stealthflash.R
import com.qoder.stealthflash.gesture.HoldGestureTracker
import com.qoder.stealthflash.gesture.HoldOutcome
import com.qoder.stealthflash.torch.TorchFailure
import com.qoder.stealthflash.torch.TorchUiState
import com.qoder.stealthflash.ui.theme.OffLabelInactive
import com.qoder.stealthflash.ui.theme.OnCaption
import com.qoder.stealthflash.ui.theme.VaultDanger

@Composable
fun FlashlightScreen(
    torchState: TorchUiState,
    tracker: HoldGestureTracker,
    onOutcome: (HoldOutcome) -> Unit,
    onAccessibilityUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val operable = torchState.operable
    val onLabel = stringResource(
        if (torchState.isOn) R.string.torch_on else R.string.torch_off,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .semanticsTorch(
                isOn = torchState.isOn,
                operable = operable,
                label = onLabel,
                unlockLabel = stringResource(R.string.accessibility_unlock),
                onToggle = { onOutcome(HoldOutcome.ToggleTorch) },
                onUnlock = onAccessibilityUnlock,
            ),
    ) {
        TorchSwitch(
            isOn = torchState.isOn,
            enabled = operable,
            tracker = tracker,
            onOutcome = onOutcome,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusLine(torchState)
            if (operable) {
                Text(
                    text = stringResource(R.string.unlock_hint),
                    color = if (torchState.isOn) OnCaption else OffLabelInactive,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(torchState: TorchUiState) {
    val text = when {
        !torchState.operable -> stringResource(R.string.no_flash_body)
        torchState.failure == TorchFailure.CameraInUse -> stringResource(R.string.torch_conflict)
        torchState.failure == TorchFailure.CameraDisabled -> stringResource(R.string.torch_disabled)
        torchState.failure == TorchFailure.PermissionDenied -> stringResource(R.string.torch_permission)
        torchState.failure != null -> stringResource(R.string.torch_failed)
        else -> null
    } ?: return

    Text(
        text = text,
        color = if (torchState.failure == null) OnCaption else VaultDanger,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

/**
 * TalkBack and Switch Access cannot perform a five-second continuous drag reliably, so the
 * gesture gets an explicit action-list entry and the switch itself gets a real toggle role.
 */
@Composable
private fun Modifier.semanticsTorch(
    isOn: Boolean,
    operable: Boolean,
    label: String,
    unlockLabel: String,
    onToggle: () -> Unit,
    onUnlock: () -> Unit,
): Modifier = this
    .toggleable(
        value = isOn,
        onValueChange = { onToggle() },
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = operable,
        role = Role.Switch,
    )
    .semantics(mergeDescendants = true) {
        contentDescription = label
        stateDescription = label
        customActions = listOf(
            CustomAccessibilityAction(unlockLabel) {
                onUnlock()
                true
            },
        )
    }
