package com.example.viko.view.screens.home.tutorial

import androidx.compose.ui.geometry.Rect

/**
 * The five things a supervisor is shown once, the first time Home opens.
 *
 * Order is the design's, not the order they appear on screen: talking is what
 * the app is for, so it goes first, and the emergency controls follow.
 */
enum class TutorialStep(
    val title: String,
    val body: String
) {
    PressAndHold(
        title = "Press and hold to talk",
        body = "Talk to your whole team by press and hold the mic button."
    ),
    Announcement(
        title = "Make announcement to all channels",
        body = "Talk to your channels all at once with make announcement feature"
    ),
    Sos(
        title = "Use SOS in Emergency",
        body = "Give SOS announcement when emergency happen to inform everyone of the situation"
    ),
    CheckChannel(
        title = "Click to check channel",
        body = "View your team availability and details"
    ),
    DirectCall(
        title = "Pick specific member to direct call",
        body = "Get hold of your member personally with direct call by their device."
    );

    val stepNumber: Int get() = ordinal + 1

    val isLast: Boolean get() = ordinal == entries.lastIndex

    fun next(): TutorialStep? = entries.getOrNull(ordinal + 1)

    companion object {
        val total: Int = entries.size
    }
}

/**
 * Where each step's spotlight goes. Home reports the bounds as it lays itself
 * out, because a channel card's position depends on the screen, not on us.
 */
data class TutorialTargets(
    val channelMic: Rect? = null,
    val channelCard: Rect? = null,
    val channelChip: Rect? = null,
    val sos: Rect? = null,
    val announcement: Rect? = null
) {
    fun boundsFor(step: TutorialStep): Rect? = when (step) {
        TutorialStep.PressAndHold -> channelCard ?: channelMic
        TutorialStep.Announcement -> announcement
        TutorialStep.Sos -> sos
        TutorialStep.CheckChannel -> channelChip
        // Step five lives inside the team sheet, which draws its own.
        TutorialStep.DirectCall -> null
    }
}
