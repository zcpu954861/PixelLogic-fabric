# PixelLogic WebUI Interaction Model

## App Shell

- Top bar: product name, current project, save state, test run, settings.
- Left rail: graph list, node library, first-use actions.
- Center: graph canvas with cards and direct edges.
- Right rail: selected card details.
- Bottom dock: validation errors and execution trace.

## Graph Editing Mental Model

Users should understand flow by reading:

1. The visible card type.
2. The card name and summary.
3. The input/output ports.
4. The direct edge label.
5. The validation and trace feedback.

## Card Rules

- Trigger cards start flows.
- Condition cards are standalone and must show pass/fail outputs.
- Action cards use typed fields and must show done/error outputs.
- Timer cards are explicit waits, not hidden delays.
- Debug Log cards are visible diagnostic endpoints.

## Property Panel Rule

The right panel edits only the selected card details. It must not hide flow-critical logic. Any branch that changes execution must remain visible on the graph canvas.

## First-Use Flow

The first-use state should offer:

- Create the first Graph.
- Start from a template.
- Create a Trigger manually.
- View an example.

These are static in this prototype.
