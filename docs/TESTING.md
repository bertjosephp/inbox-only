# On-device acceptance checklist

Run this end to end on the Galaxy S25+ (Final Checkpoint B), on the **release APK from GitHub
Releases**, installed exactly as the README says. Tick each box; note any failure with what you did
and what happened.

Before starting: **uninstall any debug build** (e.g. from Checkpoint A). Debug and release builds are
signed differently, so Android won't install one over the other. The four apps are installed and logged in, Messenger is installed, and you have a
friend (or second account) who can send you a DM and a Reel.

## 1. Install and setup (README steps, word for word)
- [ ] Downloaded the APK from Releases on the phone and installed it via "Allow from this source".
- [ ] Samsung Auto Blocker / Play Protect steps in the README were accurate (or not needed).
- [ ] Onboarding step 1: accessibility. The restricted-settings instructions worked if needed. The step
      advanced by itself once the service was on.
- [ ] Onboarding step 2: battery. The *Allow* dialog appeared. "Never sleeping apps" instructions matched One UI.
- [ ] Onboarding step 3: *Add tile* showed the system dialog and the tile appeared (or manual steps worked).
- [ ] After setup, opening the app shows the settings screen. Footer shows all three permissions ✓.

## 2. Tile
- [ ] Tile tap → Focus on, tile active, subtitle like `1h left` (default tile duration 1h).
- [ ] Tap again → off.
- [ ] Set tile duration to 30m and turn Focus on. The subtitle counts down whenever you reopen the shade.
- [ ] Auto-off: set duration to 30m, turn on, wait (screen off is fine). Focus turns off at the time.
- [ ] "Until off" shows `On` (or `N blocked`) and never turns off by itself.
- [ ] Long-press the tile → settings screen opens.
- [ ] Turn the accessibility service off → tile shows `Set up`; tapping it opens the app. Turn it back on.
- [ ] After blocking some screens, the subtitle shows `N blocked`. Tomorrow it starts from 0.

## 3. Blocking (Focus on, default settings)
For each screen: open it from the app's normal navigation and check the result.

**Instagram**
- [ ] Home feed (opening the app) → redirected to the DM inbox (or backed out).
- [ ] Reels tab → blocked.
- [ ] Explore → blocked. Searching for a user still works.
- [ ] DM inbox and a DM thread → allowed; you can read and send messages.
- [ ] Notifications (heart) → allowed.

**Facebook**
- [ ] News feed (opening the app) → Messenger opens.
- [ ] Reels → blocked. Video / Watch → blocked.
- [ ] Notifications → allowed.

**YouTube**
- [ ] Home (opening the app) → app closes.
- [ ] Shorts → blocked.
- [ ] A video opened from a link or share → plays.
- [ ] Search and Subscriptions → allowed when reachable (note: Home closes the app, so they're reached via links).

**Reddit**
- [ ] Opening from the launcher → app closes immediately.
- [ ] A Reddit link from a Google search → the thread opens and is readable.
- [ ] Back from that thread into a feed → app closes.
- [ ] Popular, a subreddit, search results, the full-screen video feed → blocked.

**General**
- [ ] No back-button loop anywhere: the app never fights you for more than a moment.
- [ ] Switching a surface off in settings (e.g. Instagram → Explore) makes it reachable at once.
      Switching an app's master toggle off makes the whole app normal.
- [ ] Focus **off** → everything behaves normally in all four apps.

## 4. Messaging is never blocked
- [ ] Messenger works fully with Focus on.
- [ ] Inline reply from an Instagram DM notification works with Focus on.
- [ ] Inline reply from a Messenger notification works with Focus on.
- [ ] Opening an Instagram DM from a notification lands in the thread, not blocked.

## 5. Reel from a chat
- [ ] Setting "Allow one": a friend's Reel in a DM opens and plays.
- [ ] Swiping to the next Reel → you're sent back to the chat.
- [ ] Setting "Block": the Reel from the DM is blocked too.

## 6. Schedule, breaks and strict mode
- [ ] Set a schedule starting ~2 minutes from now, ending 5 minutes later, with today selected.
      Focus turns on at the start (tile: `Until H:MM`) and off at the end.
- [ ] During the window, tap the tile → `Break · 5m`; blocking pauses; Focus resumes after 5 minutes.
- [ ] During a break, tap the tile → Focus resumes immediately.
- [ ] Strict mode on, during the window, tap the tile → toast `Strict mode: Focus until …`; nothing changes.
- [ ] Schedule with an overnight window (e.g. 23:00–07:00) shows "Runs overnight" and works across midnight (optional).

## 7. Survives reboot
- [ ] Manual Focus on → reboot → still on (tile active) and blocking works after unlock.
- [ ] Start a break → reboot → the break still ends on time (or has already ended correctly).
- [ ] Schedule edge passes while the phone is off → after boot, the state is right for the current time.
- [ ] Change the time zone in Settings → schedule follows the new local time.

## 8. Battery and privacy spot checks
- [ ] Settings → Battery → app usage: Inbox Only shows ~0% after a normal day.
- [ ] Settings → Apps → Inbox Only → Mobile data: 0 B used (the app has no internet permission).

## 9. Uninstall
- [ ] Uninstall like any app. No leftover tile, accessibility entry or settings changes.
