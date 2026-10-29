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
- Project scaffold: Gradle Kotlin DSL with version catalog, empty Compose activity,
  Spotless + ktlint, Android Lint, GitHub Actions CI, and a CI check that fails if the APK
  requests the INTERNET permission. (M0)
