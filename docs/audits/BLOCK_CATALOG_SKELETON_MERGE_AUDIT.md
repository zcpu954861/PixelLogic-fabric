# PixelLogic Block Catalog Skeleton Merge Audit

## Verdict
- Ready to merge into mc-1.21.11: yes
- P0: none
- P1: none
- P2: catalog form schema is not yet the editor's single source of truth; runtime still dispatches by `NodeType`.
- P3: browser self-check was not repeated in this audit stage by user workflow.

## Source
- branch: `feature/v1-block-catalog-skeleton`
- commit: `ba6be0ebceed8ffe257889148852bf809675dcd5`
- user review: completed; user reported manual testing had no blocking issue.
- browser self-check:
  - not repeated in this audit stage by user workflow
  - user manual test completed

## Scope Check
- Java catalog registry: pass; `core/catalog` contains the built-in registry records and lookup helpers.
- block definitions: pass; only the current seven demo concrete blocks are registered.
- demo blockId mapping: pass; existing `NodeType` demo nodes map to concrete `blockId` values.
- graph blockId compatibility: pass; `node.type` remains and missing `blockId` is inferred from legacy type.
- catalog API: pass; readonly `GET /api/pixellogic/catalog` returns catalog JSON.
- WebUI catalog library: pass; left library is category navigation into concrete blocks.
- self-check: pass; `blockCatalogSelfCheck` covers registry, compatibility, and validation failures.
- docs: pass; specs and audit docs describe boundaries and known limitations.

## Initial Block IDs
- `trigger.manual_test`: WebUI test run entry, `MANUAL_TRIGGER`.
- `condition.state.equals`: state comparison condition, `STATE_COMPARE_CONDITION`.
- `action.message.chat`: chat/message action, `MESSAGE_ACTION`.
- `state.set`: state set action, `STATE_SET_ACTION`.
- `state.add`: state add action, `STATE_ADD_ACTION`.
- `timer.wait`: wait/timer action, `TIMER_START_ACTION`.
- `debug.log`: debug trace action, `DEBUG_LOG_ACTION`.

## UX Checks
- categories are extensible: pass; categories and subcategories come from catalog data.
- current six are not fixed: pass; they are current demo categories, not permanent node kinds.
- category is not a block: pass; category buttons only navigate.
- concrete blocks only: pass; add flow uses `data-catalog-block` concrete ids.
- no generic big blocks: pass; generic/万能 mentions are docs-only guardrails.
- double-click editor preserved: pass by scope audit; editor flow was not rewritten.
- drag insert preserved: pass by scope audit; drag insert modules were not rewritten.
- auto save preserved: pass by scope audit; save/apply flow was not rewritten.
- undo/redo preserved: pass by scope audit; history flow was not rewritten.

## Compatibility
- old graph without blockId: pass; `GraphDocument` resolves from `node.type`.
- new graph with blockId: pass; seeded and WebUI-created nodes write concrete `blockId`.
- runtime NodeType dispatch: preserved.
- known limitation: executor dispatch is still `NodeType` based; catalog executor separation is future work.

## Validation
- git diff --check: pass.
- gradlew build: pass.
- npm install: pass; 0 vulnerabilities.
- npm run build: pass.
- apiWebUiSelfCheck: pass.
- graphStorageSelfCheck: pass.
- manualSimulationSelfCheck: pass.
- blockCatalogSelfCheck: pass.
- grep checks: pass; Channel/old UI labels/raw enum labels had no code matches, generic block matches were docs-only, command root match was the existing `/pixellogic test`.
- browser self-check:
  - not repeated in audit stage

## Boundaries
- no MC adapter: pass.
- no mass new blocks: pass.
- no WebUI static packaging: pass.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Final Recommendation

Merge `origin/feature/v1-block-catalog-skeleton` into `mc-1.21.11` with a no-ff merge after confirming the feature branch stays clean. After merge, repeat build, WebUI build, self-checks, grep checks, and push `mc-1.21.11` only if those pass.
