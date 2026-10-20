package com.qoder.stealthflash.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qoder.stealthflash.R
import com.qoder.stealthflash.vault.VaultPhase
import com.qoder.stealthflash.vault.VaultUiState

/**
 * PIN gate. Deliberately a plain dark surface with Material controls — nothing here resembles
 * the torch screen, so there is no ambiguity about which interface is open.
 */
@Composable
fun PinScreen(
    state: VaultUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onUseBiometric: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (state.phase) {
        VaultPhase.PinSetup -> R.string.pin_setup_title
        VaultPhase.PinConfirm -> R.string.pin_confirm_title
        else -> R.string.pin_title
    }
    val locked = state.lockoutRemainingSeconds > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.back_to_flashlight))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(titleRes),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pin_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        PinDots(filled = state.pinEntry.length, error = state.messageRes != null)

        Spacer(Modifier.height(18.dp))
        Text(
            text = if (locked) {
                stringResource(R.string.pin_locked, state.lockoutRemainingSeconds)
            } else {
                state.messageRes?.let { stringResource(it) } ?: " "
            },
            fontSize = 14.sp,
            color = if (locked || state.messageRes != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(Modifier.weight(1f))

        Keypad(
            enabled = !locked,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onSubmit = onSubmit,
            canSubmit = state.isPinAcceptedLength && !locked,
        )

        Spacer(Modifier.height(14.dp))
        if (state.biometricSupported && state.phase == VaultPhase.Unlock) {
            OutlinedButton(
                onClick = onUseBiometric,
                enabled = !locked,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.biometric_unlock))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PinDots(filled: Int, error: Boolean) {
    val active = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(PIN_SLOTS) { index ->
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .background(
                        color = if (index < filled) active else inactive,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KEYPAD_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    when (key) {
                        Key.Delete -> KeypadKey(
                            label = "⌫",
                            enabled = enabled,
                            modifier = Modifier.weight(1f).aspectRatio(1.15f),
                            onClick = onBackspace,
                        )

                        Key.Done -> Button(
                            onClick = onSubmit,
                            enabled = canSubmit,
                            modifier = Modifier.weight(1f).aspectRatio(1.15f),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingZero,
                        ) {
                            Text(stringResource(R.string.confirm), fontSize = 15.sp)
                        }

                        is Key.Digit -> KeypadKey(
                            label = key.digit.toString(),
                            enabled = enabled,
                            modifier = Modifier.weight(1f).aspectRatio(1.15f),
                            onClick = { onDigit(key.digit) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingZero,
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private sealed class Key {
    data class Digit(val digit: Char) : Key()
    data object Delete : Key()
    data object Done : Key()
}

private val KEYPAD_ROWS = listOf(
    listOf(Key.Digit('1'), Key.Digit('2'), Key.Digit('3')),
    listOf(Key.Digit('4'), Key.Digit('5'), Key.Digit('6')),
    listOf(Key.Digit('7'), Key.Digit('8'), Key.Digit('9')),
    listOf(Key.Delete, Key.Digit('0'), Key.Done),
)

private const val PIN_SLOTS = 8

private val PaddingZero = androidx.compose.foundation.layout.PaddingValues(0.dp)
