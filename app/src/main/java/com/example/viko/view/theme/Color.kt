package com.example.viko.view.theme

import androidx.compose.ui.graphics.Color

// Viko design system colors (from Figma) — DARK THEME.
//
// The whole app is dark-only: every screen references these names directly
// (there are ~290 call sites), so changing a value here restyles the app.
// Do NOT hardcode Color(0x...) in a screen — add a name here instead,
// otherwise the next palette change misses it.

// Page + surfaces, sampled from the Figma export. A sheet is the same colour as
// the page under it (the scrim is what separates them), and a card is one step
// lighter than whatever it sits on. That step is what makes a card read as a
// card, so keep the values distinct.
val VBackground = Color(0xFF1C1D20)      // page behind everything, and every sheet
val VSurface = Color(0xFF2E3033)         // cards, rows, top-bar buttons
val VSurfaceVariant = Color(0xFF3A3C40)  // chips / insets sitting on a card
val VBorder = Color(0xFF383838)          // hairline outlines and dividers
val VShadow = Color(0x66000000)          // elevation spot color
val VDragHandle = Color(0xFFD9D9D9)      // the pill at the top of every sheet

// Text
val VOnSurface = Color(0xFFF2F2F2)          // primary text + icons
val VOnSurfaceSecondary = Color(0xFF8E8E93) // labels, captions, chevrons

// Accents
val VAccentRed = Color(0xFFFF383C)   // SOS / emergency only — nothing else
val VSosDangerPanel = Color(0xFFFF0400).copy(alpha = .10f) // #FF0400 at 10% opacity
val VSosDangerPulse = Color(0xFFDE0400)
val VAccentBlue = Color(0xFF2076FF)  // blue glyphs and text on a dark surface
val VMicBlue = Color(0xFF0062FF)     // Figma's blue: filled circles, + buttons, checks

/**
 * The ring around the talking mic circle, and the channel name under it — the
 * same blue as ic_mic's own stroke, so circle outline and label read as one.
 */
val VMicRing = Color(0xFF0062FF)

// Full-width blue buttons (Save, Save Changes, Continue) and the Talk to Team
// button while speaking. Deeper than VMicBlue, which is for small filled shapes.
// Disabled is the same blue gone dim rather than grey — "not filled in yet".
val VButtonBlue = Color(0xFF003D9E)
val VButtonBlueDisabled = Color(0xFF0E2D5F)

// Panels behind an active channel. Both are dim so the bright circle on top of
// them is what draws the eye — manager speech is blue, member speech is teal.
val VActivePanel = Color(0xFF1C284A)    // channel you are TALKING on
val VListeningPanel = Color(0xFF193F45) // channel you are HEARING

// TEAL = a member is talking to the supervisor. Their row in the team sheet and
// the Direct Call "Speaking..." state. Kept apart from blue, which is the
// supervisor talking, so it is always clear whose turn it is.
val VMemberSpeaking = Color(0xFF11A0B6)
val VMemberSpeakingSurface = Color(0xFF193F45)
val VMemberSpeakingText = Color(0xFF2EC6DD)
val VMemberSpeakingPulse = Color(0xFF81B1FF)

// YELLOW = an alert raised by a member. The emergency banner on Home and the
// active cards in Notifications. Yellow means "someone needs attention", which is
// distinct from red (danger the supervisor is broadcasting) and from green
// (success / available).
val VAlertYellow = Color(0xFFE0A916)        // icon, accent text — NOT yet sampled
val VAlertYellowSurface = Color(0xFF3D320D) // card fill — from Figma
val VAlertYellowBorder = Color(0xFF65501C)  // card outline — from Figma

// GREEN = success / available. A healthy battery, the "Available" state on Direct
// Call, and the check on a resolved alert.
val VOnline = Color(0xFF32D74B)

val VWarning = Color(0xFFFF9F0A)
val VLowBattery = Color(0xFFFF453A)

// The earpiece render on Setup Member / Member Details. Drawn in code until the
// real 3D asset is exported, so its colours live here like everything else.
val VDeviceBody = Color(0xFFE4E4E4)
val VDeviceBodyShade = Color(0xFFB4B4B6)
val VDeviceGrill = Color(0xFF9C9C9E)
val VDeviceGrillDot = Color(0xFF7C7C7E)
val VDeviceLed = Color(0xFFFFFFFF)
val VDeviceButton = Color(0xFFF2876D)
val VDeviceNub = Color(0xFFC8B89A)

/**
 * The brand blue. Full strength for the focused outline of the channel-name
 * field and the tour's pointer; [VBrandOnDark] is the same hue dropped back far
 * enough to carry a 56dp button without glowing off a dark page.
 */
val VBrand = Color(0xFF0062FF)
val VBrandOnDark = Color(0xFF003D9E)

/** The outline of a text field at rest, and what the supervisor has typed into it. */
val VFieldBorder = Color(0xFF383838)
val VFieldText = Color(0xFFF5F5F5)

/** Blue glow and onboarding action glyphs, specified by the onboarding design. */
val VOnboardingBlue = Color(0xFF1692FF)
