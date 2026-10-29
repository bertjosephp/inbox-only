# Decisions

Short ADR-style entries: what we decided, and why. Newest at the bottom.

## ADR-001: AccessibilityService, not VPN/DNS blocking
**Decision:** Detect blocked screens with an AccessibilityService and respond with back/home or an intent.
**Why:** Instagram, Facebook and YouTube serve DMs and Reels from the same servers, so a network blocker
can't block one without the other. It would also break messaging. Accessibility only *observes* the UI,
never touches other apps or their servers, so there is no account-ban risk.

## ADR-002: Manual dependency injection, no Hilt
**Decision:** A small `AppContainer` built in the `Application` subclass.
**Why:** The app has a handful of objects. Hilt adds annotation processing, generated code and build
time for no real benefit at this size.

## ADR-003: Spotless + ktlint instead of the ktlint Gradle plugin
**Decision:** Run ktlint through Spotless with explicit file globs.
**Why:** AGP 9 compiles Kotlin itself ("built-in Kotlin") without the `kotlin-android` plugin, which
the standalone ktlint plugin uses to discover source sets. Spotless just targets `**/*.kt`.

## ADR-004: Build with JDK 21 locally, target Java 17
**Decision:** Bytecode targets Java 17 (`compileOptions`); CI runs on Temurin 17; any JDK ≥ 17 builds it.
**Why:** The spec asks for JDK 17. The owner's Mac has JDK 21, which AGP fully supports. Targeting 17
keeps the two identical in output.

## ADR-005: Focus state is reconciled from absolute times, with one alarm at a time
**Decision:** The stored `FocusState` holds absolute `Instant`s. Every reader passes it through
`FocusController.reconcile(state, settings, now, zone)`, and only the single earliest future edge
(timer end, break end, window end, next window start) is registered as an alarm.
**Why:** Alarms can be late (Doze), lost (reboot, force-stop) or made stale (time zone change, schedule
edit). Recomputing from the clock means a missed alarm can never leave Focus in the wrong state. One
alarm is simpler to reason about than a set of them.

## ADR-006: A schedule window absorbs manual Focus
**Decision:** If manual Focus is running when a schedule window starts, the state becomes `Scheduled`
and ends with the window, even if the manual timer would have run longer.
**Why:** It keeps one invariant: inside a window the state is always Scheduled or Break, so strict mode
and breaks behave the same however Focus got turned on. The edge case (manual timer longer than the
window, and also running into it) is rare and harmless.

## ADR-007: Schedule days are start days
**Decision:** A 22:00–06:00 window on Friday runs until Saturday 06:00 even if Saturday isn't selected.
A window whose start equals its end is invalid (no window).
**Why:** That's how alarm clocks and calendars treat overnight events. DST gaps shift times forward and
repeated times use the first occurrence (java.time defaults).

## ADR-008: Exact alarms via USE_EXACT_ALARM
**Decision:** Declare `USE_EXACT_ALARM` (Android 13+) and `SCHEDULE_EXACT_ALARM` with
`maxSdkVersion="32"` (Android 12/12L only); schedule with `setExactAndAllowWhileIdle`.
**Why:** Doze delays inexact alarms by many minutes, which would make breaks and schedule edges visibly
late. `USE_EXACT_ALARM` is auto-granted and can't be revoked by the user. Google Play only allows it for
alarm-clock and calendar apps, but this app is sideloaded and never reviewed by Play.
`SCHEDULE_EXACT_ALARM` can be revoked from settings and would need a permission prompt. Even when an
alarm is late, `reconcile()` corrects the state the next time anything reads it.

## ADR-009: Regular (non-"active") tile, rendered only while the shade is open
**Decision:** The tile doesn't use `META_DATA_ACTIVE_TILE`. Status is computed in `onStartListening`,
which the system calls whenever the shade shows the tile, and it re-renders if the stored state changes
while the shade stays open.
**Why:** An "active" tile only gets `onStartListening` when the app requests it, so a countdown subtitle
would go stale. A regular tile costs nothing while the shade is closed and needs no ticking timer.

## ADR-010: "Set up" tile instead of STATE_UNAVAILABLE
**Decision:** While the accessibility service is disabled, the tile is `STATE_INACTIVE` with the subtitle
`Set up`, and tapping it opens the app.
**Why:** The spec asked for `STATE_UNAVAILABLE` plus "tap opens onboarding", but Android doesn't deliver
taps to an unavailable tile, so that combination can't work.

## ADR-011: Turning the service off keeps its package filter
**Decision:** When Focus is off, `setServiceInfo()` sets `eventTypes = 0` and leaves `packageNames` as
declared in XML.
**Why:** In `AccessibilityServiceInfo`, `packageNames = null` means *every app*. "Clearing" packages
would make the service hear everything the moment event types came back. A unit test checks the XML
filter lists exactly the four packages in `rules.json`.

## ADR-012: The debug inspector listens even when Focus is off
**Decision:** In debug builds only (`InspectorLogger.ENABLED`), the service stays subscribed while
Focus is off, so screens can be captured without blocking getting in the way. Blocking itself still
requires Focus. Release builds have `ENABLED = false` and stay fully inert.
**Why:** Capturing a blocked screen with Focus on would immediately bounce out of it.

## ADR-013: Throttle content-changed events, don't debounce
**Decision:** At most one screen check per 250 ms window after a content-changed event; window-state
changes are checked immediately.
**Why:** A playing video or Reel fires content changes continuously. A trailing debounce would never
settle and never fire, which would make Reels unblockable.

## ADR-014: Block actions per surface, chosen by the owner
**Decision:** Each surface in `rules.json` lists actions tried in order, with Home as the guaranteed last
resort. Intended actions for a blocked landing screen: Instagram → DM inbox (deep link), Facebook →
Messenger (or Facebook's own inbox, if one exists on device), YouTube → close (Home), Reddit → close (Home).
**Why:** YouTube and Facebook open straight onto their feed. Pressing Back there just exits, so a
redirect is the only way to keep the useful parts reachable. The owner chose to simply close YouTube and
Reddit. Deep links are only added after they're verified on the phone.

## ADR-015: Reel identity is an in-memory hash
**Decision:** To notice a swipe to a different Reel, the service hashes the content description or text
of one rule-chosen node in the Reel viewer (e.g. the author label). It keeps only the hash, in memory.
**Why:** `TYPE_VIEW_SCROLLED` is off-limits for battery reasons, and comparing a hash is the cheapest
way to tell Reels apart. If no node is suitable, the tracker allows the opened Reel until the viewer
is left, so Reel-from-chat stays usable rather than broken.

## ADR-016: One bounded pass over the window instead of targeted ID lookups
**Decision:** `ScreenSignatureReader` visits each window's nodes once (capped at 2,000) and compares them
against the rule-referenced IDs and descriptions in memory, instead of calling
`findAccessibilityNodeInfosByViewId` / `findAccessibilityNodeInfosByText`.
**Why:** The real captures showed that Reddit (and parts of Instagram and YouTube) are built with Jetpack
Compose, which exposes bare tags like `home_screen_surface` rather than `package:id/name` resources. The
framework's ID lookup resolves real resource IDs, so it would never find those tags, and Reddit would
silently never be blocked. This departs from the original "no full-tree walks" goal, but the cost stays
small: real screens have a few hundred nodes, checks are throttled to at most 4 a second, and they only
happen while Focus is on and one of the four apps is in front.

## ADR-018: Facebook Reels opened from Messenger
**Decision:** Facebook's Reel viewer is a `reelViewer` with `openedFromWindowClasses`: when Facebook opens
`ImmersiveActivity`, the next Reel counts as opened from a chat. Its identity is the hash of the
"View ‹creator›'s reels" description (`identityDescriptionPattern`), because Facebook's view IDs are
obfuscated. A swipe to another Reel opens Messenger again.
**Why:** The service can't see Messenger, so "came from a chat" can't be observed directly. The capture
showed that Reels from Messenger open in `ImmersiveActivity`, while every Reel opened inside Facebook
(feed or Reels tab) stays in `FbMainTabActivity`. The window opens before its content is ready, so the
tracker is told on the window-state event itself. Back from the viewer lands on Facebook's feed rather
than Messenger, so the block action launches Messenger directly.

## ADR-017: Facebook rules use tab descriptions; no "Video" surface
**Decision:** Facebook's view IDs are obfuscated (`(name removed)`), so its rules match the bottom tabs'
content descriptions (`"Home, tab 1 of 5"`, `"Reels, tab 2 of 5"`) and the Reel viewer's
`"Reel details"`. The "Video / Watch" surface was removed from `BlockCatalog`.
**Why:** The owner's Facebook has no Video/Watch screen; its tabs are Home, Reels, Groups, Notifications and
Profile. Descriptions are English-UI strings, so a language change or a badge in the label can stop a
match. That fails open, and UPDATING_RULES.md covers the fix.

## ADR-019: A block screen instead of an instant exit
**Decision:** A blocked surface opens Inbox Only's own `BlockScreenActivity` over the app. It's an opaque,
fading Material 3 page with the rule's way out as its main button (`label` on `redirect`/`launch`
actions, else "Go back" or "Close ‹app›") plus "Close ‹app›". The page runs in its own task, hidden
from Recents; its Back gesture goes Home. "Go back" is handed to the accessibility service, which
presses Back once the page has faded out. If the same surface comes back within 2 s of the page, the
throttle escalates and the actions run directly, as before.
**Why:** On device, being yanked out of an app felt abrupt; a page says *why* and lets the user choose
where to go. It's still only our own activity opened by an intent, so nothing in the target apps is
touched (no ban risk). Opaque, not a translucent dialog, so the feed isn't visible or audible behind
it. Its own task stops "close" from revealing Inbox Only's settings, and Back going Home stops the
page from dropping the user back into the feed it covers. The escalation keeps a safety net if the page
ever fails to appear.

## ADR-020: Instagram and Facebook blocks always lead to messages
**Decision:** Every Instagram block screen offers **Open DMs**, and every Facebook one **Open Messenger**,
plus "Close ‹app›". That includes the Reels tabs and a Reel swiped to from a chat. "Go back" stays only
for YouTube Shorts. `back` remains in those rules after the main action, but only as the throttle's
fallback, never as a button. `BlockScreenFormatterTest` pins this.
**Why:** The owner's call after using it: on these two apps, the useful way out is always the inbox,
and "Go back" could land on another feed. For a Reel swiped to from a DM this means the inbox rather than
the exact thread, which is one tap away.

## ADR-021: One YouTube video per visit
**Decision:** YouTube's watch page stays allowed, but a `followOn` rule blocks moving on to a different
video while staying on it ("Next videos" in settings, on by default). A visit starts on the first watch
page after any other screen, or when YouTube's `MainActivity` window opens (a link while YouTube is in
the background). The video's identity is a hash of the collapsed `title` view, kept in memory only.
YouTube shows that view only while a video loads, and not for a video opened from a link, so a visit
may start without an identity; a title seen within 2 s of the start still counts as the first video.
Shorts became close-only, so every YouTube block offers **Close YouTube** alone.
**Why:** On device, a linked video's "up next" list made the watch page a feed. The capture
(`capture-5.txt`) showed the `title` view appears exactly once per newly loaded video (after a link,
search result or related tap), never while scrolling, and that the title-like first item of the list is
replaced by related cards as you scroll, so that can't be the identity. Known limit: a link opened while
YouTube is closed doesn't fire the reset window; it still works because a linked video shows no title,
but if YouTube ever starts showing one, that first video could be blocked. Closing the block screen and
reopening YouTube on the blocked video starts a new visit; that takes deliberate effort, so it's accepted.

## ADR-022: Detect a blocker that's enabled but not running
**Decision:** `AccessibilityStatus.health()` returns `NOT_ENABLED`, `STOPPED` or `RUNNING`. `STOPPED`
means switched on in Settings but missing from `AccessibilityManager.getEnabledAccessibilityServiceList`,
which the system answers from the services it has actually bound. The tile then shows `Restart blocker`
(a tap opens the app) and the settings footer shows "Blocker stopped: switch it off and on" with **Fix**.
**Why:** On the owner's Galaxy S25+, every reinstall left the service enabled but unbound
(`dumpsys accessibility`: listed under *Enabled*, not *Bound*), so nothing was blocked and nothing said
so. An app can't rebind its own accessibility service, so the honest fix is to say so and point to the
switch. Right after boot the service can take a moment to bind, so the warning may flash briefly.
