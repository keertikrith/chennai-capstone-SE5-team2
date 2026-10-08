import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { createApp, createRateLimiter } from '../src/app.js';
import { ApiError } from '../src/tradeApi.js';

const GOOD = 'etpmcp_goodsecretgoodsecretgoodsecret1234567890abc';
const READ_ONLY = 'etpmcp_readonlysecretreadonlysecret1234567890ab';
const PENDING = '22222222-2222-4222-8222-222222222222';

function fakeApi(calls) {
  const connectors = {
    [GOOD]: { connectorId: 'c-1', accountId: 7, label: 'Claude', scopes: ['READ', 'ALERTS', 'TRADE'] },
    [READ_ONLY]: { connectorId: 'c-2', accountId: 7, label: 'Reader', scopes: ['READ'] },
  };
  return {
    async resolve(token) {
      const c = connectors[token];
      if (!c) throw new ApiError(401, 'AUTH-401', 'Unauthorised');
      return { ...c, jwt: 'jwt-for-7', jwtTtlSeconds: 60 };
    },
    async get(path, jwt) {
      calls.push({ method: 'GET', path, jwt });
      return { cashBalance: 1000 };
    },
    async post(path, jwt, body) {
      calls.push({ method: 'POST', path, jwt, body });
      return { id: 1 };
    },
    async del(path, jwt) {
      calls.push({ method: 'DELETE', path, jwt });
      return { id: 1 };
    },
    async prepareOrder(connectorId, order) {
      calls.push({ method: 'PREPARE', connectorId, order });
      if (order.quantity > 100) throw new ApiError(422, 'MCP-422', 'Over this link\'s limit.');
      return { pendingId: PENDING, status: 'PENDING' };
    },
    async confirmOrder(connectorId, pendingId) {
      calls.push({ method: 'CONFIRM', connectorId, pendingId });
      return { pendingId, status: 'PLACED' };
    },
    async pendingOrder() {
      return { status: 'PENDING' };
    },
    async cancelOrder() {
      return { cancelled: true };
    },
    async audit(connectorId, entry) {
      calls.push({ method: 'AUDIT', connectorId, entry });
    },
  };
}

describe('MCP server', () => {
  const calls = [];
  const logs = [];
  let server;
  let base;

  before(async () => {
    const config = { ratePerMinute: 1000, resolveCacheMs: 0, maxBodyBytes: 65536, allowedOrigins: [] };
    server = createApp({ config, api: fakeApi(calls), log: (line) => logs.push(line) });
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(() => new Promise((resolve) => server.close(resolve)));

  async function connect(secret) {
    const client = new Client({ name: 'test', version: '1.0.0' });
    await client.connect(new StreamableHTTPClientTransport(new URL(`${base}/t/${secret}/mcp`)));
    return client;
  }

  it('offers only the tools the link has permission for', async () => {
    const full = await connect(GOOD);
    const names = (await full.listTools()).tools.map((t) => t.name);
    assert.ok(['get_balance', 'create_alert', 'prepare_order', 'confirm_order', 'cancel_order'].every((n) => names.includes(n)));
    await full.close();

    const reader = await connect(READ_ONLY);
    const readNames = (await reader.listTools()).tools.map((t) => t.name);
    assert.ok(readNames.includes('get_balance'));
    assert.ok(!readNames.includes('create_alert'));
    assert.ok(!readNames.includes('prepare_order'));
    assert.ok(!readNames.includes('confirm_order'));
    await reader.close();
  });

  it('marks the order-placing tool as destructive and the reads as read-only', async () => {
    const client = await connect(GOOD);
    const tools = Object.fromEntries((await client.listTools()).tools.map((t) => [t.name, t]));
    assert.equal(tools.confirm_order.annotations.destructiveHint, true);
    assert.equal(tools.get_balance.annotations.readOnlyHint, true);
    await client.close();
  });

  it('reads with the one-minute token for the link\'s own account', async () => {
    calls.length = 0;
    const client = await connect(GOOD);
    const result = await client.callTool({ name: 'get_balance', arguments: {} });
    assert.match(result.content[0].text, /1000/);
    const read = calls.find((c) => c.method === 'GET');
    assert.equal(read.path, '/api/v1/accounts/7/balance');
    assert.equal(read.jwt, 'jwt-for-7');
    await client.close();
  });

  it('prepares then confirms an order through the Trade API, and reports a refused limit as an error', async () => {
    calls.length = 0;
    const client = await connect(GOOD);

    const prepared = await client.callTool({
      name: 'prepare_order',
      arguments: { symbol: 'AAPL', side: 'BUY', quantity: 5, price: 100 },
    });
    assert.match(prepared.content[0].text, /PENDING/);
    assert.deepEqual(calls.find((c) => c.method === 'PREPARE').order, { symbol: 'AAPL', side: 'BUY', quantity: 5, price: 100 });

    const placed = await client.callTool({ name: 'confirm_order', arguments: { pendingId: PENDING } });
    assert.match(placed.content[0].text, /PLACED/);

    const refused = await client.callTool({
      name: 'prepare_order',
      arguments: { symbol: 'AAPL', side: 'BUY', quantity: 500, price: 100 },
    });
    assert.equal(refused.isError, true);
    assert.match(refused.content[0].text, /limit/);
    await client.close();
  });

  it('rejects malformed tool input before it reaches the platform', async () => {
    calls.length = 0;
    const client = await connect(GOOD);
    const result = await client.callTool({
      name: 'prepare_order',
      arguments: { symbol: 'AAPL', side: 'HOLD', quantity: 5, price: 100 },
    }).catch((error) => ({ isError: true, thrown: error }));
    assert.equal(result.isError, true);
    assert.equal(calls.some((c) => c.method === 'PREPARE'), false);
    await client.close();
  });

  it('answers 401 for an unknown link and never logs the secret', async () => {
    const res = await fetch(`${base}/t/etpmcp_unknownunknownunknown0000/mcp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
      body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
    });
    assert.equal(res.status, 401);
    assert.ok(logs.length > 0);
    for (const line of logs) assert.ok(!line.includes('etpmcp_'), `secret leaked into a log line: ${line}`);
  });

  it('refuses GET, other paths and any browser Origin', async () => {
    assert.equal((await fetch(`${base}/t/${GOOD}/mcp`)).status, 405);
    assert.equal((await fetch(`${base}/nothing`)).status, 404);
    assert.equal((await fetch(`${base}/health`)).status, 200);
    const withOrigin = await fetch(`${base}/t/${GOOD}/mcp`, {
      method: 'POST',
      headers: { Origin: 'https://evil.example', 'Content-Type': 'application/json' },
      body: '{}',
    });
    assert.equal(withOrigin.status, 403);
  });

  it('answers 413 to an oversized body', async () => {
    const res = await fetch(`${base}/t/${GOOD}/mcp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
      body: JSON.stringify({ pad: 'x'.repeat(70000) }),
    });
    assert.equal(res.status, 413);
  });
});

describe('rate limiter', () => {
  it('allows the configured number per minute per link, then resets', () => {
    let now = 0;
    const allow = createRateLimiter(2, () => now);
    assert.equal(allow('a'), true);
    assert.equal(allow('a'), true);
    assert.equal(allow('a'), false);
    assert.equal(allow('b'), true);
    now = 61000;
    assert.equal(allow('a'), true);
  });
});
