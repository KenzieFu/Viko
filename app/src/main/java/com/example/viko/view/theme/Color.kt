package com.example.viko.view.theme

import androidx.compose.ui.graphics.Color

// Viko design system colors (from Figma) — DARK THEME.
//
// The whole app is dark-only: every screen references these names directly
// (there are ~290 call sites), so changing a value here restyles the app.
// Do NOT hardcode Color(0x...) in a screen — add a name here instead,
// otherwise the next palette change misses it.

// Page + surfaces (each step is lighter than the one before it — that
// difference is what makes a card read as a card, so keep them distinct)
val VBackground = Color(0xFF0D0D0F)      // page behind everything
val VSurface = Color(0xFF1C1C1E)         // cards, sheets, top-bar buttons
val VSurfaceVariant = Color(0xFF262628)  // chips / insets sitting on a card
val VBorder = Color(0xFF2E2E30)          // hairline outlines and dividers
val VShadow = Color(0x66000000)          // elevation spot color

// Text
val VOnSurface = Color(0xFFF2F2F2)          // primary text + icons
val VOnSurfaceSecondary = Color(0xFF8E8E93) // labels, captions, chevrons

// Accents
val VAccentRed = Color(0xFFFF383C)   // SOS / emergency only — nothing else
val VAccentBlue = Color(0xFF3B82F6)  // talk state, badges, links
val VMicBlue = Color(0xFF2563EB)     // the filled mic circle while talking

// Panels behind an active channel. Both are dim so the bright circle on top of
// them is what draws the eye — talk is blue-tinted, speaker is near-neutral.
val VActivePanel = Color(0xFF1D3054)    // channel you are TALKING on
val VListeningPanel = Color(0xFF242A38) // channel you are HEARING

// Emergency alert banner (kept the "Cream" names so call sites still compile;
// on dark they are a deep olive, not cream)
val VAlertCream = Color(0xFF3D3312)
val VAlertCreamBorder = Color(0xFF564818)
val VAlertAmber = Color(0xFFE0A916)

// Buttons (PrimaryButton — e.g. "Make Announcement")
val VPrimary = Color(0xFF2A2A2C)
val VOnPrimary = Color(0xFFF2F2F2)

// Status
val VOnline = Color(0xFF32D74B)
val VOffline = Color(0xFF6E6E73)
val VWarning = Color(0xFFFF9F0A)
val VLowBattery = Color(0xFFFF453A)
