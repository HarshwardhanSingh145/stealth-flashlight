package com.qoder.stealthflash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val VaultColors = darkColorScheme(
    primary = VaultAccent,
    onPrimary = Color.White,
    secondary = NeonCore,
    background = VaultSurface,
    onBackground = VaultText,
    surface = VaultSurface,
    onSurface = VaultText,
    surfaceVariant = VaultSurfaceHigh,
    onSurfaceVariant = VaultTextMuted,
    outline = VaultOutline,
    error = VaultDanger,
    onError = Color.White,
)

/**
 * The torch screen is drawn from sampled tokens rather than a Material palette, so this theme
 * only governs the private area's controls. Keeping them visually distinct is a requirement,
 * not an accident: it tells the user which interface they are standing in.
 */
@Composable
fun StealthFlashTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VaultColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Geometric sans with wide tracking, matching the reference's lettering. */
object TorchType {
    val family = FontFamily.SansSerif
    val stateWeight = FontWeight.Medium
    val stateTracking = 2.sp
    val captionWeight = FontWeight.Normal
    val captionTracking = 1.sp
}
