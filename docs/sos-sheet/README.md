# SOS bottom sheet

The SOS modal wraps its content and leaves Home visible above it. Its content is capped at 78% of the display height, with scrolling available on smaller screens or with larger text. The modal host supplies the drag handle and system insets.

- Two columns of 160 dp emergency cards, without the channel member-count row.
- Selected emergency types reuse the homepage `ChannelSpeakingPulse` behind a 48 dp circle. Danger panels use #FF0400 at 10% opacity, circles #FF383C, and pulses #DE0400. All Clear uses #1C284A panels with #0062FF circles and pulses.
- Fire uses the imported `ic_flame` drawable; Gas Leak uses `ic_gas_leak`, and Evacuate uses `ic_evacuate`. Original drawable colors are preserved.
- Heading: 20 sp. Close icon: 24 dp.
- Grid to Announce: 32 dp.
- Announce: 104 dp circle, 38 dp `ic_announces`, 12 dp gap to its 18 sp label.
- Tapping Announce shows the red bottom wave and Speaking label, and dims/disables the four emergency cards. The broad wave fades before rising above the Announce circle and stays behind its opaque icon control. Tapping again stops the preview. The existing 3.5-second automatic stop remains.

This is the existing local UI preview; this screen does not send emergency audio or packets to badges.

![Idle, Fire, All Clear, and speaking states](overview.jpg)

Actual emulator captures with test-only homepage data, including early and late wave phases. Validation: debug and instrumentation builds, lint, and the SOS interaction test passed. The test checks selection changes, Announce start/stop, disabled cards while speaking, stable grid position, the wave starting at the Announce control boundary, and closing back to Home.
