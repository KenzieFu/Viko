package com.example.viko.view.screens.home.tutorial

import org.junit.Assert.*
import org.junit.Test

class TutorialPlacementTest {
    @Test fun announcementAtBottomPlacesActionsAboveIt() {
        assertEquals(406f, tutorialBubbleTop(650f, 730f, 220f, 800f, 44f, 44f, 24f), 0f)
    }
    @Test fun topControlPlacesBubbleBelowIt() {
        assertEquals(144f, tutorialBubbleTop(60f, 120f, 220f, 800f, 44f, 44f, 24f), 0f)
    }
    @Test fun missingOrOffscreenTargetKeepsActionsInViewport() {
        for (target in listOf(null, -200f, 1500f)) {
            val top = tutorialBubbleTop(target, target?.plus(80f), 300f, 480f, 44f, 44f, 24f)
            assertTrue(top >= 44f)
            assertTrue(top + 300f <= 436f)
        }
    }
    @Test fun largeTextUsesTheEntireSafeHeight() {
        assertEquals(44f, tutorialBubbleTop(300f, 380f, 392f, 480f, 44f, 44f, 24f), 0f)
    }
}
