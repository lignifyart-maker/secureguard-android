# Project Log

## Current Branch

- `feature/recent-activity-panel`

## This Round

- Added a `RecentConnectionTimeline` model and observation use case.
- Stored recent activity in `PermissionAuditUiState` and observed it from `PermissionAuditViewModel`.
- Added a dashboard card for recent activity.
- Expanded recent activity rows to show:
  - target
  - source app
  - risk label
  - event type
  - relative time
- Added a summary line for recent activity volume.
- Added a clear action path for recent network events.

## Follow-up Round

- Stabilized the recent activity card layout so each item renders as a proper stacked row.
- Added event type labels to recent activity items.
- Added a recent-activity summary line.
- Wired the clear action from the dashboard into the screen content path.
- Kept the branch buildable after the clear-action integration fix.

## Merge Prep Round

- Aligned the recent-activity header so the clear action sits correctly in the panel header.
- Polished the empty state into a softer card-style hint instead of plain text.
- Kept the feature branch buildable and ready to merge back to `main`.

## Attribution Round

- Added an Android owner-lookup resolver for UDP connections inside the VPN runtime path.
- Wired DNS events through `ConnectivityManager.getConnectionOwnerUid(...)` before logging them.
- Persisted attribution confidence on `network_events`.
- Surfaced attribution status in the recent-activity panel.

## Attribution Hardening Round

- Limited attribution logging to outgoing DNS queries so reply traffic does not muddy app mapping.
- Hardened owner lookup fallback for address parsing and UID-without-package cases.
- Surfaced attribution status in the live feed source text.
- Added attribution state badges to the recent-activity panel.

## Recent Activity UX Round

- Added a guarded clear flow for recent activity so repeated taps do not fire duplicate clears.
- Surfaced transient clear-status copy in the recent-activity card.
- Improved the empty state copy so it explains the difference between protection-off and waiting-for-traffic states.
- Tightened recent activity row layout with clearer event, risk, and attribution chips.

## Recent Activity Expansion Round

- Expanded recent activity observation so the dashboard can keep a deeper local history window.
- Added a `View all` and `Collapse` path on the recent-activity card instead of keeping it fixed to three rows.
- Preserved a compact three-row preview by default while allowing the full in-place history list when needed.

## Attribution Recovery Round

- Added a short-lived port-history cache to the VPN owner resolver so brief Android lookup misses can reuse a very recent app match.
- Kept the fallback bounded by a small cache and expiry window instead of treating it as a long-term attribution source.
- Surfaced the recovered attribution state in both the live feed copy and recent-activity badges.

## Non-DNS UDP Round

- Extended the VPN read loop so outgoing UDP traffic is no longer limited to DNS-only logging.
- Added lightweight non-DNS UDP event classes for encrypted app traffic, time sync traffic, peer/call traffic, and generic UDP traffic.
- Kept non-DNS logging deduplicated with a short signature window so the feed does not flood immediately.
- Updated live feed and recent-activity labels so these new UDP events render as meaningful traffic types instead of falling back to DNS wording.

## First Impression Round

- Rewrote the hero copy so the app explains its value in plain language on first launch.
- Added a quick-start card that tells users exactly how to get useful results in three steps.
- Tightened protection-mode language so it feels more product-like and less like internal tooling copy.
- Clarified that the live feed is a plain-language traffic view rather than a packet log.

## Recent History Screen Round

- Added a dedicated recent-activity history screen instead of relying only on the dashboard panel.
- Wired the top app bar into a back path so users can move between dashboard and history cleanly.
- Reused the recent-activity item card layout across the dashboard preview and the full history screen.

## Current State

- `assembleDebug` passes.
- Recent activity now appears as a dashboard section rather than a single preview string.
- The UI is still a summary view; it is not yet a full drill-down screen.
- Recent activity is now a usable dashboard panel with an expandable in-place history view, but not yet a dedicated history screen.
- Per-app attribution is now partially wired for outgoing DNS events and a first non-DNS UDP path, with better fallback handling, short-lived recovery from lookup misses, and clearer dashboard status.

## Next Recommended Steps

1. Finish the recent activity card polish:
   - better empty state
   - clearer button placement
   - cleaner row spacing
2. Push the clear action all the way through UX copy and verify it behaves well after repeated toggles.
3. Add a dedicated recent-activity screen or expanded panel.
4. Continue per-app attribution hardening for VPN events and extend beyond the current DNS-first path.
5. Start shaping a broader non-DNS attribution path after the DNS-first route is stable enough.
