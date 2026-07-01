# PixelLogic Admin Client Bridge & Authorized Tool Session

This document is a docs-only architecture design. It does not authorize implementation, networking, item registration, permission integration, WebUI packaging, tag, release, or merge work.

## Goals

- client-hosted WebUI: the long-term recommended admin UI runs from an authorized PixelLogic client on local `127.0.0.1`, not from an always-on public server WebAdmin endpoint.
- server-authorized admin session: the server grants a bounded admin session to a specific player/client connection after permission checks.
- client mod default no functionality: installing the PixelLogic client mod alone gives no admin capability, no WebUI, no bridge, no tool action, and no overlay.
- server remains authority: graph, catalog, validation, runtime, storage, trace, capability checks, and real Minecraft side effects remain server-authoritative.
- real tool items gated by capability: real PixelLogic tool items may exist later, but their useful behavior is disabled unless the server grants the matching capability.

## Non-goals

- no implementation in this phase.
- no removal of server HTTP.
- no full permission plugin.
- no MC adapter.
- no tool item registration yet.
- no Minecraft custom payload implementation.
- no WebUI static packaging.
- no Channel, Region, old TZZ, or public remote admin model.

## Components

### PixelLogic Server

The server is the only authority for:

- graph read/write, draft validation, commit, and runtime graph installation;
- Block Catalog definitions and capability/safety metadata;
- simulation execution, trace retrieval, runtime run requests, and real Minecraft adapter calls later;
- admin session lifecycle;
- capability checks;
- tool item server behavior;
- rate limits, payload size limits, stale request checks, audit, and revocation.

The current `server/api` HTTP API remains a development/local transport. Future client bridge work should route to the same server-side application operations rather than creating a second graph/runtime authority.

### PixelLogic Client

The client mod is a transport and interaction host, not an authority. In the default unauthenticated state it should:

- not start a local WebUI server;
- not expose a localhost bridge;
- not show an admin entry point;
- not enable admin hotkeys, overlay, selectors, or tool interactions;
- not request graph/catalog/admin data;
- not make server decisions locally.

After the server starts an authorized session, the client may:

- start a localhost-only bridge with a random port and one-time token;
- open the WebUI for that session;
- forward WebUI requests through Minecraft networking;
- display selection overlays and client-side tool affordances only for granted capabilities.

### PixelLogic WebUI

The WebUI should stay a static frontend that talks to a transport. The v1 API shape should remain recognizable:

- catalog read;
- graph read;
- draft save;
- validate;
- commit;
- simulation/test run;
- trace read;
- future runtime run.

The WebUI should not know whether a request goes directly to the current dev HTTP server or through a client-local bridge into Minecraft custom payloads. A small WebUI transport abstraction is the preferred long-term boundary.

### Local Bridge

The local bridge belongs to the authorized client session. It should:

- bind only to `127.0.0.1`;
- choose a random available port;
- require a one-time session token;
- validate `Origin` and avoid `Access-Control-Allow-Origin: *`;
- translate local HTTP/WebSocket requests into PixelLogic Minecraft payload envelopes;
- close or reject requests when the session is revoked, expired, or the client disconnects.

### Minecraft Networking

Minecraft networking is the hop between the authorized client and the authoritative server. It should carry envelopes such as `BRIDGE_REQUEST`, `BRIDGE_RESPONSE`, tool actions, selection updates, and session lifecycle messages. It must not be treated as trusted merely because it arrived from a modded client.

## Session Lifecycle

### client hello

When a PixelLogic client joins a server, it may send a small `HELLO`:

- installed: true;
- client protocol version;
- supported capabilities;
- whether local bridge is supported;
- whether tool UI / overlay / selection are supported.

The server records client support and version. This does not grant admin power.

### authorization

The first implementation should stay simple:

- `/pixellogic web` for the executing player, requiring permission level 4;
- console/operator grant such as `/pixellogic admin grant <player>` or `/pixellogic web grant <player>`;
- future permission nodes can map to capabilities later.

The server checks that:

- the requester is OP 4 or console-authorized;
- the target player has a compatible PixelLogic client;
- no conflicting active session exists, or the old session can be safely replaced;
- server policy allows WebUI sessions.

### start session

If authorized, the server sends `START_ADMIN_SESSION` to that client:

- `sessionId`;
- `expiresAt`;
- `allowedCapabilities`;
- `oneTimeWebToken`;
- `serverDisplayName`;
- `protocolVersion`.

The client then starts the local bridge and opens:

```text
http://127.0.0.1:<randomPort>/?token=<oneTimeWebToken>
```

### local web start

The local bridge should consume or bind the token to:

- current server identity;
- player UUID;
- session id;
- expiry;
- protocol version.

The token must not be printed in logs or persisted to long-lived config.

### request routing

Every WebUI request follows:

```text
Browser
 -> 127.0.0.1:<randomPort> + token
 -> PixelLogic Client Local Bridge
 -> Minecraft networking payload
 -> PixelLogic Server Core
```

Every server-side request must re-check:

- player UUID;
- session id;
- capability;
- graph id / graph version / save sequence when applicable;
- request size;
- rate limit;
- expiry and revocation status.

The principle is simple: client UI is convenience, server checks are security.

### revoke

Session revocation should be possible through console/admin command. Revocation sends `SESSION_REVOKED` or `STOP_ADMIN_SESSION`, invalidates server session state, and makes later bridge requests fail closed.

### expiry

First version recommendation:

- player disconnect: expire immediately;
- server stop/restart: expire all sessions;
- 30 minutes idle: expire;
- permission change/revoke: expire;
- protocol mismatch: reject or expire.

### disconnect

Client disconnect or bridge shutdown invalidates the local bridge. The server should also clear any active session for that player to prevent stale request replay.

## Capabilities

### capability list

Recommended initial capability names:

- `WEB_UI`
- `LOCAL_BRIDGE`
- `CATALOG_READ`
- `GRAPH_READ`
- `GRAPH_EDIT`
- `GRAPH_COMMIT`
- `SIMULATION_RUN`
- `TRACE_READ`
- `RUNTIME_RUN`
- `TOOL_ITEM_USE`
- `SELECTION_BLOCK`
- `SELECTION_REGION`
- `SELECTION_ENTITY`

Region and entity selection remain future capabilities; naming them now prevents a later all-powerful boolean session.

### capability checks

Capability checks are server-side and per request. Examples:

- catalog route requires `CATALOG_READ`;
- graph GET requires `GRAPH_READ`;
- draft save requires `GRAPH_EDIT`;
- commit requires `GRAPH_COMMIT`;
- simulation/test run requires `SIMULATION_RUN`;
- trace routes require `TRACE_READ`;
- real server execution requires `RUNTIME_RUN`;
- tool interaction requires `TOOL_ITEM_USE` plus the specific selection capability.

### future permissions

Future permission integration can map permission nodes to capabilities:

- `pixel_logic.admin.web` -> `WEB_UI`, `LOCAL_BRIDGE`;
- `pixel_logic.admin.read` -> `CATALOG_READ`, `GRAPH_READ`, `TRACE_READ`;
- `pixel_logic.admin.edit` -> `GRAPH_EDIT`, `GRAPH_COMMIT`;
- `pixel_logic.admin.simulate` -> `SIMULATION_RUN`;
- `pixel_logic.admin.run` -> `RUNTIME_RUN`;
- `pixel_logic.admin.tools` -> `TOOL_ITEM_USE`, selection capabilities.

Do not start with a large permission plugin. OP 4 plus console grant is enough for the first slice.

## Tool Items

### real item registration policy

Real PixelLogic tool items can be registered in a later implementation, for example:

- `pixel_logic:selector_wand`;
- `pixel_logic:region_tool`;
- `pixel_logic:entity_picker`;
- `pixel_logic:logic_debugger`.

Registration does not imply permission. Server behavior must remain capability-gated.

### default unusable state

If an ordinary player obtains a tool item:

- right-click does nothing useful;
- no admin UI opens;
- no overlay enables;
- no valid edit request is accepted;
- server rejects tool payloads without capability.

### server-side enforcement

The server must verify:

- active session;
- player UUID;
- `TOOL_ITEM_USE`;
- selection capability;
- request size/rate;
- target world/dimension validity;
- whether the action is read-only, simulated, or real mutating.

### client-side UX

Client-side UX is only a courtesy:

- unauthorized players get no effect or a low-frequency "not authorized" hint;
- authorized admins may see overlay, selected block/region/entity markers, and WebUI sync affordances;
- admin debug mode may display more explicit denial reasons.

### anti-spam / cooldown

Denial messages should be rate-limited. The server should also rate-limit tool payloads because malicious clients can ignore client-side cooldowns.

## Security

### localhost binding

The local bridge must bind to `127.0.0.1`, not `0.0.0.0`.

### random port

Use a random available port per session. Do not hardcode a predictable public admin endpoint for normal use.

### token

Use a one-time, high-entropy token bound to session, player, server, and expiry. Token acceptance by the local bridge is not enough; the server still validates the session envelope on every payload.

### origin

The bridge must validate `Origin` for browser requests. It should accept only its own local WebUI origin or a narrowly defined dev origin.

### CORS

Do not use `Access-Control-Allow-Origin: *`. Dev exceptions must be explicit and not become production defaults.

### CSRF / DNS rebinding

Other websites can try to reach localhost services from the browser. The design must assume this and use:

- token-bearing requests;
- strict origin checks;
- host checks;
- no wildcard CORS;
- no state-changing GET;
- bounded session lifetime.

### per-request server validation

The server validates every request. A client saying "I am authorized" is not evidence.

### logging

Do not log tokens, raw large graph payloads, or private local bridge URLs. Log session ids in a redacted or short form when needed.

### replay/stale protection

Requests should include sequence and createdAt. Graph mutations should include graph version/fingerprint/save sequence where applicable. Stale or duplicate requests should fail closed or be idempotently ignored.

## Relationship to Server HTTP API

### current dev mode

The current JDK `HttpServer` bound to `127.0.0.1:18111` remains useful for local development, Vite proxy, and self-checks.

### future client bridge default

The long-term recommended administrator flow is client-hosted WebUI through an authorized Minecraft session.

### transport abstraction

The WebUI should grow a transport boundary:

```text
WebUI API call
 -> dev HTTP transport
 -> or client bridge transport
 -> same server application operation
```

Transport changes must not fork graph validation, catalog authority, simulation semantics, trace formatting, or storage.

## Implementation Phases

1. Phase 0 docs: keep this as architecture only.
2. Phase 1 handshake/status: add client/server protocol version handshake and server-side client capability status, with no WebUI bridge and no tools.
3. Phase 2 local bridge read-only: authorized session can read catalog, committed graph, and traces through localhost bridge.
4. Phase 3 graph edit/save: route draft save, validate, and commit through the bridge with graph version/sequence checks.
5. Phase 4 simulation run: route WebUI test/simulation requests through the bridge.
6. Phase 5 tool item selection: register the smallest useful tool item and gate it by capability; start with block selection before Region/entity.
7. Phase 6 server HTTP dev-only: keep server HTTP as optional dev/local mode, not the recommended admin route.
