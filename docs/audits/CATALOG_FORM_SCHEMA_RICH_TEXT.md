# PixelLogic Catalog Form Schema + Rich Text Field

## Scope
- schema renderer: WebUI editor modal now resolves `node.blockId` into Block Catalog `formSchema` first.
- migrated demo blocks: the current seven demo blocks expose schema-driven fields.
- rich_text_component: `action.message.chat.message` is a structured rich text component field with multiline editing and preview.
- legacy fallback: old `NodeType` field builders remain only as a clearly named fallback when no catalog block is found.
- summary: catalog `summaryTemplate` drives card, modal, and sidebar summaries when available.
- validation: backend still owns final validation; frontend maps required/min/max/step/options into native controls for basic UX.
- API dev proxy: Vite `/api` proxy now returns a short JSON 503 when the Java API is offline, so the WebUI shows the normal Chinese disconnected state instead of hanging or parsing HTML.
- manual-save follow-up: schema-driven modal fields now edit a local draft first. `保存` applies the draft once; typing in rich text no longer mutates the graph or triggers autosave per keypress.

## Rich Text Semantics
- tellraw/text component: message output is modeled as vanilla text component semantics.
- not /say: no `/say` execution or user-facing message block naming was added.
- plain text compatibility: legacy string `message` values still load, validate, summarize, and execute as plain text.
- future style runs: stored value includes `version`, `plainText`, and `segments` extension space.
- future advanced components: color, formatting, hover/click, selector, score, translate, keybind, and nbt remain future editor work.

## Validation
- gradlew build: pass.
- npm build: pass.
- self-check: `apiWebUiSelfCheck`, `graphStorageSelfCheck`, `manualSimulationSelfCheck`, and `blockCatalogSelfCheck` pass.
- browser self-check: see `reports/catalog-form-schema-rich-text/REPORT.md`.
- screenshots: see `reports/catalog-form-schema-rich-text/screenshots/`.
- API offline: `/api` returns `application/json` with `API 未连接，请确认 PixelLogic API server 已启动。`.
- manual-save follow-up: see `docs/audits/CATALOG_FORM_MANUAL_SAVE_FIX.md`.

## Known Limitations
- no full toolbar.
- no hover/click.
- no real MC adapter.
- rich text is stored inside the existing string-valued graph config as a JSON text component payload until graph config typing is expanded.
