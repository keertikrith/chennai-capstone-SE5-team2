import { createHash } from 'node:crypto';
import { createServer } from 'node:http';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { ApiError } from './tradeApi.js';
import { registerTools, SERVER_INSTRUCTIONS } from './tools.js';

// The link a customer pastes into their assistant is  /t/<secret>/mcp . The secret IS the credential, so it is
// never logged: every log line names the route as /t/[link]/mcp and nothing else about the URL.
const ROUTE = /^\/t\/([A-Za-z0-9_-]{8,100})\/mcp$/;

function jsonRpcError(res, status, code, message) {
  if (res.headersSent) return;
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify({ jsonrpc: '2.0', error: { code, message }, id: null }));
}

class BodyTooLarge extends Error {}

async function readJson(req, limit) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw new BodyTooLarge();
    chunks.push(chunk);
  }
  if (size === 0) return undefined;
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

/** A fixed one-minute window per link, held in memory. Enough to stop a runaway assistant loop. */
export function createRateLimiter(perMinute, now = Date.now) {
  const windows = new Map();
  return function allow(key) {
    const minute = Math.floor(now() / 60000);
    const entry = windows.get(key);
    if (!entry || entry.minute !== minute) {
      windows.set(key, { minute, count: 1 });
      if (windows.size > 5000) {
        for (const [k, v] of windows) if (v.minute !== minute) windows.delete(k);
      }
      return true;
    }
    entry.count += 1;
    return entry.count <= perMinute;
  };
}

/** Remembers what the Trade API said about a link for a few seconds, so one chat turn is not many lookups. */
function createResolver(api, ttlMs, now = Date.now) {
  const cache = new Map();
  return async function resolve(secret) {
    const key = createHash('sha256').update(secret).digest('hex');
    const hit = cache.get(key);
    if (hit && hit.expires > now() && hit.jwtExpires > now() + 15000) return { key, ctx: hit.ctx };
    try {
      const resolved = await api.resolve(secret);
      const ctx = {
        connectorId: resolved.connectorId,
        accountId: resolved.accountId,
        label: resolved.label,
        scopes: resolved.scopes,
        jwt: resolved.jwt,
      };
      cache.set(key, {
        ctx,
        expires: now() + ttlMs,
        jwtExpires: now() + resolved.jwtTtlSeconds * 1000,
      });
      if (cache.size > 2000) for (const [k, v] of cache) if (v.expires <= now()) cache.delete(k);
      return { key, ctx };
    } catch (error) {
      cache.delete(key);
      if (error instanceof ApiError && (error.status === 401 || error.status === 404)) return { key, ctx: null };
      throw error;
    }
  };
}

export function createApp({ config, api, log = console.log }) {
  const allow = createRateLimiter(config.ratePerMinute);
  const resolve = createResolver(api, config.resolveCacheMs);

  async function handleMcp(req, res, secret) {
    if (req.method !== 'POST') {
      // Stateless server: there is no session to stream to or to close.
      res.setHeader('Allow', 'POST');
      return jsonRpcError(res, 405, -32000, 'Method not allowed.');
    }

    let linked;
    try {
      linked = await resolve(secret);
    } catch {
      return jsonRpcError(res, 502, -32000, 'The trading platform is unavailable. Try again shortly.');
    }
    if (!linked.ctx) {
      log('[mcp] refused: unknown, revoked or expired link');
      res.setHeader('WWW-Authenticate', 'Bearer error="invalid_token"');
      return jsonRpcError(res, 401, -32001, 'This link is not valid any more. Make a new one in Settings.');
    }
    if (!allow(linked.key)) {
      res.setHeader('Retry-After', '30');
      return jsonRpcError(res, 429, -32000, 'Too many requests. Slow down.');
    }

    let body;
    try {
      body = await readJson(req, config.maxBodyBytes);
    } catch (error) {
      return error instanceof BodyTooLarge
        ? jsonRpcError(res, 413, -32000, 'Request too large.')
        : jsonRpcError(res, 400, -32700, 'Parse error.');
    }

    const server = new McpServer(
      { name: 'etp-trading', version: '1.0.0' },
      { instructions: SERVER_INSTRUCTIONS },
    );
    registerTools(server, linked.ctx, api);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on('close', () => {
      transport.close().catch(() => {});
      server.close().catch(() => {});
    });
    try {
      await server.connect(transport);
      await transport.handleRequest(req, res, body);
    } catch {
      log('[mcp] request failed');
      jsonRpcError(res, 500, -32603, 'Internal error.');
    }
  }

  return createServer((req, res) => {
    const path = (req.url ?? '').split('?')[0];

    if (path === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end('{"status":"UP"}');
    }

    const match = ROUTE.exec(path);
    if (!match) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      return res.end('{"error":"not found"}');
    }

    const origin = req.headers.origin;
    if (origin && !config.allowedOrigins.includes(origin)) {
      log('[mcp] refused: origin not allowed');
      return jsonRpcError(res, 403, -32000, 'Origin not allowed.');
    }

    handleMcp(req, res, match[1]).catch(() => {
      log('[mcp] unexpected failure');
      jsonRpcError(res, 500, -32603, 'Internal error.');
    });
  });
}
