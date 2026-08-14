# Viko — Architecture Foundation

Android app, Kotlin + Jetpack Compose, package root `com.example.viko`.

## Layers

Three layers, each a top-level package. Domain and Data are split per-feature inside;
`view` is already split per-feature by screen.

```
view/            UI layer: screens (per feature) + theme
  screens/<feature>/
    <Feature>Screen.kt      top-level screen(s), one per file — each has a route in
                             navigation/Routes.kt (or is a sheet-flow shown from one,
                             e.g. AddMemberFlow)
    components/             everything that screen(s) use — sheets, cards, list items,
                             small enums — one composable/type per file
  theme/
components/      Feature-agnostic UI atoms used across 2+ features (buttons, badges,
                 dividers — no domain-model params), one file per component
navigation/      Routes.kt (route definitions) + AppNavigation.kt (the NavHost)
utils/           Helper functions (date formatting, etc.) — created on first real need
type/            Small enums shared across features, one file per enum
                 (WorkerStatus, AlertStatus, CallState)
domain/<feature>/
  model/         Domain data classes (e.g. Worker, Channel)
  repository/    Repository *interfaces* — the only thing the UI layer depends on
data/<feature>/
  local/         Room @Entity + @Dao for that feature
  repository/    Repository *implementations*, map Entity <-> domain model
data/local/AppDatabase.kt   Single Room database, one table per feature
di/AppContainer.kt          Manual DI: builds the DB once, exposes repository singletons
```

Features today: `team` (Worker, NearbyDevice), `channel`, `alert`, `message`.

Rule of thumb for a new feature: `domain/<feature>/model` + `domain/<feature>/repository`
(interface) first, then `data/<feature>/local` (Entity + Dao) + `data/<feature>/repository`
(impl), wire it into `AppDatabase` and `AppContainer`.

## Compose file conventions

One composable per file, file name matches the composable name, everywhere — screens,
`components/`, `<feature>/components/`, and `type/`'s enums.

- **Where a composable lives** is decided by content, not caller count:
  - Feature-agnostic UI atom (takes primitives/lambdas, no domain-model params, e.g.
    `VikoBlueButton`, `StatusDot`) → top-level `components/`.
  - Anything that renders a specific feature's content (takes a domain model like `Worker`
    or `Channel`, or is a sheet/card that belongs to one feature's flow) →
    `view/screens/<feature>/components/`, **even if another feature's screen also calls
    it** — e.g. `SelectChannelSheetContent` lives in `channel/components/` even though
    `team/AddMemberFlow.kt` uses it too, because it's channel content, not generic UI.
    Cross-feature imports of another feature's `components/` are normal here.
  - A screen's own top-level composable (has a route, or is the sole entry point of a
    sheet-flow like `AddMemberFlow`) stays directly under `view/screens/<feature>/`, not
    in `components/`.
- **Private helpers** (a sub-composable only its one caller in the same file uses) can stay
  `private` and colocated in that caller's file. The moment a second file needs it, split
  it into its own file **and drop `private`** — Kotlin's `private` is file-scoped, not
  package-scoped, so a moved-but-still-private function silently breaks the original caller.
- **Every composable gets a `@Preview`**: a `private fun <Name>Preview()` in the same file,
  wrapped in `VikoTheme { ... }`, calling the composable with `AppState()` (self-seeds from
  mock data — no fixtures needed) or a directly-constructed domain model for simple cases.
  This is how you check a screen's UI without running the app.

## Local storage

Room, on top of SQLite. Chosen over hand-rolled `SQLiteOpenHelper` for compile-time-checked
queries and native `Flow` support.

- One `@Entity`/`@Dao` pair per feature under `data/<feature>/local/`.
- Repository implementations map `Entity <-> domain model` (`toDomain()` / `toEntity()`
  extension functions live next to the Entity).
- `AppDatabase` (`data/local/AppDatabase.kt`) is the single Room database, `version = 1`,
  `exportSchema = false`. Bump the version and add a migration when a schema changes.

## Domain layer is thin

`domain/<feature>/repository` interfaces + `domain/<feature>/model` data classes only.
No UseCase/Interactor classes — ViewModels call repositories directly. Add a UseCase only
when real cross-repository business logic shows up (not just to wrap a single repo call).

## DI

Manual — `di/AppContainer.kt` builds the Room database once and exposes repository
singletons. No Hilt. Revisit if the dependency graph grows past what a single container
class can hold clearly.

## Build

- Room `2.8.4`, wired via KSP (`com.google.devtools.ksp`, version `2.2.10-2.0.2` — matches
  the Kotlin version pinned in `gradle/libs.versions.toml`, must move in lockstep with it).
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` — AGP 9's built-in
  Kotlin compilation rejects KSP's generated source dirs by default without it.

## Current state — what's NOT wired up yet

The 8 existing screens still run on `mock/AppState.kt` (in-memory, seeded from
`mock/MockData.kt`), not on the Room-backed repositories. The Domain and Data layers above
are fully built, compile, and are ready to use, but no screen has been migrated off the
mock yet. Migrate one feature at a time: build a ViewModel per feature that takes the
repository from `AppContainer`, swap the screen(s) from `appState.workers` etc. to
collecting the repository's `Flow`, then delete the corresponding slice of `AppState`/
`MockData` once nothing references it.
