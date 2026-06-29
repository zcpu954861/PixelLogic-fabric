# Graph Draft UX Simplification

Date: 2026-06-29

Branch: `feature/v1-graph-draft-simple-ux`

## User Feedback

The graph draft UI exposed an engineering sequence as normal user actions:

- save draft
- validate draft
- commit
- reset test state

Without Codex explaining the order, the UI did not make it obvious what the user should click.

## Change

The Slot-Based horizontal block flow remains the visual baseline.

Normal user actions are now:

- `保存`: writes pending edits, validates them, and promotes them only when validation passes.
- `测试运行`: saves pending valid edits first, resets the demo test state, starts the test run, and refreshes trace output.

Validation remains fail-closed: invalid edits do not replace the committed graph or runtime graph.

## Boundaries

- Internal draft, validate, and commit APIs remain.
- No Region.
- No Channel.
- No old TZZ adapter.
- No full graph editor expansion.
- No merge, tag, or release.
