# PixelLogic Condition Output Modes

## Problem

- All condition blocks used the dual-branch visual form.
- Some validation paths expected both `pass` and `fail` outputs to be connected.
- Simple filters such as "player has tag" were too visually heavy and were categorized as player actions.

## Solution

- Condition blocks now support `outputMode`:
  - `PASS_ONLY`: 满足时继续；不满足时该路径结束。
  - `FAIL_ONLY`: 不满足时继续；满足时该路径结束。
  - `BRANCH`: 分成两路，满足和不满足分别连接后续流程。
- Old graphs with no `outputMode` still behave as `BRANCH`.
- New catalog condition nodes default to `PASS_ONLY`.
- The demo graph explicitly keeps `BRANCH`.
- Unconnected selected condition output means graceful end and trace records the end.
- Switching to a mode that makes an existing branch inactive asks for confirmation before removing the affected edge.

## Catalog Changes

- `condition.player.has_tag` moved to `条件判断 / 玩家条件`.
- `action.player.add_tag` remains under `玩家操作 / 标签`.
- Condition form schemas expose `条件用途` as Chinese segmented buttons.

## Validation

- `conditionOutputModeSelfCheck` covers old graph compatibility, new catalog defaults, demo graph branch mode, graceful path ending, single-side branch validation, invalid mode rejection, and player tag catalog placement.
- `git diff --check`: pass.
- `.\gradlew.bat build`: pass.
- `cd web-ui; npm install`: pass, 0 vulnerabilities.
- `cd web-ui; npm run build`: pass.
- `.\gradlew.bat apiWebUiSelfCheck graphStorageSelfCheck manualSimulationSelfCheck blockCatalogSelfCheck simulationBackendSelfCheck conditionOutputModeSelfCheck`: pass.
- forbidden Channel/Relay grep in `src/main/java web-ui/src`: pass, no matches.
- old WebUI save label grep: pass, no matches.
- raw enum HTML text grep: pass, no matches.
- `/say` / raw JSON source grep: source pass; matches are docs-only boundary notes.
- command root grep: pass; only the existing `literal("test")` subcommand matched.
- `core/simulation` Minecraft dependency grep: pass, no matches.

## Boundaries

- No Minecraft adapter.
- No Region.
- No Channel or signal/relay model.
- No old TZZ migration.
- No WebUI static packaging.
- No merge, tag, or release.
