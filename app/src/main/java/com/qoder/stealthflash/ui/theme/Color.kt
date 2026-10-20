package com.qoder.stealthflash.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every value here is sampled from the reference clip. See reference/DESIGN_SPEC.md.
 */

// OFF state — light neumorphic clay.
val OffBackground = Color(0xFFC0C0C0)
val OffTrack = Color(0xFFC7C7C9)
val OffKnob = Color(0xFFA8A8AA)
val OffKnobRim = Color(0xFFB49BD8)
val OffLabelActive = Color(0xFFFFFFFF)
val OffLabelInactive = Color(0xFF4B4B4D)
val OffDotIdle = Color(0x8CA9AEC4)
val OffDotLit = Color(0xFFFFFFFF)
val OffCaption = Color(0xFF6E6E70)
val OffShadow = Color(0x4A000000)
val OffHighlight = Color(0x66FFFFFF)

// ON state — black with a purple neon rim.
val OnBackground = Color(0xFF0B0B0D)
val OnBloom = Color(0xFF1A1024)
val OnTrack = Color(0xFF0A0A0C)
val OnKnob = Color(0xFF141416)
val NeonCore = Color(0xFFC77DFF)
val NeonMid = Color(0xFFA855F7)
val NeonOuter = Color(0xFF7C2FD1)
val NeonBloom = Color(0xFF6A1FB8)
val OnLabelActive = Color(0xFFFFFFFF)
val OnLabelInactive = Color(0xFF6A6A6E)
val OnDotIdle = Color(0x40C77DFF)
val OnDotLit = Color(0xFFE9D5FF)
val OnCaption = Color(0xFF9A8FB0)

// Shared surfaces (private area). Deliberately a different visual family from the torch
// screen so it is obvious which interface the user is in.
val VaultSurface = Color(0xFF14101C)
val VaultSurfaceHigh = Color(0xFF1E1729)
val VaultOutline = Color(0xFF35294A)
val VaultText = Color(0xFFEDE7F6)
val VaultTextMuted = Color(0xFF9E93B5)
val VaultAccent = Color(0xFFA855F7)
val VaultDanger = Color(0xFFFF6B6B)
val VaultSuccess = Color(0xFF4ADE80)
