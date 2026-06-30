# PixelLogic Catalog Expansion v1: Player + Message Blocks

## Scope

- 玩家是否拥有标签: existing `condition.player.has_tag` keeps its block id and updates the user-facing title.
- contextual condition mode labels: `拥有标签时继续`, `不拥有标签时继续`, `分开执行` for tag checks; `是管理员时继续`, `不是管理员时继续`, `分开执行` for admin checks.
- 玩家是否为管理员: new `condition.player.is_admin` under `条件判断 / 玩家条件`.
- 移除玩家标签: new `action.player.remove_tag` under `玩家操作 / 标签`.
- 显示标题: new `action.message.title` under `消息显示 / 屏幕提示`.
- 显示副标题: new `action.message.subtitle` under `消息显示 / 屏幕提示`.
- 显示快捷栏消息: new `action.message.actionbar` under `消息显示 / 屏幕提示`.
- no negative condition blocks: no separate `玩家没有标签` or `玩家不是管理员` block was added.

## Product Decision

- One condition block handles positive and negative flow through `outputMode`.
- No separate negative blocks.
- No extra expected boolean field on tag/admin conditions.
- Contextual labels make each condition's mode control read like the specific block.
- Title, subtitle, and actionbar are separate blocks.
- No combined title+subtitle block.

## Simulation

- actor tags: `condition.player.has_tag`, `action.player.add_tag`, and `action.player.remove_tag` operate on the per-run actor tags.
- administrator flag: `condition.player.is_admin` reads the per-run actor administrator flag.
- remove tag result: missing tags do not fail; the action records no state change beyond the current run result.
- message result channels: title/subtitle/actionbar simulation results are recorded as `TITLE`, `SUBTITLE`, and `ACTIONBAR`.
- no persistence: actor changes do not write graph JSON, named scenarios, or the WebUI test-player input.

## Text Component Note

- Message blocks use `rich_text_component`.
- Full color/format editor is still pending.
- No raw JSON main path.
- No `/say` behavior.

## Validation

- `catalogExpansionV1SelfCheck`: added.
- `.\gradlew.bat catalogExpansionV1SelfCheck`: pass during implementation.

## User-Accepted UI Display Addendum

- Card gray type labels display the top-level catalog category, not the concrete block name.
- The right-panel selected-block badge follows the same category-label rule.
- The block editor title displays `未命名(官方积木名)` when the node has not been renamed away from the official catalog name, otherwise `自定义名称(官方积木名)`.
- The base-info `积木类型` field is static metadata and displays `积木类别：官方积木名`; it is not an editable or readonly-input-style control.
