# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Domain core (M1): `Settings`, `BlockCatalog`, `FocusState`, `ScheduleCalculator` (midnight-crossing
  windows, DST and time zone safe) and `FocusController` (tile taps, timers, schedule edges, breaks,
  strict mode, reboot recovery), with unit tests.
- Persistence (M2): DataStore-backed `SettingsRepository`, `FocusStateStore` and `CounterStore`
  (daily count resets at local midnight on read), `AppContainer` for manual dependency injection.
- Quick Settings tile (M3): tap toggles Focus for the configured duration with an exact auto-off alarm;
  subtitle shows `1h 12m left`, `Until 6:00 PM`, `Break · 4m` or `14 blocked`; long-press opens settings.
  `FocusCoordinator` applies every state change and keeps the single alarm in sync.
- Accessibility service (M4) registered for Instagram, Facebook, YouTube and Reddit only, listening to
  window-state and (throttled) content-changed events, and fully inert while Focus is off.
  Debug-only screen inspector logging redacted outlines to Logcat (`InboxOnlyInspector`), plus
  `scripts/inspect.sh`. Placeholder `rules.json` that blocks nothing. The tile shows `Set up` and opens
  the app while the accessibility service is disabled.
- Blocking engine (M5): data-driven `rules.json` model (allowed screens, blockable surfaces, Reel viewer,
  ordered actions), `BlockDecider` (allowed screens always win, fail open), `ReelFromChatTracker`
  (allow one Reel opened from a chat; swiping to another goes back), `BlockThrottle` (700 ms per-app
  cooldown; escalates redirect → back → home if a surface reappears), `RulesValidator`, and the
  service's signature reader and action executor. Unit tests use hand-written fixture signatures.
- Schedule, breaks and strict mode (M6): alarms re-registered on boot, clock change, time zone change,
  app update and exact-alarm permission change, and on every process start. Tapping the tile inside a
  window starts a break (or, in strict mode, shows "Strict mode: Focus until 6:00 PM").
- Settings screen and onboarding (M7): one Compose screen (block list, schedule, tile duration, break
  length, Reels from chats, strict mode, permission footer with Fix buttons), Material 3 with dynamic
  color. Three-step onboarding: accessibility with Android 13+ restricted-settings help, battery
  optimization with Samsung "Never sleeping apps" guidance, and adding the tile.
- Release workflow (M8): pushing a `v*` tag builds a signed APK from repository secrets, verifies its
  signature and the no-INTERNET guarantee, and attaches it to a GitHub Release.
- Documentation (M8): final README, `docs/TECHNICAL.md` (architecture, Mermaid diagrams, rules format,
  build-from-source, release signing), `docs/UPDATING_RULES.md`, `docs/TESTING.md`, `docs/DECISIONS.md`.

- Real selectors (Checkpoint A): `rules.json` v2 written only from screens captured on a Galaxy S25+;
  test fixtures replaced with the real captured signatures, and tests now run the shipped rules.

### Changed
- `ScreenSignatureReader` makes one bounded pass over the window instead of targeted ID lookups, because
  Compose screens (Reddit and others) expose bare tags that ID lookup can't find.

### Removed
- Facebook "Video / Watch" surface: it doesn't exist in the current Facebook app.

### Fixed
- The tile is only updated while Quick Settings is listening, avoiding updates after the shade closed. (M8)
- Project scaffold: Gradle Kotlin DSL with version catalog, empty Compose activity,
  Spotless + ktlint, Android Lint, GitHub Actions CI, and a CI check that fails if the APK
  requests the INTERNET permission. (M0)
