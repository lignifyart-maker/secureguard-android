# Project Log

## Current Branch

- `main`

## Product Direction

- The app is now being positioned as a simple phone-cleanup and safety helper for non-technical users.
- The primary value is:
  - surface apps worth attention
  - surface apps that feel too large
  - surface apps unused for many days
  - give the user an immediate next step
- `Protection mode` is no longer the product centerpiece.

## Recent Delivery

- Refocused the home screen around a single score and three clear entry points:
  - `值得注意的`
  - `過於龐大的`
  - `很多天沒用的`
- Replaced the previous long dashboard-style home flow with a shorter, action-first cleanup flow.
- Added tappable app cards that open an action dialog instead of leaving the user at a dead end.
- Added direct next steps from app cards:
  - open system app info
  - open Android uninstall flow
- Preserved the current section when the user returns from uninstall and refreshed the list automatically.
- Added rough app-size surfacing via APK size so the oversized-app list can work now.
- Added recent-usage surfacing via Usage Stats so the unused-app list can work when access is granted.
- Added a clear prompt for Usage Access when the user opens the unused-app section without that permission.
- Rewrote risk reasons into short Traditional Chinese user-facing copy.
- Renamed the app to `手機史萊姆`.
- Replaced the launcher icon with a green slime icon.
- Added a manual `檢查更新` button on the home summary card.
- Wired update checking to GitHub latest release:
  - no forced update
  - no automatic popup on launch
  - tap to check
  - if newer, show a simple download dialog and open the release page
- Added a GitHub Release workflow so APK distribution can move from manual file passing to release links.

## Core Runtime Status

- `assembleDebug` passes locally.
- DNS relay and short-window event dedupe were already added before this round and remain in place.
- The local VPN path still exists, but it is no longer the main product story.

## GitHub Release Status

- Initial release tag `v1.0.0` failed because the workflow expected `./gradlew`, but the repo does not include a Gradle wrapper.
- Fixed the workflow to use `gradle/actions/setup-gradle@v4` with Gradle `8.14`.
- Pushed workflow fix in commit `cada769`.
- Pushed release retry tag `v1.0.1`.
- Current release run to watch:
  - `https://github.com/lignifyart-maker/secureguard-android/actions/runs/23411471656`

## Important Commits

- `ffc7773` Strengthen DNS relay and event dedupe
- `97c4e4a` Refocus app around actionable cleanup flow
- `d9d5f40` Add update checker and slime branding
- `cada769` Fix GitHub release workflow build step

## Current State

- The app is now usable as a lightweight cleanup helper rather than a long technical dashboard.
- Users can now act on risky or removable apps instead of only reading about them.
- The home screen is cleaner, but still needs more polish around empty states and wording consistency.
- The oversized-app list is a first-pass approximation based on APK size, not full storage usage yet.
- The unused-app list depends on Usage Access and should be considered functional but not fully polished.
- The update-check flow is implemented, but the release pipeline still needs a confirmed successful end-to-end run.

## Next Recommended Steps

1. Confirm the `v1.0.1` GitHub Release run succeeds and verify the APK appears on the Releases page.
2. Polish the three-section home flow:
   - tighten empty states
   - smooth wording
   - improve section summary copy
3. Improve oversized-app accuracy by moving beyond APK-only size if needed.
4. Decide whether `Protection mode` should stay as an advanced page, hidden tool, or be removed from the main experience entirely.
5. Add small success feedback after uninstall return so users know the list was refreshed on purpose.
