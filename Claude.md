# ViKo Manager App

Android app, Kotlin + Jetpack Compose, package root `com.example.viko`, min SDK 24.

**Read [PRODUCT.md](PRODUCT.md) first** — what ViKo is, who the two users are, the
visual language, every screen as designed, and where the code still diverges from
it. This file covers how the code is arranged.

## What this app is

The **manager half** of ViKo — Team ADA's entry in the OneVoice (Qualcomm) edge-AI
challenge. A Vietnamese factory line worker wears a push-to-talk badge (ESP32); this
app runs on the Korean supervisor's Android phone, translates the worker's speech
on-device, and sends synthesised Korean/Vietnamese audio back to the badge. **No
internet, no cloud** — everything crosses a local factory Wi-Fi network.

Deployment context, which explains most of the UI decisions: Korean-invested plants
in Vietnam, 80+ dBA floor noise, phones banned on the line, workers' hands occupied.
Hence: badge-side push-to-talk, big tap targets here, an SOS broadcast on the top-left
of the dashboard, and a channel grid that a supervisor can read at a glance.

This repo is **only the phone app**. The badge firmware, the bench tooling and the
networking spec (`Detailed System.docx`) live in the parent OneVoice folder, which has
its own CLAUDE.md.

### The eight screens

| Screen | What it does |
| --- | --- |
| Home | Channel grid; tap the mic to talk to a channel, the chip to see its roster. On a fresh install it is the onboarding screen: *No team yet* with the pairing sheet already open |
| — SOS sheet | Site-wide emergency broadcast (Fire / Gas / Evacuate / All Clear) |
| — Announcement sheet | Site-wide voice announcement |
| — Team sheet | One channel's roster; rename / remove / pull in members |
| Notifications | The emergency feed — every SOS a badge has raised, resolve one or all |
| Direct call | One-to-one PTT with a worker, with a live translation transcript |
| Manage members | The full roster, searchable and channel-filterable |
| — Add member flow | Pair a badge → name the worker → pick a channel → save, then back to the device list for the next one. Hosted by both Home and Manage members |
| Member detail | Profile, badge status (MAC, firmware, battery), recent messages |
| Select channel | Change an existing member's channel, or create one inline |

## Architecture

Three layers. Domain and Data are split per-feature inside; `view` is split per-feature
by screen. Features today: `team` (Worker, NearbyDevice), `channel`, `alert`, `message`.

```
VikoApplication.kt          Builds AppContainer once, seeds the DB on first launch
MainActivity.kt             Sets the theme and hands off to AppNavigation

view/                       UI layer
  screens/<feature>/        <X>Screen.kt + <X>ViewModel.kt, side by side
  screens/home/components/  Sheets and cards too big to live in HomeScreen.kt
  theme/
components/                 Reusable Compose components shared across screens
utils/                      Helpers (UI_STATE_TIMEOUT_MS lives here)
type/                       Small shared enums (WorkerStatus, AlertStatus, CallState)
navigation/                 Routes.kt (Screen + NavArgs), AppNavigation.kt
domain/<feature>/
  model/                    Domain data classes (Worker, Channel, ...)
  repository/               Repository *interfaces* — the only thing the UI depends on
data/<feature>/
  local/                    Room @Entity + @Dao + toDomain()/toEntity() mappers
  repository/               Repository *implementations*
data/team/scanner/          SimulatedDeviceScanner — stands in for BLE until it exists
data/local/                 AppDatabase, SeedData, DatabaseSeeder
di/
  AppContainer.kt           Manual DI: builds the DB, exposes repository singletons
  AppViewModelProvider.kt   The one ViewModel factory every screen uses
```

**Adding a feature:** `domain/<feature>/model` + `domain/<feature>/repository`
(interface) first, then `data/<feature>/local` (Entity + Dao) + `data/<feature>/repository`
(impl), wire into `AppDatabase` and `AppContainer`, then a ViewModel + Screen pair
under `view/screens/<feature>/` and an `initializer` in `AppViewModelProvider`.

## The rules that keep this clean

**Screens are dumb.** A `@Composable` screen takes navigation callbacks and a
ViewModel, collects one `uiState`, and renders it. It never touches a repository, never
holds a `NavController`, and never does business logic. Anything a screen holds in
`remember` should be view-only state: which sheet is open, which dialog is showing —
and use `rememberSaveable` for it, so an Activity recreation (rotation, IME insets, a
system overlay) does not drop the supervisor back through a stack of sheets.

**ViewModels own state, not Compose.** Each screen has exactly one, exposing a single
`StateFlow<XUiState>` built with `combine(...).stateIn(viewModelScope,
SharingStarted.WhileSubscribed(UI_STATE_TIMEOUT_MS), initial)`. Route arguments arrive
through `SavedStateHandle` using the keys in `NavArgs` — never as composable parameters
threaded down by hand.

**A half-filled form goes through `SavedStateHandle` too.** A ViewModel already survives
rotation and the other configuration changes, but not process death, and a supervisor who
backgrounds the app mid-pairing should not come back to an empty sheet. `AddMemberViewModel`
mirrors its form into the handle — see the `persistForm` / `restoreForm` pair at the bottom
of that file, and note that it stores the paired device as primitives rather than dragging
Android's `Parcelable` into a domain model.

**Derive in the ViewModel, not in the composable.** Member counts, channel names,
"is anyone speaking" — resolve them once into the UI state (see `ChannelSummary`,
`MemberRow`) rather than recomputing them inside a `LazyColumn` item.

**Domain is thin.** Interfaces + data classes only. No UseCase/Interactor classes —
ViewModels call repositories directly. Add a UseCase only when real cross-repository
logic shows up, not to wrap a single repo call.

**The UI never sees an Entity.** Repository impls map `Entity <-> domain model` via
`toDomain()` / `toEntity()` extensions that live next to the Entity.

**Fakes hide behind interfaces.** Anything not built yet gets a domain interface and a
simulated implementation in `data/`, wired in `AppContainer` — never a `delay()` in a
`LaunchedEffect` inside a screen. `DeviceScanner` / `SimulatedDeviceScanner` is the
pattern to copy when the network layer lands.

## Local storage

Room on SQLite, chosen over hand-rolled `SQLiteOpenHelper` for compile-time-checked
queries and native `Flow` support.

- One `@Entity`/`@Dao` pair per feature under `data/<feature>/local/`.
- `AppDatabase` is the single database, `version = 1`, `exportSchema = false`. Bump the
  version and add a migration when a schema changes.
- `AppDatabase.build()` is called **only** by `AppContainer`, which is built **only** by
  `VikoApplication`.
- **A fresh install starts empty.** `SEED_DEMO_DATA` in `VikoApplication` is `false`,
  because onboarding — Home opening the pairing sheet over a *No team yet* empty
  state — only exists if there is genuinely nothing there. Flip it to `true` and
  `DatabaseSeeder.seedIfEmpty()` writes `SeedData` (4 channels, 20 workers, 3 alerts,
  4 messages) through the repositories for the populated demo. Either way it goes
  away once badges enrol themselves over the wire.
- Anything that reacts to "there is nothing here" must wait for `HomeUiState.isLoaded`.
  The initial UI state is empty by construction, so acting on it directly makes a
  populated install flash its empty state and re-run onboarding on the first frame.

## Build

- Room `2.8.4` via KSP (`com.google.devtools.ksp`, `2.2.10-2.0.2` — must move in lockstep
  with the Kotlin version in `gradle/libs.versions.toml`).
- `androidx.lifecycle` `2.8.7` supplies `viewmodel-compose`, `runtime-compose`
  (`collectAsStateWithLifecycle`) and `viewmodel-savedstate` (`createSavedStateHandle`).
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` — AGP 9's built-in
  Kotlin compilation rejects KSP's generated source dirs by default without it.
- `local.properties` is gitignored; a fresh clone needs `sdk.dir` (or `ANDROID_HOME`).

```bash
./gradlew :app:assembleDebug
```

## What is NOT built yet

**The entire transport layer.** This app is a fully working UI over a local database,
and nothing else. Specifically:

- `AndroidManifest.xml` declares **zero** network/Bluetooth permissions — no `INTERNET`,
  `ACCESS_WIFI_STATE`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`
  (needed for BLE scan pre-Android 12), `CHANGE_WIFI_MULTICAST_STATE` (needed for mDNS).
- No `network` package exists. BLE provisioning, mDNS/NSD discovery and the audio/control
  socket are all unwritten.
- `SimulatedDeviceScanner` fakes badge discovery. The real `BleDeviceScanner` belongs
  beside it, implementing the same `DeviceScanner` interface.
- Talking is UI-only: the mic buttons, the SOS broadcast and the announcement sheet
  toggle local state and animate. No audio is captured or sent.
- `DirectCallViewModel.simulateIncomingSpeech()` fakes a received translation on a timer.
  Delete it when real messages arrive. It emits only the translated line — the design
  shows no source-language transcript, so nothing renders the original Vietnamese.
- The AI pipeline (ASR → NMT → TTS) is not integrated.
- `EmergencyAlert.timeAgo` and `TranslationMessage.timestamp` are display strings, not
  timestamps. They need to become real time values before anything sorts or ages them.

Wire the transport in this order: manifest permissions → BLE provisioning (behind
`DeviceScanner`) → mDNS discovery → audio/control socket. Each step should land as a
`data/` implementation of a `domain/` interface, so no screen changes when it does.

The packet schemas, IP plan and pairing flow are specified in the parent folder's
`Detailed System.docx` — that document is the source of truth for anything on the wire.
