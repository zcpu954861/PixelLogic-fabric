# PixelLogic Admin Client Bridge Design Audit

## Verdict

- Recommended: yes, as the long-term admin architecture direction.
- Risks: local bridge security, malicious client behavior, stale graph mutation, session lifecycle leaks, and future payload size/chunking.
- P0: none in the design if the server remains authoritative and the local bridge is session-bound.
- P1: do not implement a client-self-authorized bridge, wildcard CORS, `0.0.0.0` binding, or tool items that execute server behavior without per-request capability checks.
- P2: server HTTP API remains useful for development/local self-checks and should remain in place for the foreseeable near-term work.
- P3: permission-node naming and exact session timeout are still open.

## Fit with PixelLogic Product

- Minecraft mod positioning: strong fit. Admin work happens from an in-game authorized client rather than a public WebAdmin surface.
- admin-only workflow: strong fit. The client mod can be installed by anyone, but management features stay locked until the server grants a session.
- game interaction / selection tools: strong fit. Tool items, selectors, and overlays become natural client-side affordances while server authority remains intact.
- WebUI security: better long-term fit than a default remote server HTTP admin port, provided the localhost bridge follows strict token/origin/session rules.

## Architecture Review

- server authority: required. Graph, catalog, validation, runtime, storage, trace, session, capability, and real Minecraft adapter decisions remain server-side.
- client bridge: acceptable as transport only. It should forward requests and host UI, not validate final permission or mutate graph locally.
- local WebUI: acceptable when bound to `127.0.0.1`, random port, short-lived token, and session expiry.
- custom payload protocol: appropriate future transport. It must be versioned, bounded, rate-limited, and revocable.
- tool item capability gate: required. Real items can exist later, but useful behavior must be gated by active server-granted capabilities.

## Security Review

- unauthorized client: safe only if default state exposes no bridge/UI/tools and server rejects admin payloads.
- malicious client: expected. The server must check every request and never trust client-side UI state.
- localhost attack: reduced by random port, token, Origin/Host checks, no wildcard CORS, no state-changing GET, and short expiry.
- token leak: mitigated by one-time token, no logs, no long-term config, and session binding.
- replay: mitigated by session id, sequence, createdAt, expiry, and server-side duplicate/stale rejection.
- stale requests: graph mutations must include graph version/fingerprint and save sequence.
- permission revocation: server revocation must immediately invalidate sessions and cause the local bridge to close or reject requests.

## Compatibility Review

- existing server HTTP API: keep it for dev/local mode and self-checks. Do not remove it in the bridge design phase.
- current WebUI API: mostly reusable. The current catalog/graph/save/validate/commit/simulation/trace shape can map onto bridge payload types.
- current simulation runtime: reusable as server-side operations behind bridge requests.
- future MC adapter: compatible. The bridge can authorize UI/tool requests; real Minecraft side effects still belong in loader/server adapters.
- future tool items: compatible if server-side item use checks active session and capability.

## Roadmap Placement

- long-term track: Admin Client Bridge, Authorized Tool Session, client-hosted WebUI, and capability-gated tool items.
- near-term track: Simulation Backend expansion, catalog/graph/runtime capabilities, WebUI editor work, new block simulation semantics, and trace/result/debug.
- future candidates: handshake/status, read-only bridge, graph edit bridge, simulation bridge, and tool item selection are reference candidates only, not the active roadmap.
- deferred implementation prompts: each candidate needs a new prompt after its prerequisites are mature.

## Open Questions

- permission integration: exact permission nodes and whether to support LuckPerms-style providers later.
- session timeout: recommended first value is 30 minutes idle, but product may choose a different default.
- local port lifecycle: whether each session gets a new port or the client reuses a bridge process with per-session tokens.
- large payload chunking: exact limits and whether graph payloads need chunking before multi-graph editing.
- tool item UX: silent no-op vs low-frequency denial message for unauthorized players.
- player-facing denial behavior: how much detail to show without leaking admin policy.

## Final Recommendation

Adopt Admin Client Bridge & Authorized Tool Session as the long-term direction, while keeping near-term development focused on Simulation Backend, catalog, graph, runtime, and WebUI editor work. Future implementation should happen only after a new scoped prompt; when it eventually starts, the first slice should be handshake/status only, not local bridge, graph mutation, custom payload multiplexing, or tool items all at once. The hard non-negotiables are server authority, client default no functionality, localhost-only bridge, no wildcard CORS, one-time token, expiry/revocation, and per-request capability checks.
