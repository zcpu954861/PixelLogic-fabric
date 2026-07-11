# PixelLogic Container Control Flow v1

## User Sketch

- C-shaped container block: implemented as a control-flow puzzle shape with a top header, continuous left rail, open body, and bottom rail.
- body slot: catalog exposes `containerSlots=["body"]`; graph nodes store `parentContainerId` and `parentSlot`.
- auto height: WebUI computes container height from body children and nested containers.

## Scope

- container block UI: C-shaped control blocks in the existing slot-flow canvas.
- body slot: one `body` slot for v1.
- drag in/out: blocks and chains can be assigned to or removed from a container body through the existing drag loop.
- nesting: loop blocks can be placed inside loop bodies; validation caps depth.
- loop count: `control.loop.count`.
- forever loop: `control.loop.forever`.

## Graph Model

- flat graph: nodes and edges remain flat lists.
- parentContainerId: child nodes point to the containing loop node.
- parentSlot: v1 uses `body`.
- body edges: internal chain order still uses normal control edges.
- save/load: `GraphDocument.NodeDocument` persists membership fields without changing `schemaVersion`.

## Runtime / Simulation

- loop count: executes the body entry chain N times and then returns the outer `done` slot.
- forever loop: runs a finite simulation cap and then stops without an outer next.
- safety caps: count is limited to 1..100; forever simulation cap is 20 rounds.
- trace: loop entry, iteration start/end, completion, and cap-stop messages are human-readable.

## UI

- C-shaped block: not a normal card or generic group panel.
- body drop zone: empty body shows a dashed drop zone.
- auto expand: body children and nested container height extend the outer block.
- nested containers: nested body children remain visually grouped by parent membership.
- product-quality refinements: C shape uses current PixelLogic outlines, color, soft body background, and green hover glow.

## Validation

- count: integer 1..100.
- interval: integer seconds greater than 0.
- max depth: 4 nested container levels.
- empty body: warning, not a save blocker.

## Follow-up Scope

- loop until: delivered later by `LOOP_UNTIL_CONDITION_RACK_V1.md` using the same container body model plus a typed predicate rack.
- break/continue: not implemented.
- if/else: not implemented.
- variable count: not implemented.
- real MC adapter: not implemented.

## Known Limits

- Control Flow Continuation v1 now preserves count/forever loop progress, nested return frames, cumulative steps, and the forever cap across `timer.wait` resumes.
- Continuations remain in-memory and do not survive server restart; waiting for events and break/continue are still out of scope.
- Catalog drag and active-chain insertion now render live ghost/placement previews without mutating the graph before pointer release.
- Copy/duplicate behavior is not added because the editor has no existing copy command.
