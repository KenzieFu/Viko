# ViKO — Product & Design Spec

**The Figma board is the source of truth.** This file mirrors it. Where the code
disagrees with the design, the code is what is wrong — fix the code, do not
"correct" this document to match the code.

`Claude.md` covers how the code is arranged. This file covers what the app is, who
it is for, and what every screen looks like and does.

## What ViKO is

Hardware **plus** an app. We build both.

- **The earpiece** — an ESP32 wearable we designed (see
  `1V_P2_TechProposal_Team ADA ViKo.pdf` one folder up). Mic, speaker, one
  multi-function button, RGB LED, vibration motor. It does **no** AI work: it
  captures audio, plays audio, reports button gestures. It has **no screen**.
- **This app** — the brain. Speech recognition, translation, and speech synthesis
  all run on-device. It is also the supervisor's control surface.

Target: Korean-managed electronics factories in Vietnam. Everything runs on the
factory's **local WiFi with no internet**.

## Two people, one of whom never sees this app

| | Vietnamese line worker | Korean supervisor |
|---|---|---|
| Carries | earpiece only | the phone running this app |
| Sees | nothing — LED + vibration only | every screen in this repo |
| Speaks | Vietnamese | Korean |

**This app is the supervisor's tool.** Workers appear *in* it as members but never
hold it. A feature that needs a worker to read a screen is wrong by construction.

## Translation is bidirectional

- Supervisor speaks Korean → worker hears **Vietnamese**.
- Worker speaks Vietnamese → supervisor hears **Korean** *and* sees the **text**.

Text is only ever shown to the supervisor, because only the supervisor has a
screen. The transcript surfaces on **Direct Call — Listen** as a bubble under the
member's name.

---

# Visual language

## Icons — solid, never line

**Use filled/solid icons everywhere.** `Icons.Filled.*`, not `Icons.Outlined.*`.
Do not "modernise" a solid icon into an outline one. This is a standing decision,
not a per-screen choice.

Reference treatment is the SOS sheet: each icon is a **solid glyph inside a solid
circle**, and the circle carries the colour.

## Colour carries meaning

| Meaning | Colour | Used by |
|---|---|---|
| **Danger** — supervisor broadcasting an emergency | **red** | SOS presets Fire, Gas Leak, Evacuate; SOS Announce; Delete; Remove Member; the `SOS` pill |
| **Normal action** — safe, routine, in progress | **blue** | All Clear, Talk, Speaker, Save, Continue, `+` buttons, selected check, transcript status |
| **Alert** — a member needs attention | **yellow** | Emergency banner on Home, active notification cards |
| **Member talking** — the earpiece holds the channel | **teal** | `Speaking` in the team sheet, Direct Call — Listen |
| **Success / available** | **green** | `Available` on Direct Call, healthy battery, resolved alert check |

Three attention colours, three different jobs — keep them apart:

- **Red** is the supervisor pushing danger *out* to the floor.
- **Yellow** is a member pulling attention *in* to the supervisor.
- **Green** is success, or someone reachable.

Blue is the supervisor talking; teal is a member talking. Two colours so it is
always clear whose turn it is.

Tokens: `VAlertYellow` / `VAlertYellowSurface` / `VAlertYellowBorder` for yellow,
`VMemberSpeaking` / `VMemberSpeakingSurface` for teal, `VOnline` for green. (These were named `VAlertCream` / `VAlertAmber` — leftovers
from the light theme — and are renamed.)

Two announce buttons, two colours, on purpose:

- **Make Announcement** (routine, whole factory) → speaking state is **blue**.
- **SOS → Announce** (emergency free-speak) → speaking state is **red**.

**Red is reserved for emergency.** Nothing else may use red — it has to stay
unambiguous under stress in a loud room.

Palette lives in `view/theme/Color.kt`. Never hardcode `Color(0x...)` in a screen.
Dark theme only; there is no light variant.

---

# The flow, as designed

## A. Onboarding — first run

### 1. Member — Pairing  ·  *Nearby Device* sheet

Bottom sheet over a dimmed Home. Drag handle.

- Title **Nearby Device** bold + small scan glyph. `X` top-right.
- Subtitle *Pair your device to add a new member.*
- Rows (card): device name bold left · `Pair Device` grey + blue link icon right.
- **Empty variant:** plain grey *No devices found*. No icon, no illustration.

### 2. Setup Member

Full screen. `X` **top-right only** — no title, no eyebrow.

- 3D device render, centred, with a soft glow beneath it.
- `Name` — tiny grey label **above** a bordered rounded field, placeholder
  *Enter Member Name*.
- `Channel` — tiny grey label above a bordered row: *Select Channel* + `›`.
- Separate read-only card:
  `Device Address` → `1234546363SDF##SDF`
  `Device Status` → `Central Building • 🔋 70%`
- **Save**, full width. **Dim navy while incomplete → bright blue once name *and*
  channel are set.**

Name and Channel are **two separate bordered fields with labels outside them** —
not one grouped card.

### 3. Select Channel (sheet)

Drag handle. Back `‹` + **Select Channel** + **blue filled circle `+`** top-right.

- Search field with magnifier, *Search Channel*.
- **Empty:** grey *No channel yet*, then a dark pill `+ Add Channel` (blue `+`).
- **Populated:** channel rows as cards. Selected row gets a **blue ✓** on the
  right — the row background does not change.
- **Search miss:** the typed word appears as a row — `Store` · `Add New +` — which
  creates it.

### 4. Add New Channel (sheet over the parent, parent dimmed)

**Add New Channel** bold + `X` · field *Enter Channel Name* · **Continue** full
width, dim until a name is typed.

### 5. Repeat, then Home

Pair another member, or finish → Home.

**Locked-channel variant** (*Setup Member* frame with a drag handle): opened from
a team sheet's *Add Member → +*, the Channel row is a filled, borderless field
with no `›`. The member joins the channel whose sheet it came from.

## B. Home

Channel cards, two per row. Top bar: `SOS` pill left (dark card, **solid red siren
+ red bold label**), bell and gear right. Bottom: **Make Announcement** (dark card,
solid megaphone, white label).

| State | Card panel | Circle | Icon | Label |
|---|---|---|---|---|
| **Default** | none | outlined | white mic | white |
| **Inactive** (`0/5`) | none | dimmed | dimmed | dimmed |
| **Talk** | navy | bright blue + glow | white mic | **blue** |
| **Speaker** | navy | **white** + glow | **blue** speaker | **white** |

- **Notification** variant: bell carries a **blue count badge**.
- **Notified** variant: yellow banner above the grid — yellow `ⓘ`,
  *Emergency by {name}* bold, `1m ago`, `Zone 3 • Operations Team`, and two
  outlined actions **Direct Call** (phone) and **Resolve** (check-circle).

Interactions:

- **Hold a card** → talk to that whole team.
- **Tap the member chip** (`3/5 ›`) → Team sheet.
- **Make Announcement** → whole factory.
- **SOS** → emergency sheet.
- **Gear** → Settings.

## C. Team sheet

Drag handle. **Operations (3/5)** bold + pencil edit icon.

- Member rows: name bold, `Zone 4 • 🔋 70%`, **phone icon** right to call them.
- Unavailable members dimmed with *Not Available* and a dimmed phone icon.
- **Talk to Team** at the bottom — dark card, mic + label.
- **Speak state:** button turns **bright blue** reading *🎤 Speaking...*, with navy
  glow rings behind it.
- **Member Talk:** the speaking member's card turns **teal** with a teal outline, a
  waveform glyph beside the name, and a teal bold **Speaking** leading the
  subtitle. **Talk to Team** dims and stops responding while they hold the channel.
- **Edit mode:** title drops the count; blue **Save** pill replaces the pencil.
  Rows collapse to name-only fields (sorted by name), each with a **red trash
  icon**. A `+ Add Member` pill sits below. Renames only apply on **Save**.
- **Add Member** sheet (from that pill): `‹` + **Add Member** + blue `+`, *Search
  Member*, then every member not already on this channel as a card. Tap one to
  move them here; `+` pairs a new earpiece straight into this channel.
- **Delete member?** sheet — `X`, body *Your paired devices data will be deleted and
  has to setup a new one later.*, **Cancel** (dark) and **Delete** (solid red).

The red trash in edit mode **deletes the member**, not merely their channel
assignment — the copy says the paired device data goes with it.

## D. Direct Call

`DIRECT CALL` tiny uppercase centred at top, `X` right. Name centred mid-screen.

| State | Status line | Circle | Extra |
|---|---|---|---|
| **Default** | `Central Building • Available` (green) | **white** circle, blue mic | — |
| **Speak** | `Central Building • Listening...` (blue) | **blue** circle, white mic | navy glow below |
| **Listen** | `Central Building • Speaking...` (**teal**) + waveform | small grey circle, mic **dimmed / disabled** | teal glow behind name + **transcript card** |

The transcript bubble is the translated speech, e.g. *"Yes boss, I think we've got
them handled already now. So it's fine now."* This is feature 3's text translation.

## E. SOS sheet

Drag handle, **SOS** centred bold, `X` right.

2×2 grid of preset cards, each a **solid glyph in a solid circle** above its label:

| Preset | Icon colour |
|---|---|
| Fire | red |
| Gas Leak | red |
| Evacuate | red |
| All Clear | **blue** |

- **Danger On:** the tapped danger card gains a **red border and red-tinted
  background**, its circle goes bright red, its label turns red.
- **All Clear:** same treatment in **blue**.
- Below the grid: **Announce** — a **solid red circle with a white megaphone** and
  the label `Announce`. This is the **free-speak slot**: the supervisor talks
  instead of firing a preset.
- **Speak state:** grid dims, the Announce circle grows with **red glow rings**,
  label becomes **`Speaking...` in red**.

## F. Announcement sheet

Sheet over Home. **Announce** bold centred + `X`.

- **Default:** *From Lee Suyeon (수연이)* grey, large **white** circle with a blue
  megaphone.
- **Speak:** *Speaking...* grey, circle turns **bright blue**, navy wave glow fills
  the lower sheet.

## G. Notifications

**Notifications** bold, `Resolve All` as a dark pill, `X`.

- **Active card:** yellow fill + yellow border, yellow `ⓘ`, title bold (wraps to
  two lines), time top-right, `Zone 3 • Operations Team`, actions **Direct Call**
  and **Resolve**.
- **Resolved card:** plain dark surface, **green check-circle**, grey *Resolved*, no
  action buttons.

## H. Manage Member

Back `‹` + **Manage Member** bold + **blue filled circle `+`** top-right.

- `Search Member` field + filter icon.
- Member rows: name bold, an uppercase channel chip (`OPERATIONS`),
  `Central Building • 🔋 70%`, and `›` on the right.
- Unavailable members show *Not Available* with no battery.
- **No per-row delete** — deletion happens in Member Details or Team edit mode.

### Member Details

`X` top-right. Device render. `Name` field and `Channel` row (same pattern as
Setup Member). Device Address / Device Status card. **Save Changes** bright blue
full width, then **Remove Member** as red text beneath it.

Name and Channel are drafts until **Save Changes**; `X` discards them. **Remove
Member** opens the same *Delete member?* sheet as the team sheet.

## I. Settings

Back `‹` + **Settings**. Two card rows, each with `›`: **Manage Members** and
**Manage Channels**.

## J. Manage Channel

Back `‹` + **Manage Channel** + **blue filled circle `+`**.

- `Search Channel` field.
- Channel rows as cards: name bold, member count grey and `›` on the right. Tap →
  Manage Member filtered to that channel.
- **Empty:** grey *No channel yet* + the dark `+ Add Channel` pill.
- **Search miss:** `Store` · `Add New +`, same as Select Channel.
- `+`, the pill and the miss row all open **Add New Channel**.

---

# Where the code diverges from the design

Verified by **running the app on an emulator** and screenshotting each screen, not
by reading code alone. Reading code alone produced three false gaps — see
*Already correct* below before you "fix" something that is fine.

## Already correct — do not touch

- **SOS sheet is done.** Four presets with **solid glyphs in tinted circles**, red
  for Fire / Gas Leak / Evacuate, blue for All Clear, and the **red Announce
  circle** free-speak slot with a `Speaking...` state that auto-stops
  (`HomeScreen.kt:441`). This matches the design.
- **Team sheet has `Talk to Team`.** The control exists.
- Home Default / Inactive / Talk / Speaker states all render correctly.

## Broken

1. **Nothing is actually voice.** `AndroidManifest.xml` declares **zero**
   permissions — no `RECORD_AUDIO`, no `INTERNET`, no Bluetooth. No audio capture,
   ASR, translation, TTS, BLE, or WiFi transport exists. Every "talk" control is a
   visual state change.
2. **Nothing outlives the phone.** Every screen now reads the Room repositories
   through a ViewModel, so state survives rotation and process death — but the
   database is the only source of truth there is. No badge has ever written to it.

## Fixed already

**Every frame in the October design export is built** (`OneVoice - Solution
design`, 38 frames). Checked by rendering each screen at 360×800 next to its
frame — not on an emulator, so re-check on one.

- **Palette sampled from the export.** Page and every sheet `#1C1D20`, cards
  `#2E3033`, hairlines `#383838`, blue `#0062FF`, full-width buttons `#003D9E`
  (dim `#0E2D5F`), teal `#11A0B6` on `#193F45`. The old values were read off
  screenshots and were a step too dark throughout.
- **Home emergency banner.** The newest active alert sits above the grid as the
  yellow card with *Direct Call* / *Resolve* — the same `AlertCard` the
  Notifications list uses.
- **Team sheet** rows are cards (no avatar, battery inline, phone right),
  unavailable members dim, a talking member goes teal, and **Talk to Team** is a
  dark card that turns bright blue *Speaking...*. Edit mode, the Add Member
  sheet and the *Delete member?* sheet (was an AlertDialog) match the frames.
- **Manage Member** rows have the uppercase channel chip, `Location • 🔋 %` and
  `›`, and no trash. Channel filter chips became the funnel menu.
- **Setup Member / Member Details** use separate labelled, bordered fields, the
  read-only device card, and Save pinned at the bottom. Member Details is now a
  form with **Save Changes**; the full-screen channel picker route is gone in
  favour of the same sheet the add flow uses.
- **New:** Settings and Manage Channel.

**All state survived nothing.** This was the real bug behind several confusing
symptoms — the pairing flow "bouncing" back to the device list, a typed member
name disappearing, and members vanishing after they were added. Every piece of
state was plain `remember`, and `AppState` itself was `remember { AppState() }`.
Any Activity recreation — rotation, an IME inset change, a system overlay such as
the stylus tutorial — destroyed all of it. Confirmed in logcat:
`finishDrawing of relaunch ... MainActivity`.

The fix landed in two stages. First a process-lifetime `AppStateHolder` singleton,
so paired members were at least not lost; then the proper answer, which that note
called for — every screen now reads a ViewModel over the Room repositories.
`AppState` and `AppStateHolder` are gone.

- Form state lives in `AddMemberViewModel`, which survives Activity recreation by
  construction. A rotation mid-form no longer costs a typed name, and members are
  written to the database rather than to a list in memory.
- What is left in the composables is view state only — which sheet is open — and
  that is `rememberSaveable`: `showChannelPicker` / `addChannelPrefill` in
  `AddMemberFlow`, `showAddMember` / `onboardingOffered` in `HomeScreen`.

Verified on an emulator: type a name, rotate, and the form is still there. Before
the fix the same rotation returned to *Nearby Device* with an empty form.

**Onboarding now exists.** `SEED_DEMO_DATA` in `VikoApplication` is `false`, so a
fresh install starts with an empty database — no members, no channels.
`HomeScreen` opens the pairing sheet by itself when `uiState.needsOnboarding`, and
shows a *No team yet* + `Pair Device` empty state instead of four channels nobody
created. `AddMemberFlow` no longer closes on Save — it clears the form, returns to
the device list, and shows *"N members added"* + a blue **Done** once there is at
least one. Flip `SEED_DEMO_DATA` to `true` for the populated demo
(`data/local/SeedData.kt`).

Note that "empty" now means empty *on disk*: the UI waits for the database to
answer before deciding it has nothing to show, so a populated install cannot flash
the empty state or re-open onboarding on its first frame.

Side effects worth knowing: *Add new channel* now has a purpose, `Select Channel`'s
**No channel yet** state is reachable on a fresh install (it was dead UI), and Home
no longer boots into Speaker state — that was `w1` being hardcoded
`WorkerStatus.SPEAKING` in the seed data.

**Direct Call rewritten.** The glow was a free-floating layer pinned to the bottom
of the screen at a fixed offset and 280 dp wide, completely detached from the mic
button — so it drew straight over the transcript card. Each glow now lives inside
the slot it belongs to (behind the name, or behind the mic), so layout reserves
space and nothing overlaps. The two also never coexist: the name glows while the
member talks, the mic glows while the supervisor talks. Also brought to the design:
`DIRECT CALL` label with the close button on the **right**, the letter avatar
removed, green `Available` / blue `Listening...`, white-circle-blue-mic → blue-
circle-white-mic → dimmed, and the transcript bubble. Removed the red End Call FAB
and the *Tap to speak* caption — neither is in the design; closing is the `X`.

**Red no longer leaks outside emergency.** A speaking member in the team sheet was
red (`VAccentRed`) in both the status text and the animated dot. Now blue — a member
talking is a normal action.

**All icons are solid.** `Icons.Outlined.Settings`, `.People`, `.Notifications`,
`.CheckCircle`, `.ErrorOutline` → their `Filled` equivalents.

**Channel labels no longer truncate.** `headlineLarge` (25 sp) overflowed a
half-width card and rendered `Maintenance` as `Maintenan…`; now `headlineMedium`.

- Manage Member's `+` was an **invisible icon on a white blob** — a `VOnSurface`
  circle with a `VOnPrimary` tint, which the dark palette made near-white on
  near-white. Now a blue circle with a white glyph.
- The channel filter chip row overflowed; the last chip rendered as a column of
  single letters. Now horizontally scrollable.

## Running it without the earpiece

You do not need the hardware. `SimulatedDeviceScanner` (`data/team/scanner/`) fakes
the BLE scan — four devices trickle in over about three seconds, and pairing is a
1.4 s timer. It implements the `DeviceScanner` domain interface, so the real
`BleDeviceScanner` drops in beside it and no screen changes; return an empty flow
from `scan()` to see the *No devices found* state.

```
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone &
./gradlew installDebug
```

---

# Vocabulary

Keep consistent — the code mixes these today.

- **Member** = **worker** = a Vietnamese line worker with an earpiece. Domain model
  is `Worker` (`domain/team/model/Worker.kt`); UI says "member".
- **Channel** = **team** = a group who hear each other. UI uses both: Home cards say
  the channel name, the sheet title says `Operations (3/5)`.
- **Supervisor** = the app user. Never "user" in UI copy.
- **Location** = where a member is (`Central Building`, `Zone 4`). The model calls
  this `zone`; the design shows building names as well as zone numbers.
- **Not Available** = the member's earpiece is unreachable. Not "offline".

# Hard constraints

- **No internet, ever.** Factory local WiFi, WiFi Direct as the two-device
  fallback. A cloud API call is a design error.
- **The earpiece has no screen.** Worker feedback is LED colour, vibration, audio.
- **Workers may not read Korean; supervisors may not read Vietnamese.** Never solve
  a problem with text a worker has to read.
- Solid icons only. Red only for emergency. Dark theme only.

# Still to confirm

- Exact hex values. Surfaces, blues, teal and the yellow card are sampled from the
  export (see *Fixed already*). Still guessed: the yellow **accent** (`VAlertYellow`,
  icon and text), the green, and the chip-on-card `VSurfaceVariant`.
- The 3D device render is not exported yet. `components/DeviceIllustration.kt`
  draws it in code to match the frame. Needs the real asset in `res/drawable`.
- **Setup Member has three fields the frame does not:** Location, Factory Wi-Fi and
  Wi-Fi Password. The earpiece has no screen or keyboard, so pairing is the only
  moment it can be given the factory network; without them provisioning writes a
  badge that never comes back. They use the frame's field style and scroll above
  a pinned Save. Design should decide where they really live (e.g. a one-time
  Wi-Fi setting) — then they leave this form.
- Whether **SOS → Announce** broadcasts to the whole factory or only to the
  supervisor's channels.
- Whether a danger preset fires immediately on tap or needs a confirm.
- **The transcript bubble shows one paragraph, but the PRD says the supervisor sees
  the original *and* the translation.** The code currently renders the translation
  only, per the design. If both should show, the original Vietnamese is already
  available — it just is not drawn.
