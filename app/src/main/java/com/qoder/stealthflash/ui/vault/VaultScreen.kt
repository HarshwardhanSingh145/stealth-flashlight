package com.qoder.stealthflash.ui.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qoder.stealthflash.R
import com.qoder.stealthflash.vault.ManagedApp
import com.qoder.stealthflash.vault.VaultSort
import com.qoder.stealthflash.vault.VaultTab
import com.qoder.stealthflash.vault.VaultUiState

@Composable
fun VaultScreen(
    state: VaultUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSortChanged: (VaultSort) -> Unit,
    onTabChanged: (VaultTab) -> Unit,
    onShowSystemChanged: (Boolean) -> Unit,
    onTogglePrivate: (ManagedApp) -> Unit,
    onLaunch: (ManagedApp) -> Unit,
    onBiometricChanged: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onResetVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showExplanation by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back_to_flashlight), fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) {
                Text(stringResource(R.string.settings), fontSize = 14.sp)
            }
        }

        Text(
            text = stringResource(R.string.vault_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text(stringResource(R.string.vault_search)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.tab == tab,
                    onClick = { onTabChanged(tab) },
                    label = {
                        Text(
                            stringResource(
                                when (tab) {
                                    VaultTab.Private -> R.string.private_list
                                    VaultTab.All -> R.string.all_apps
                                },
                            ),
                        )
                    },
                )
            }
            VaultSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { onSortChanged(sort) },
                    label = {
                        Text(
                            stringResource(
                                when (sort) {
                                    VaultSort.Name -> R.string.sort_name
                                    VaultSort.RecentlyAdded -> R.string.sort_recent
                                },
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        when {
            state.isLoadingApps && state.apps.isEmpty() -> LoadingRow()
            state.visibleApps.isEmpty() -> EmptyRow(state)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(state.visibleApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        isPrivate = app.packageName in state.privatePackages,
                        onTogglePrivate = { onTogglePrivate(app) },
                        onLaunch = { onLaunch(app) },
                    )
                }
                item {
                    TextButton(
                        onClick = { showExplanation = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.explanation), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onBiometricChanged = onBiometricChanged,
            onShowSystemChanged = onShowSystemChanged,
            onChangePin = { showSettings = false; onChangePin() },
            onReset = { showSettings = false; confirmReset = true },
            onDismiss = { showSettings = false },
        )
    }

    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.explanation)) },
            text = { Text(stringResource(R.string.explanation_body)) },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetVault()
                    },
                ) { Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
        )
    }
}

@Composable
private fun AppRow(
    app: ManagedApp,
    isPrivate: Boolean,
    onTogglePrivate: () -> Unit,
    onLaunch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isPrivate) { onLaunch() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isPrivate) {
                    stringResource(R.string.status_private)
                } else {
                    stringResource(R.string.status_public)
                },
                fontSize = 12.sp,
                color = if (isPrivate) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (isPrivate) {
            TextButton(onClick = onLaunch) { Text(stringResource(R.string.launch), fontSize = 13.sp) }
        }
        Checkbox(checked = isPrivate, onCheckedChange = { onTogglePrivate() })
    }
}

@Composable
private fun LoadingRow() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.loading_apps),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun EmptyRow(state: VaultUiState) {
    Box(modifier = Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (state.tab == VaultTab.Private) R.string.vault_empty else R.string.no_matches,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SettingsDialog(
    state: VaultUiState,
    onBiometricChanged: (Boolean) -> Unit,
    onShowSystemChanged: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                SettingToggle(
                    label = stringResource(R.string.settings_biometric),
                    enabled = state.biometricSupported,
                    checked = state.biometricEnabled,
                    onCheckedChange = onBiometricChanged,
                )
                SettingToggle(
                    label = stringResource(R.string.settings_show_system),
                    enabled = true,
                    checked = state.showSystemApps,
                    onCheckedChange = onShowSystemChanged,
                )
                TextButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_change_pin))
                }
                TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun SettingToggle(
    label: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
