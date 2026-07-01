# WebUI Architecture

The WebUI is an independent Vite + TypeScript project.

## Current Skeleton

- Vanilla TypeScript.
- No React, Vue, or Svelte until the user confirms a framework.
- Slot-based horizontal flow surface for the v1 demo graph.
- API-backed status, test run, reset, latest trace, and recent traces.
- Graph API-backed draft save, draft validation, and commit for `demo-start-flow`.
- App shell communicates the product direction: cards, slots, direct execution trace, and readable user actions.

## Future Block Catalog

The left library is now driven by the Block Catalog skeleton. The current top-level entries (`触发事件 / 条件判断 / 消息显示 / 状态数据 / 时间调度 / 调试诊断`) are catalog data for the demo set, not permanent product categories.

Current behavior:

- categories come from `BlockCatalog.categories`;
- clicking a category opens concrete blocks for that category;
- clicking a concrete block creates a graph node with `blockId`, `nodeType`, default config, and slots from catalog data;
- API catalog failures show a minimal offline catalog placeholder while the normal Chinese API disconnected error explains that the full catalog is unavailable;
- technical block ids are not primary user-facing copy.

Still future:

- search, tags, recent blocks, and common recommendations;
- richer per-block summary templates shared with backend trace formatting;
- drag-from-library placement.

Users should drag concrete blocks such as `发送聊天消息`, `状态等于`, or `等待一段时间`, not a generic `动作` or `条件` block that hides many unrelated modes in one form.

## API Test Run

The current WebUI calls:

```text
GET  /api/pixellogic/status
POST /api/pixellogic/test/reset
POST /api/pixellogic/test/start
GET  /api/pixellogic/traces/latest
GET  /api/pixellogic/traces
```

The Vite dev server proxies `/api` to `http://127.0.0.1:18111`. The UI defaults to `WebUI 模拟玩家`. The `测试运行` control is a split button: the left side starts the run, and the right arrow opens a small dropdown with brief helper copy and `编辑测试上下文`.

Long-term, `/api` should be treated as a WebUI transport boundary rather than a permanent direct server HTTP assumption. The current transport is dev/local HTTP. A future transport candidate is client localhost bridge -> Minecraft networking -> server core. This is not the active near-term implementation track; the user-facing WebUI API shape should stay stable where possible: catalog, graph, save/validate/commit, simulation, and trace.

`POST /api/pixellogic/test/start` may include:

```json
{
  "testContext": {
    "actor": {
      "id": "webui-sim-player",
      "displayName": "WebUI 模拟玩家",
      "tags": ["runner"],
      "operator": false
    },
    "world": {
      "playerPosition": { "dimensionId": "minecraft:overworld", "x": 0, "y": 64, "z": 0 },
      "targetBlock": { "enabled": false, "dimensionId": "minecraft:overworld", "x": 0, "y": 64, "z": 0, "blockId": "minecraft:stone" },
      "regions": []
    }
  }
}
```

This context is temporary input for the next run only. It is not written to graph JSON, not saved as a named scenario, and not reused from the previous run result. The result summary displays the run actor, player position, target block, region count/name summary, initial tags, final tags, tag changes, and administrator status.

## Graph Draft Flow

The current WebUI also calls:

```text
GET  /api/pixellogic/graphs/demo-start-flow
GET  /api/pixellogic/graphs/demo-start-flow/draft
PUT  /api/pixellogic/graphs/demo-start-flow/draft
POST /api/pixellogic/graphs/demo-start-flow/validate
POST /api/pixellogic/graphs/demo-start-flow/commit
```

The UI renders the graph as the confirmed Slot-Based horizontal block flow. Single click selects a block, and double-clicking opens a focused editor modal for selected-block fields such as display name, message text, state key/value, and timer seconds.

Important user semantics:

- The backend still keeps draft, validate, and commit separated.
- Structural graph edits such as add, delete, drag, insert, and disconnect still enter an automatic save, validate, and commit queue.
- Block configuration edits inside the editor modal use a local draft and are applied to the graph only when the user clicks `保存`.
- Closing a modified editor modal asks whether to continue editing or discard the local draft.
- Automatic save promotes edits only when validation passes, and stale save responses do not overwrite newer local edits.
- Invalid edits do not replace the committed runtime graph.
- `上一步` / `下一步` plus `Ctrl+Z`, `Ctrl+Y`, and `Ctrl+Shift+Z` roll back and reapply graph operations such as naming, configuration, add/delete, drag, and connection edits.
- `测试运行` waits for pending automatic save, resets the demo test state, starts the run, and refreshes the trace.
- `编辑测试上下文` opens a modal using the same local-draft, save, close animation, and unsaved-close confirmation pattern as block editing.
- The test-context modal sends display name, tags, administrator status, player position, optional target block, and simple region facts with the test run; tag chips use a left-side `×` remove button, and final tags from `action.player.add_tag` stay in the result summary and do not rewrite the input tags.
- Reset remains an internal API step, not a primary user button.
- The right panel is an information surface, not the main field editor.
- Closing the editor modal without saving discards only the modal-local draft and does not mutate the graph.
- Modal open/close uses short CSS animation and respects reduced motion.
- The editor modal uses humanized Chinese form labels and controls. Internal graph values such as `PLAYER`, `BOOLEAN`, and `true` remain storage/runtime values, but normal UI renders them as labels such as `玩家`, `是或否`, and `是`.
- The editor modal now prefers Block Catalog `formSchema` for known `blockId` values. Nodes without `blockId` only infer catalog schema when their `node.type` maps to one catalog block; legacy `NodeType` form builders remain only as fallback for old or unknown nodes.
- `action.message.chat` uses a shared `rich_text_component` editor: multiline continuous formatted text editing, selected-text color/style controls, Word-like active button outlines, and structured storage. Preset colors store Minecraft JSON named colors, while custom colors store `#RRGGBB` hex values and show recent local choices. The normal UI does not expose raw JSON or component/segment management.
- `action.message.title`, `action.message.subtitle`, and `action.message.actionbar` use the same shared rich text editor and remain separate blocks; no combined title+subtitle block or timing controls are included yet.
- Rich text typing updates only the modal-local draft; one click on `保存` creates one graph edit/history entry.
- Short configuration fields use compact two-column layout where space allows; long text fields remain full-width.
- Condition blocks expose `条件用途` in the same catalog form path:
  - `满足时继续` renders a normal-height single green output condition card.
  - `不满足时继续` renders a normal-height single red output condition card.
  - `分成两路` keeps the current dual-branch condition shape.
- Player condition blocks override those labels through catalog schema, such as `拥有标签时继续` / `不拥有标签时继续` / `分开执行` and `是管理员时继续` / `不是管理员时继续` / `分开执行`.
- Context condition blocks also override those labels through catalog schema:
  - `玩家所在维度是否为`: `在该维度时继续` / `不在该维度时继续` / `分开执行`.
  - `玩家是否在区域内`: `在区域内时继续` / `不在区域内时继续` / `分开执行`.
  - `目标方块是否为`: `为该方块时继续` / `不为该方块时继续` / `分开执行`.
  - `目标方块是否在区域内`: `在区域内时继续` / `不在区域内时继续` / `分开执行`.
- These context blocks edit only dimension id, block id, region name, and condition usage. Coordinates and target block facts stay in `编辑测试上下文`.
- Switching condition usage removes inactive branch connections only after the user confirms `切换并断开`, and the config change plus edge removal share one undo history entry.
- Card gray type labels and the right-panel selected-block badge show the catalog top-level category, such as `条件判断`, `玩家操作`, or `消息显示`, instead of repeating the concrete block name.
- Block card titles stay on one line. If the rendered title actually overflows, it scrolls horizontally back and forth instead of wrapping or using a fixed ellipsis.
- Block card summaries reserve about three lines. If the rendered summary actually overflows, it scrolls vertically back and forth; short summaries and summaries that fit in three lines do not animate.
- The block editor title uses `未命名(官方积木名)` when the current display name still equals the catalog name, and `自定义名称(官方积木名)` after the user renames it.
- The editor base-info block type is static metadata in the format `积木类别：官方积木名`; it is not styled as an editable or readonly input.
- Region-name fields for region condition blocks read current test-context `world.regions[].name` values into a project-styled dropdown when regions exist, preserve an old value that is not in the list, and fall back to text input when no regions exist.
- Editor select/scope controls use the project custom dropdown style instead of relying on the browser's native blue select popup.

Safety bounds:

- Automatic save is debounced and keeps at most one in-flight save path, with a latest-rerun flag when edits happen during the request.
- Save responses are applied only if both the local graph version and save sequence still match.
- Undo and redo history are both capped at 80 graph snapshots.
- Trace rendering caps visible steps at 100 even though the backend already bounds each trace.
- The app uses assigned `document.onkeydown` and `window.onbeforeunload` handlers rather than stacking duplicate global listeners.

## Slot Flow Drag / Insert

The slot-based canvas now supports a minimal direct-manipulation graph editing loop:

- Concrete block entries from the catalog add a new block to the visible canvas and select it.
- Single click selects the block; pointer movement past the drag threshold starts drag; double click opens the existing editor modal.
- Dragging a block moves that block and all downstream nodes reachable from outgoing typed edges.
- Dragging a dual-branch Condition moves both pass and fail downstream branches; single-output conditions behave like normal chain blocks.
- Single-output conditions can be appended, attached, or inserted as a normal chain tail even when the active slot id is `pass` or `fail`; dual-branch conditions still are not treated as a single tail.
- Inactive condition outputs are hidden and ignored by visual connection, chain dragging, append, attach, and insert candidate detection.
- Dragging follows only edges whose puzzle mouths are still visually snapped together; visually separated stale edges are ignored.
- Magnetic snap has a wider hit area, but connected-state detection uses a tight snapped-position tolerance so near-misses are not treated as one chain.
- During an active drag, candidate detection uses preview positions so a just-separated old port can be reconnected before pointer release.
- Reconnecting to the just-separated original port uses a smaller snap radius than connecting to a new target, so slight separation does not immediately pull the block back.
- Condition branch lanes reserve recursive downstream visual span above and below each input anchor so nested Conditions do not overlap, while the Condition card outline only draws the input head and normal-height branch caps around pass/fail mouths instead of drawing the full reserved lane.
- Block hit testing follows the painted puzzle outline and visible text, not the oversized layout rectangle used for dynamic branch lanes.
- Connected downstream nodes are vertically realigned to those dynamic ports; this keeps the puzzle mouths snapped while preventing nested branch overlap.
- Releasing on blank canvas stores new position metadata and drops edges that are no longer visually snapped.
- Automatic save and test-run also sync graph edges to the current visual snapped state before persisting.
- Releasing near a valid join magnetically snaps the dragged chain into place, rewrites the edge as an inserted chain, and keeps validation fail-closed through the existing save flow.
- A Condition block with no connected input can still be saved like other placed blocks; it is simply unreachable until connected to a trigger path.
- While hovering near a valid middle join, the two connected components produced by temporarily cutting that join animate apart, leaving a green glow-only insertion gap.
- Releasing near a chain-end output magnetically appends the dragged chain to that output.
- Releasing a dragged chain tail near a free input magnetically connects the dragged chain before that block or chain.
- Insert success shifts the target block and local downstream chain rightward to keep puzzle blocks from overlapping.
- The right panel remains informational, but now also shows selected-block connections plus minimal `断开输入` and `删除积木` controls.
- Trace text is humanized in the WebUI display layer only; runtime trace payloads remain unchanged.

## Frontend Structure

The WebUI remains Vanilla TypeScript. `main.ts` is a bootstrap entry that imports `styles/index.css` and starts `ui/app.ts`.

Current responsibility boundaries:

- `api/`: localhost PixelLogic API client and connection/content-type errors.
- `model/`: graph/API types, seeded demo graph, pure graph layout, connection, and cloning helpers.
- `model/blockCatalog.ts`: catalog sorting/lookup helpers, catalog-block-to-graph-node conversion, and a minimal API-offline fallback placeholder.
- `model/richText.ts`: rich text component helpers for structured storage, named/hex color normalization, selected-range formatting, and plain text display.
- `ui/editor/richText/`: shared rich text editor toolbar, contenteditable rendering, and selection-offset helpers.
- `model/simulationTestContext.ts`: per-run WebUI test context model, validation, tag normalization, simple world facts, and request payload.
- `state/`: mutable app state and canvas world dimensions.
- `ui/app.ts`: orchestration, app shell assembly, event binding, autosave, undo/redo, and API actions.
- `ui/canvas/`: puzzle block view, slot-flow view-model building, block constants, and drag/insert graph rules.
- `ui/catalog/`: catalog library rendering for category and block lists.
- `ui/editor/`: editor modal shell and humanized form controls.
- `ui/sidebar/`: selected-block summary and connection actions.
- `ui/simulation/`: test-run split dropdown, test-player modal, and simulation result summary.
- `ui/trace/`: trace rendering.
- `ui/humanize/`: labels and trace message humanization.
- `ui/validation/`: validation/draft status copy.
- `styles/`: split CSS modules imported by `styles/index.css`.

## Boundary

Java may serve built static assets later, but Java must not generate WebUI source strings.

The normal user flow must expose visual graph relationships instead of channel names.

The normal block library must expose concrete, human-readable blocks from the catalog. Category names are navigation, not executable nodes.

Future client-hosted WebUI rules:

- The client-hosted WebUI only starts after a server-authorized admin session.
- The local bridge must bind to `127.0.0.1`, use a random port and one-time token, and reject wildcard CORS.
- The WebUI must not rely on client-side authorization. The server checks every request.
- Server HTTP remains the current dev/local/self-check transport; client bridge implementation is deferred to a future scoped prompt.
