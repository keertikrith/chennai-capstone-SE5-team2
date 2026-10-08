# 0020 AI assistants reach the account through a private MCP link, not OAuth

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |
| Decided by | Sprint 10 work, at the product owner's request (drafted by Claude; the team to confirm) |
| Supersedes | nothing |

## Context

The request: from a browser assistant (Claude, ChatGPT) a customer should be able to ask about their account, set price alerts and place orders, the way the Zerodha and Zomato assistants work. The standard way is a remote MCP server with OAuth 2.1 (authorization code with PKCE, dynamic client registration, consent screen). That means extending the auth service with an authorisation server. The product owner chose to avoid OAuth, and to write the server in JavaScript.

## Decision

- A new service, `services/mcp-server` (Node, plain JavaScript, the official MCP SDK, stateless Streamable HTTP). It has no database and no business rules; it calls the Trade API.
- The credential is a **private link** the customer makes in Settings, `https://<host>/t/<secret>/mcp`. The secret is 256 random bits, shown once. The Trade API stores only its SHA-256 (`mcp_connectors`, migration 007).
- Each link has permissions (READ always; ALERTS; TRADE), a per-order value limit, a per-day order limit and an expiry (default 30 days, at most 90). A link can be revoked and stops working on its next call.
- The MCP server learns a link's account and permissions from `POST /internal/mcp/resolve`, guarded by a shared service key (`MCP_SERVICE_KEY`; without one the internal API refuses everything). The Trade API answers with a **one-minute token for that one account**, built like the automation module's. The customer's login token and password never reach the assistant or the MCP server.
- **Orders are two steps and every rule lives in the Trade API.** `prepare_order` checks limits and stores the order; it trades nothing. `confirm_order` places it only after the customer says yes **in the chat**, once: one guarded `UPDATE` claims the row and its id is the order's idempotency key. A prepared order expires after two minutes. The customer gets the normal order e-mail from the existing trade events; they never need to open the app.
- Tools the link has no permission for are not offered at all. Write tools carry MCP annotations (`destructiveHint` on `confirm_order`, `cancel_order`, `delete_alert`) so the assistant's own approval prompt appears.
- Every tool call and order step is written to `mcp_audit_log`; the customer reads it in Settings → AI assistants.

## Consequences

- No OAuth server to build, host or secure, and no Keycloak or Auth0. The cost is a worse connect experience: the customer copies a link instead of clicking "Connect".
- **A leaked link is a working credential until it is revoked or expires.** What bounds the damage is the permission set, the per-order and per-day limits, the two-minute single-use confirmation and the audit trail, not the secrecy of the link alone. Mitigations: the link is shown once, only its hash is stored, the server never logs the path, Origin headers are refused by default, requests are rate-limited, and HTTPS is required in production (the secret travels in the URL).
- **Chat-only confirmation can be defeated by a confused or prompt-injected model** that calls `confirm_order` without the customer's yes. The assistant's tool-approval prompt is a second gate, and the limits above cap the loss. Customers who want a stricter setup should leave TRADE off. An out-of-chat approval (a button in the app) was considered and rejected because it defeats the purpose.
- Some assistants may refuse "no authentication" connectors that can write. If one does, that assistant needs OAuth, which could be added in front of the same tables and tools without redoing them.
- `/internal/mcp/**` is open to any caller that holds the service key; it must not be exposed publicly (in compose it is reachable only on `trading-net` through the backend port that is already published, so the key is the barrier; a reverse proxy should block `/internal/` from outside).
