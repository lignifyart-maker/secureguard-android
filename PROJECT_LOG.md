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
- Added a stable release-signing pipeline backed by a dedicated release keystore in GitHub Actions.
- Switched release publishing from debug APK output to signed `release` APK output.
- Added tag-driven `versionName` and `versionCode` generation in the release workflow so published builds can upgrade cleanly over older release builds.
- Improved the unused-app section so apps with no recent usage history can still be surfaced when Usage Access is available.
- Improved the oversized-app section so it prefers actual installed size from Android storage stats and falls back to APK-size estimates when needed.
- Added clearer in-app status feedback after returning from the uninstall flow.

## Core Runtime Status

- `assembleDebug` passes locally.
- DNS relay and short-window event dedupe were already added before this round and remain in place.
- The local VPN path still exists, but it is no longer the main product story.

## GitHub Release Status

- Initial release tag `v1.0.0` failed because the workflow expected `./gradlew`, but the repo does not include a Gradle wrapper.
- Fixed the workflow to use `gradle/actions/setup-gradle@v4` with Gradle `8.14`.
- Pushed workflow fix in commit `cada769`.
- `v1.0.1` release completed successfully.
- `v1.0.2` release completed successfully after the home-list and uninstall-flow fixes.
- `v1.0.3` release failed because the release signing config was referenced before creation.
- Fixed release signing config ordering in commit `734fba7`.
- `v1.0.4` release completed successfully and now publishes a signed `app-release.apk`.
- Releases page:
  - `https://github.com/lignifyart-maker/secureguard-android/releases`
- Latest release page:
  - `https://github.com/lignifyart-maker/secureguard-android/releases/tag/v1.0.4`
- Latest release APK:
  - `https://github.com/lignifyart-maker/secureguard-android/releases/download/v1.0.4/app-release.apk`
- The repository visibility was later changed from private to public so release links and update checks are no longer blocked by repo access.

## Important Commits

- `ffc7773` Strengthen DNS relay and event dedupe
- `97c4e4a` Refocus app around actionable cleanup flow
- `d9d5f40` Add update checker and slime branding
- `cada769` Fix GitHub release workflow build step
- `06dcb6b` Update project log for cleanup-focused direction
- `3eabb25` Polish home list limits and uninstall flow
- `4f3fc64` Improve cleanup flow and signed release pipeline
- `734fba7` Fix release signing config creation order

## Current State

- The app is now usable as a lightweight cleanup helper rather than a long technical dashboard.
- Users can now act on risky or removable apps instead of only reading about them.
- The home screen is cleaner, but still needs more polish around empty states and wording consistency.
- The home summary now shows the app version.
- The `值得注意的` section is capped to 10 items by default, with a `顯示更多` expansion path.
- The uninstall path now opens Android's uninstall flow correctly instead of stalling.
- The uninstall path now shows a clearer refresh status message after returning to the app.
- The oversized-app list now prefers actual installed size and marks whether a result is based on device totals or APK estimates.
- The unused-app list depends on Usage Access, but now handles missing recent history more gracefully instead of collapsing to zero too often.
- The update-check flow is implemented and the public GitHub release pipeline is now working end to end.
- `v1.0.4` is now published as a signed `release` APK from GitHub Actions.
- Installing a signed release build over an older debug-installed local build may still fail because Android does not allow upgrading across different signing keys.

## Next Recommended Steps

1. Update the in-app update flow so it downloads or installs the signed release APK directly instead of only opening the release page.
2. Polish the three-section home flow:
   - tighten empty states
   - smooth wording
   - improve section summary copy
3. Make the `很多天沒用的` section clearer when Usage Access is missing versus when the result is truly zero.
4. Improve oversized-app accuracy further if needed, especially on devices where storage stats fall back to APK-size estimates.
5. Decide whether `Protection mode` should stay as an advanced page, hidden tool, or be removed from the main experience entirely.
6. Decide whether to document a local signed-release build path in addition to the GitHub Actions release path.
