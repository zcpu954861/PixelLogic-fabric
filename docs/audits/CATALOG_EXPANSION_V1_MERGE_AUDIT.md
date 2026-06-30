# PixelLogic Catalog Expansion v1 Merge Audit

## Verdict

- Ready to merge into `mc-1.21.11`: yes.
- P0: none.
- P1: none.
- P2: none.
- P3: `web-ui/src/ui/app.ts` remains the largest frontend file from earlier WebUI work; this task reused `ui/humanize` and did not expand orchestration materially.

## Scope Check

- 玩家是否拥有标签: pass; `condition.player.has_tag` keeps its stable block id and uses the updated user-facing name.
- 玩家是否为管理员: pass; `condition.player.is_admin` is under `条件判断 / 玩家条件`.
- 移除玩家标签: pass; `action.player.remove_tag` is under `玩家操作 / 标签`.
- 显示标题: pass; `action.message.title` is under `消息显示 / 屏幕提示`.
- 显示副标题: pass; `action.message.subtitle` is under `消息显示 / 屏幕提示`.
- 显示快捷栏消息: pass; `action.message.actionbar` is under `消息显示 / 屏幕提示`.
- no negative condition blocks: pass; no `玩家没有标签` or `玩家不是管理员` block exists.
- no title+subtitle combo block: pass.

## Product Semantics

- contextual condition mode labels: pass; tag/admin conditions use block-specific labels.
- no extra expected boolean fields: pass; tag condition has `outputMode` + `tag`; admin condition has only `outputMode`.
- add/remove tag per-run: pass; simulation mutates only the supplied run actor.
- message result channels: pass; title/subtitle/actionbar produce `TITLE`, `SUBTITLE`, and `ACTIONBAR`.
- rich_text_component: pass; all message blocks use the rich text field.
- no raw JSON main path: pass; source paths do not expose raw JSON editing.
- no `/say`: pass; source paths do not use `/say` behavior.

## UI Display Rules

- card gray type label: pass; known catalog nodes display the top-level category such as `条件判断`, `玩家操作`, or `消息显示`.
- sidebar selected block type badge: pass; uses the same category label.
- edit modal title: pass; uses `未命名(官方积木名)` when the display name equals the official name, otherwise `自定义名称(官方积木名)`.
- base info block type static display: pass; uses static text with `积木类别：官方积木名`, not the readonly input visual.
- official block name vs custom name: pass; the modal title and base info separate custom display name from catalog display name.
- no specific block name repeated as type label: pass for catalog-backed cards and selected-block badge.

## Runtime / Simulation

- actor tags: pass; `condition.player.has_tag`, add tag, and remove tag operate on the run actor.
- administrator flag: pass; `condition.player.is_admin` reads the run actor operator/admin flag.
- remove tag: pass; missing tag is non-fatal and records no hard failure.
- title: pass; title message result channel is `TITLE`.
- subtitle: pass; subtitle message result channel is `SUBTITLE`.
- actionbar: pass; actionbar message result channel is `ACTIONBAR`.
- no persistence: pass; simulation actor changes do not write graph JSON or named scenarios.
- no graph writes: pass; run result tags do not rewrite the WebUI test-player input.

## Validation

- feature `git diff --check`: pass.
- feature `.\gradlew.bat build`: pass.
- feature `npm install`: pass; 0 vulnerabilities reported by npm.
- feature `npm run build`: pass.
- `apiWebUiSelfCheck`: pass.
- `graphStorageSelfCheck`: pass.
- `manualSimulationSelfCheck`: pass.
- `blockCatalogSelfCheck`: pass.
- `simulationBackendSelfCheck`: pass.
- `conditionOutputModeSelfCheck`: pass.
- `simulationTestContextSelfCheck`: pass.
- `catalogExpansionV1SelfCheck`: pass.
- grep negative block names: pass; matches are docs-only boundary notes.
- grep old 玩家拥有标签: pass; no source hits.
- grep UI internal terms: pass; no WebUI source hits for the checked raw enum/value patterns.
- grep nodeTypeLabel/发送消息: pass with explanation; remaining hits are fallback helper code, not the card/sidebar catalog type label path.
- grep checkbox: pass; no hits.
- grep auto-check pass bubble: pass; no hits.
- grep raw JSON / say: pass with explanation; matches are docs-only boundary notes and search-keyword examples.
- grep Channel: pass; no source hits.
- grep core/simulation MC deps: pass; no `net.minecraft` in `core/simulation`.
- largest files: `web-ui/src/ui/app.ts` remains 66,377 bytes; largest Java file is `BuiltInBlockCatalog.java` at 27,454 bytes.

## Boundaries

- no full color/format editor: pass.
- no title timing settings: pass.
- no title+subtitle combo block: pass.
- no named scenario: pass.
- no persistence: pass.
- no graph writes outside existing graph edit flow: pass.
- no multiplayer simulation: pass.
- no inventory/world/container simulation expansion: pass.
- no MC adapter: pass.
- no WebUI static packaging: pass.
- no Channel: pass.
- no Region: pass.
- no old TZZ: pass.
- no tag: pass.
- no release: pass.

## Known Limitations

- color/format editor for `rich_text_component` is still pending.
- title/subtitle/actionbar timing settings are intentionally not implemented.
- Browser screenshot testing was skipped for this merge-readiness pass; user hand-tested the accepted UI state.

## Final Recommendation

Merge `feature/v1-catalog-expansion-player-message` into `mc-1.21.11` with a normal merge commit, then repeat build, npm build, self-checks, and grep on the merge result before pushing mainline.
