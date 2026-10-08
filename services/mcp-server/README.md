# MCP server: AI assistants for the trading platform

Lets a customer use their account from Claude or ChatGPT in the browser: ask about balance, holdings, orders and
prices, set price alerts and (only if they allow it) place orders they confirm in the chat. Decision:
[`decision-log/0020`](../../decision-log/0020-ai-assistants-reach-the-account-through-an-mcp-link-not-oauth.md).

It is a thin adapter. No database, no business rules: every limit is enforced by the Trade API
(`services/trade-api/src/main/java/com/capstone/etp/mcp`).

## How a customer connects

1. Settings, then **AI assistants**, then **New link**. Choose a name, what it may do, and the limits.
2. Copy the link once. It looks like `https://<host>/t/etpmcp_<secret>/mcp`. Only a hash is stored.
3. In Claude or ChatGPT, add a custom connector with that URL and no authentication.

## Tools

A link is only offered the tools its permissions allow.

| Permission | Tools |
|---|---|
| READ (always) | `get_balance`, `get_positions`, `get_portfolio_pnl`, `get_watchlist_quotes`, `search_instruments`, `list_orders` |
| ALERTS | `list_alerts`, `create_alert`, `delete_alert` |
| TRADE | `prepare_order`, `confirm_order`, `get_prepared_order`, `cancel_order` |

`prepare_order` trades nothing. `confirm_order` places the order, once, after the customer's yes in the chat.

## Configuration

| Variable | Where | Meaning |
|---|---|---|
| `MCP_SERVICE_KEY` | mcp-server **and** trade-api | Shared secret on `/internal/mcp/**`. Required; use a long random string. Empty on the Trade API means that API refuses everything |
| `MCP_PUBLIC_URL` | trade-api | Where customers reach this server. The link is built from it. Must be **https** in production |
| `TRADE_API_URL` | mcp-server | Default `http://localhost:8084` (`http://backend:8084` in compose) |
| `PORT` | mcp-server | Default 8090 |
| `MCP_ALLOWED_ORIGINS` | mcp-server | Comma-separated browser origins allowed. Leave empty: the assistants' servers send no `Origin`, and any `Origin` is refused |
| `MCP_RATE_PER_MINUTE` | mcp-server | Per link, default 60 |
| `app.mcp.*` | trade-api | Defaults and ceilings for limits and expiry (`application.properties`) |

Database: apply `databases/postgreSQL/migrations/007_mcp_access.sql` (compose does it for a fresh volume).

## Run locally

```bash
# 1. trade-api running with MCP_SERVICE_KEY=dev-key (and the migration applied)
# 2. this server
cd services/mcp-server
npm install
MCP_SERVICE_KEY=dev-key TRADE_API_URL=http://localhost:8084 npm start
# 3. make a link in the app, then try it with the MCP Inspector:
npx @modelcontextprotocol/inspector   # transport: Streamable HTTP, URL: the link
```

Claude and ChatGPT reach the server from the internet, so for a real test expose port 8090 over https (for example a
Cloudflare Tunnel or ngrok) and set `MCP_PUBLIC_URL` to that address before making the link.

## Test

```bash
npm test                      # 9 tests: a real MCP client against this server, with a fake Trade API
cd ../trade-api && mvn -o test -Dtest='Mcp*'
# with a database: TEST_DB_URL=jdbc:postgresql://localhost:5432/<db> mvn -o test -Dtest=McpChainIT
```

## Things to know

- The link is the credential. Never log or paste it; the server logs the route as `/t/[link]/mcp` and nothing else.
- Block `/internal/` at the public reverse proxy. The service key is the only thing protecting it.
- Revoking a link takes effect on the next order step immediately, and on reads within a few seconds.
