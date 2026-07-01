# PixelLogic Admin Client Bridge Docs Merge Audit

## Verdict
- Ready to merge docs into mc-1.21.11: yes
- P0: none
- P1: none
- P2: exact future permission provider and protocol wire version remain open.
- P3: none

## Source
- branch: `docs/admin-client-bridge-authorized-session`
- commits: `6ceb43f docs: design admin client bridge architecture`
- user review: user reviewed the direction and requested scope wording cleanup
- code changed: no

## Scope
- docs-only: pass
- long-term architecture: pass
- no implementation: pass
- no networking: pass
- no item registration: pass
- no server HTTP removal: pass

## Design Kept
- server authority: graph, catalog, validation, runtime, storage, trace, permission, session, and capability remain server-side
- client default no functionality: installing the client mod alone grants no WebUI, bridge, tool, overlay, or graph access
- authorized admin session: server grants bounded admin sessions after permission checks
- capability model: retained for WebUI, graph, simulation, trace, runtime, tools, and selection
- client-hosted WebUI: retained as long-term direction
- local bridge security: retained 127.0.0.1, random port, token, Origin/CORS, expiry, and revoke rules
- tool item gating: real tools may exist later but useful behavior requires capability
- per-request validation: retained player UUID, sessionId, capability, graphVersion, sequence, size, and rate checks

## Scope Adjustments
- future phases are not active roadmap: pass; phases are now future implementation candidates / long-term reference
- near-term remains Simulation Backend: pass; docs explicitly keep near-term focus on Simulation Backend, catalog, graph, runtime, WebUI editor, block simulation semantics, trace/result/debug
- protocol draft is not stable wire format: pass
- server HTTP remains dev/local transport: pass

## Security Review
- localhost: pass; bridge must bind to `127.0.0.1`
- random port: pass
- token: pass; one-time, session-bound token
- Origin/CORS: pass; no wildcard CORS
- per-request capability: pass
- malicious client: pass; server does not trust client state
- tool items: pass; unauthorized tools have no effective server behavior
- revoke/expiry: pass

## Roadmap Placement
- long-term track: Admin Client Bridge / Authorized Tool Session / client-hosted WebUI / capability-gated tool items
- near-term track: Simulation Backend expansion, catalog / graph / runtime capability, WebUI editor, new block simulation semantics, trace/result/debug
- deferred implementation prompts: pass; each future candidate needs a fresh scoped prompt

## Validation
- git diff --check: pass
- docs grep: pass
- code changed: no

## Final Recommendation

Merge these docs into `mc-1.21.11` as a long-term architecture record. Do not start implementation from this merge; continue near-term development from Simulation Backend / catalog / graph / editor work.
