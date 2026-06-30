# PixelLogic Block Catalog Skeleton Audit

## Scope

- Java registry: added `core/catalog` built-in catalog records and registry.
- Catalog API: added readonly `GET /api/pixellogic/catalog`.
- Demo block migration: current demo nodes now write concrete `blockId`.
- WebUI library: left library is category -> concrete block, backed by catalog data.
- Compatibility: `node.type` remains; old graph JSON without `blockId` infers it from legacy `node.type`.

## Implemented Blocks

| blockId | 中文名 | category | capability |
| --- | --- | --- | --- |
| `trigger.manual_test` | WebUI 测试运行 | 触发事件 | `FULLY_SIMULATABLE` |
| `condition.state.equals` | 判断状态是否等于 | 条件判断 | `FULLY_SIMULATABLE` |
| `action.message.chat` | 发送聊天消息 | 消息显示 | `APPROXIMATE_SIMULATION` |
| `state.set` | 设置状态 | 状态数据 | `FULLY_SIMULATABLE` |
| `state.add` | 累加状态 | 状态数据 | `FULLY_SIMULATABLE` |
| `timer.wait` | 等待一段时间 | 时间调度 | `FULLY_SIMULATABLE` |
| `debug.log` | 调试记录 | 调试诊断 | `FULLY_SIMULATABLE` |

## Compatibility

- Old `node.type`: still present and still used by the v1 runtime dispatch path.
- New `blockId`: written by seeded graph, normalized graph storage, and new WebUI catalog nodes.
- Legacy documents: `GraphDocument` resolves missing `blockId` from `node.type`.
- Validator: unknown `blockId` and `blockId`/`node.type` mismatch fail with Chinese validation issues.
- Self-check: `blockCatalogSelfCheck` verifies registration, document write, legacy inference, and validator failures.

## Validation

- `git diff --check`: pass; CRLF warnings only.
- `gradlew build`: pass.
- `npm install`: pass, 0 vulnerabilities.
- `npm run build`: pass.
- `apiWebUiSelfCheck`: pass.
- `graphStorageSelfCheck`: pass.
- `manualSimulationSelfCheck`: pass.
- `blockCatalogSelfCheck`: pass.
- Channel grep: no matches in `src/main/java` or `web-ui/src`.
- Old UI label grep: no matches for draft/validate/commit/reset technical buttons in `web-ui/src`.
- Enum UI grep: no raw `PLAYER/GLOBAL/SESSION/BOOLEAN/INTEGER/STRING/true/false` option labels in `web-ui/src`.
- Generic block grep: matches only docs that forbid generic/万能 blocks.
- Command root grep: only existing `/pixellogic test` subcommand matched `literal("test")`; no old root command was added.
- Largest frontend files: `web-ui/src/ui/app.ts` remains the largest file at 49602 bytes; new `web-ui/src/model/blockCatalog.ts` is 8710 bytes.

## Boundaries

- No MC adapter.
- No mass new blocks.
- No WebUI static packaging into jar.
- No Channel.
- No Region.
- No old TZZ.
- No merge, tag, or release in this branch.

## Known Limitations

- Runtime execution still dispatches on `NodeType`; catalog executor dispatch is future work.
- Form rendering still primarily uses existing WebUI `NodeType` field builders; catalog form schema is present but not yet the single source of truth.
- Library placement is click-to-add; drag-from-library can wait until the concrete catalog UX is accepted.
