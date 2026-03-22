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

## Current State

- `assembleDebug` passes.
- Recent activity now appears as a dashboard section rather than a single preview string.
- The UI is still a summary view; it is not yet a full drill-down screen.
- Recent activity is now a usable dashboard panel, but not yet a dedicated history screen.
- Per-app attribution is still incomplete. Most VPN-captured activity still shows `Unknown app`.

## Next Recommended Steps

1. Finish the recent activity card polish:
   - better empty state
   - clearer button placement
   - cleaner row spacing
2. Push the clear action all the way through UX copy and verify it behaves well after repeated toggles.
3. Add a dedicated recent-activity screen or expanded panel.
4. Start per-app attribution groundwork for VPN events.
