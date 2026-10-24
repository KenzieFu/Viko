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
val VBackground = Color(0xFF1C1D20)      // page behind everything (dark/main-background)
val VSurface = Color(0xFF2E3033)         // cards, sheets, rows (dark/secondary-background)
val VSurfaceVariant = Color(0xFF26272A)  // chips / insets sitting between page and card
val VBorder = Color(0xFF323236)          // hairline outlines on buttons and cards
val VFieldBorder = Color(0xFF383838)     // the outline of a text field at rest
val VShadow = Color(0x66000000)          // elevation spot color

/**
 * The sheet grabber. Lighter than [VBorder] because it sits on the sheet, not
 * on the page, and has to stay findable against it.
 */
val VDragHandle = Color(0xFF494949)

// Text
val VOnSurface = Color(0xFFF2F2F2)          // primary text + icons
val VOnSurfaceSecondary = Color(0xFF8E8E93) // labels, captions, chevrons
val VFieldText = Color(0xFFF5F5F5)          // what the supervisor has typed into a field

// Accents
val VAccentRed = Color(0xFFFF383C)   // SOS / emergency only — nothing else
val VAccentBlue = Color(0xFF3B82F6)  // talk state, badges, links
val VMicBlue = Color(0xFF2563EB)     // the filled mic circle while talking

/**
 * The brand blue. The filled circle behind a `+`, the link glyph on a pairing
 * row, and the focused outline of the channel-name field all use it at full
 * strength; [VBrandOnDark] is the same hue dropped back far enough to carry a
 * 56dp button without glowing off a dark page.
 */
val VBrand = Color(0xFF0062FF)
val VBrandOnDark = Color(0xFF003D9E)

/**
 * The ring around the talking mic circle, and the channel name under it — the
 * same blue as ic_mic's own stroke, so circle outline and label read as one.
 */
val VMicRing = Color(0xFF0062FF)

// Panels behind an active channel. Both are dim so the bright circle on top of
// them is what draws the eye — talk is blue-tinted, speaker is near-neutral.
val VActivePanel = Color(0xFF1C284A)    // channel you are TALKING on
val VListeningPanel = Color(0xFF242A38) // channel you are HEARING

// YELLOW = an alert raised by a member. The emergency banner on Home and the
// active cards in Notifications. Yellow means "someone needs attention", which is
// distinct from red (danger the supervisor is broadcasting) and from green
// (success / available).
val VAlertYellow = Color(0xFFE0A916)        // icon, accent text — NOT yet sampled
val VAlertYellowSurface = Color(0xFF3D320D) // card fill — from Figma
val VAlertYellowBorder = Color(0xFF65501C)  // card outline — from Figma

// Buttons (PrimaryButton — e.g. "Make Announcement")
val VPrimary = Color(0xFF2A2A2C)
val VOnPrimary = Color(0xFFF2F2F2)

// GREEN = success / available. Used for the online status dot, a healthy battery,
// and the "Available" state on Direct Call.
val VOnline = Color(0xFF32D74B)

val VOffline = Color(0xFF6E6E73)
val VWarning = Color(0xFFFF9F0A)
val VLowBattery = Color(0xFFFF453A)
