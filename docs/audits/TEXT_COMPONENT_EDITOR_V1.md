# PixelLogic Text Component Editor v1

## Scope

- Adds a shared editor for every `rich_text_component` field.
- Supports continuous multiline text editing through the existing block editor modal.
- Supports local formatting directly in the editable text surface: color, bold, italic, underline, strikethrough, and obfuscated.
- Uses Word-like toolbar buttons whose active state is shown with a green outline when the whole selection has that color or style.
- Clicking an active style button removes that style from the selection; clicking the active color returns that selection to the default color.
- Stores structured rich text payloads in the existing string-valued graph config.
- Keeps normal users away from raw JSON and internal component/segment management.

## Data Model

- Existing plain string messages still load as plain text.
- Existing `{ version, plainText, segments }` payloads still load.
- New payloads keep `version: 1`, a derived `plainText`, and `segments`.
- Each segment stores `text` plus a `style` object.
- Supported color values are Minecraft named colors: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, and white.
- Supported style flags are boolean values: bold, italic, underlined, strikethrough, and obfuscated.
- Frontend editing normalizes adjacent equal-style segments and drops empty saved segments.
- Backend validation rejects invalid color values, non-boolean style flags, empty saved segments, too many segments, and text over the configured length limit.
- Runtime and trace continue to use plain text extraction.
- Simulation message results now retain the structured payload alongside readable plain text.

## UX

- The user edits one continuous formatted text surface, not a list of components.
- Toolbar controls apply formatting only to the selected text.
- Color controls use Chinese names/tooltips and swatches while storing Minecraft color keys.
- The editor surface itself renders approximate color, bold, italic, underline, strikethrough, obfuscated text, and line breaks.
- There is no separate preview panel in the normal modal.
- The editor still uses modal-local draft state; graph JSON changes only after the user clicks `保存`.

## Message Blocks

- `action.message.chat` / 发送聊天消息: shared editor.
- `action.message.title` / 显示标题: shared editor.
- `action.message.subtitle` / 显示副标题: shared editor.
- `action.message.actionbar` / 显示快捷栏消息: shared editor.

## Validation

- `textComponentEditorSelfCheck` covers:
  - old plain text normalization;
  - structured formatted payloads;
  - color validation;
  - boolean style validation;
  - newline preservation;
  - empty segment rejection;
  - all four message blocks using the rich text field;
  - trace output staying plain text;
  - simulation message results retaining structured formatting payloads.

## Non-goals

- No hoverEvent or clickEvent editor.
- No selector, score, nbt, translate, keybind, or insertion editor.
- No full JSON editor.
- No raw JSON main path.
- No Minecraft adapter.
- No tellraw command generator UI.
- No new message blocks.
- No title timing settings.
- No WebUI static asset packaging.
- No Channel, Region, or old TZZ.
- No merge, tag, or release.
