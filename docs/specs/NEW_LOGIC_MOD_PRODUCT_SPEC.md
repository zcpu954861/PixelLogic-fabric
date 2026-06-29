# PixelLogic v1 Product Spec

PixelLogic is a visual logic flow mod for Minecraft server owners. The v1 goal is small and strict: a user can build, save, validate, simulate, run, and debug one simple logic flow through a slot-based horizontal block UI.

This document is a product boundary. It is not an implementation plan and it does not authorize runtime, API, Java, or WebUI source changes by itself.

## Product Promise

PixelLogic lets server owners create minigame logic with visible cards, slots, and direct typed edges instead of datapacks, command blocks, scoreboard scripts, or complex JSON.

The normal user should understand a flow by reading the canvas:

```text
Trigger -> Condition -> pass branch actions
                    -> fail branch actions
```

The user should not need to know about loader events, channel names, event buses, relays, listeners, or internal adapter concepts.

## Highest Principles

### 1. 性能为大

Performance is a design requirement from day one.

- Runtime execution must not rely on graph-wide scans per step.
- Trigger routing must be indexed by trigger type and graph entry.
- State reads and writes must be scoped and keyed.
- Conditions must be cheap, typed, and side-effect free.
- Timers must use a due queue or equivalent indexed scheduling, not full scans every tick.
- WebUI large graph interaction must avoid obvious O(n^2) layout or selection behavior.
- Any future expensive feature must state its budget before implementation.

### 2. 维护性为先

The architecture must stay layered and boring.

- Frontend, backend API, runtime, storage, validation, and loader adapters stay separate.
- Java must not generate HTML, CSS, or JavaScript strings.
- WebUI remains an independent frontend project.
- Core model must not depend on Fabric, Forge, or NeoForge classes.
- Avoid giant files, giant functions, patch-on-patch fixes, and speculative abstractions.
- A smaller v1 with clear seams is preferred over a broad v1 that hides coupling.

### 3. 稳定性至上

Save, validation, execution, and errors must fail closed.

- Unknown block types do not execute.
- Invalid graph drafts do not commit.
- Runtime has max steps, loop guards, and per tick budget.
- Errors produce readable trace entries.
- Timer continuations must be durable enough for the chosen v1 storage level.
- Bad user config should not crash global runtime.

### 4. 人性化看重

The UI exists for server owners, not engine authors.

- Anything designed must be useful first.
- Any configuration must be practical before it is powerful.
- Content must be simple, simple, and simpler again.
- Concepts that users do not need are not shown.
- Common actions should be quick.
- The canvas should make the flow explain itself.

## Hard Product Boundaries

### Direct Typed Edge Is The Flow Model

PixelLogic v1 uses Graph / Block / Slot / Typed Edge as the user-visible model.

- A Graph contains blocks and typed edges.
- A Block is the card or puzzle piece the user sees.
- A Slot is a visible input or output connection point.
- A Typed Edge directly connects one output slot to one input slot.

Typed Edge is not a skin over Channel. It is the core relationship.

### No Channel In Core

PixelLogic Core must not contain Channel as a domain model.

The following are not v1 core concepts:

- Channel
- SignalBridge
- SignalListener
- ActionRelay
- Receiver
- Relay
- channel name routing

If compatibility with old TZZ or external broadcasts is ever needed, it must live in a legacy adapter or external integration layer. It must not pollute core model, runtime execution, or normal WebUI.

### Condition Is A Standalone Block

Condition is not a hidden field on another block.

- Condition has a visible card.
- Condition has one input slot.
- Condition has two output slots: pass and fail.
- Condition is read-only.
- Condition does not write state.
- Condition does not execute actions.
- Condition does not emit events.

### Action Is Typed Form, Not Script

Action configuration is a controlled form.

Each v1 Action needs:

- action type
- typed schema
- required fields
- backend validation
- Chinese summary
- execution trace result

PixelLogic v1 does not include a script language.

## WebUI v1 Baseline

The confirmed v1 direction is a slot-based horizontal block flow.

The WebUI is:

- all Chinese in normal user UI
- canvas first
- large card / puzzle block oriented
- left to right
- slot-based rather than wire-based
- condition high block with pass and fail outputs
- left rail for graph list and block library
- right rail for selected block properties only
- bottom dock for validation and execution trace

The WebUI is not:

- a free node wire editor as the primary expression
- a traditional admin resource table
- a Scratch vertical script stack
- a channel picker
- a scripting IDE

### Slot-Based Flow Rules

- Ordinary blocks connect by left and right puzzle-like slots.
- Condition is a taller branch block.
- Condition owns pass and fail slots structurally.
- Pass and fail blocks align to those slots.
- Long cables are avoided.
- Short connection affordances are allowed when they help selection or hit testing.
- Action error output may exist but should be folded or visually weakened by default.

## v1 Required Scope

v1 proves one target:

```text
Users can build a simple logic flow that can be saved, validated, simulated, run, and debugged.
```

v1 includes:

1. Project / Graph basics.
2. Slot-Based horizontal block flow WebUI.
3. Command Trigger.
4. Manual/Test Trigger.
5. Condition Card.
6. State Compare Condition.
7. Message Action.
8. State Set Action.
9. State Add Action.
10. Timer Start Action.
11. Timer Completed continuation.
12. Debug Log Action.
13. State scopes: GLOBAL / PLAYER / SESSION.
14. Runtime execution trace.
15. Graph validation.
16. Basic save/load.
17. Simulate/Test Run.

## v1 Explicit Non-Scope

v1 does not include:

- Channel.
- SignalBridge.
- SignalListener.
- ActionRelay.
- Receiver / Relay.
- Old TZZ items / blocks / phone / AR / map / note / gallery / task / password / blocking.
- Region system.
- VBD / world device system.
- Forge implementation.
- NeoForge implementation.
- Multi-loader release.
- GameController.
- MissionSystem.
- PhaseController.
- Complex quest/story system.
- Script language.
- Complete rich text template/click/hover system.
- Bossbar lifecycle.
- Inventory matching.
- Teleport expansion.
- Particle expansion.
- Effect expansion.
- Sound expansion.
- Scoreboard/objective bridge.
- Complex item/block/entity state scopes.

## Region Is Deferred

Region is useful, but v1 should not do it.

Reason:

- Region adds spatial detection.
- Region needs player enter / leave / stay semantics.
- Region introduces multiplayer trigger edge cases.
- Region needs chunk safety and lifecycle rules.
- Region can create performance traps if implemented before runtime budgets are proven.

v1 should first prove the no-Channel Graph Runtime. Region can be designed for v1.1 after the execution model is stable.

## v1 User Story

A server owner opens PixelLogic, creates a lobby start flow, and builds:

```text
/pixellogic test start
-> whether PLAYER.started == false
   pass:
     -> send welcome message
     -> set PLAYER.started = true
     -> add PLAYER.start_count += 1
     -> start 30 second timer
     -> debug log "countdown finished"
   fail:
     -> debug log "player already started"
```

The UI shows cards and slots. The bottom trace explains what happened. Validation tells the user what is missing before they commit.

## Acceptance Checklist

v1 product direction is acceptable only if:

- No Channel appears in normal UI.
- No Channel appears in core model.
- Condition is visible and independent.
- The canvas can express pass and fail branches without long wire chasing.
- Actions are typed forms with validation.
- A saved graph can be validated before execution.
- Runtime trace can explain each step.
- The first spike can run without Region or old TZZ code.
