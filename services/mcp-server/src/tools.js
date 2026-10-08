import { z } from 'zod';
import { ApiError } from './tradeApi.js';

const ORDER_ID = z.string().uuid();
const SYMBOL = z.string().min(1).max(20).regex(/^[A-Za-z0-9.:^=-]+$/, 'letters, digits and . : ^ = - only');

function ok(data) {
  return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] };
}

function failure(error) {
  const text =
    error instanceof ApiError
      ? `${error.message}${error.code && error.code !== 'UPSTREAM' ? ` (${error.code})` : ''}`
      : 'Something went wrong on the server. Nothing was changed.';
  return { isError: true, content: [{ type: 'text', text }] };
}

/**
 * Registers every tool for one connector. `ctx` is what the Trade API told us about the link: its account, its
 * permissions and a one-minute token. A tool the link has no permission for is not offered at all, so an
 * assistant cannot even attempt it; the Trade API checks again on every order step.
 */
export function registerTools(server, ctx, api) {
  const accountPath = `/api/v1/accounts/${ctx.accountId}`;
  const has = (scope) => ctx.scopes.includes(scope);

  function define(name, scope, config, handler) {
    if (!has(scope)) return;
    server.registerTool(name, config, async (args) => {
      try {
        const result = await handler(args ?? {});
        api.audit(ctx.connectorId, { tool: name, outcome: 'ok' }).catch(() => {});
        return ok(result);
      } catch (error) {
        const detail = error instanceof ApiError ? (error.code ?? 'error') : 'internal';
        api.audit(ctx.connectorId, { tool: name, outcome: 'error', detail }).catch(() => {});
        return failure(error);
      }
    });
  }

  const readOnly = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };
  const write = { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false };
  const destructive = { readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false };

  // ---- read ----

  define('get_balance', 'READ', {
    title: 'Cash balance',
    description: 'The cash balance of the customer\'s trading account, with its currency.',
    annotations: readOnly,
  }, () => api.get(`${accountPath}/balance`, ctx.jwt));

  define('get_positions', 'READ', {
    title: 'Holdings',
    description: 'The shares the customer holds: symbol, quantity and average cost.',
    annotations: readOnly,
  }, () => api.get(`${accountPath}/positions`, ctx.jwt));

  define('get_portfolio_pnl', 'READ', {
    title: 'Profit and loss',
    description: 'Realised and unrealised profit and loss for the whole portfolio, with how fresh each price is.',
    annotations: readOnly,
  }, () => api.get(`/api/v1/portfolio/${ctx.accountId}/pnl`, ctx.jwt));

  define('get_watchlist_quotes', 'READ', {
    title: 'Watchlist quotes',
    description: 'Current price, bid, ask and day change for every symbol on the customer\'s watchlist. '
      + 'Check `stale` before relying on a price.',
    annotations: readOnly,
  }, () => api.get(`${accountPath}/watchlist/quotes`, ctx.jwt));

  define('search_instruments', 'READ', {
    title: 'Search instruments',
    description: 'Find tradable instruments by symbol or name. Use it to confirm a symbol before preparing an order.',
    inputSchema: { query: z.string().min(1).max(40).describe('Part of a symbol or company name') },
    annotations: readOnly,
  }, async ({ query }) => {
    const needle = query.trim().toLowerCase();
    const all = await api.get('/api/v1/instruments', ctx.jwt);
    return (all ?? [])
      .filter((i) => i.symbol?.toLowerCase().includes(needle) || i.name?.toLowerCase().includes(needle))
      .slice(0, 20);
  });

  define('list_orders', 'READ', {
    title: 'Order history',
    description: 'The customer\'s orders, newest first. Optionally filter by status.',
    inputSchema: {
      status: z.enum(['NEW', 'FILLED', 'CANCELLED', 'REJECTED']).optional(),
    },
    annotations: readOnly,
  }, ({ status }) => api.get(`${accountPath}/orders${status ? `?status=${status}` : ''}`, ctx.jwt));

  // ---- alerts ----

  define('list_alerts', 'ALERTS', {
    title: 'Price alerts',
    description: 'The customer\'s price alerts and whether each is active, triggered or switched off.',
    annotations: readOnly,
  }, () => api.get(`${accountPath}/alerts`, ctx.jwt));

  define('create_alert', 'ALERTS', {
    title: 'Create price alert',
    description: 'Create a price alert: "tell me when SYMBOL is above/below PRICE". The customer is told by '
      + 'e-mail and in the app when it triggers.',
    inputSchema: {
      symbol: SYMBOL,
      condition: z.enum(['>', '>=', '<', '<=', '==']).describe('How the live price is compared with `price`'),
      price: z.number().positive().max(1e9),
    },
    annotations: write,
  }, ({ symbol, condition, price }) =>
    api.post(`${accountPath}/alerts`, ctx.jwt, { symbol, condition, price }));

  define('delete_alert', 'ALERTS', {
    title: 'Delete price alert',
    description: 'Delete one of the customer\'s price alerts by its id (from list_alerts).',
    inputSchema: { alertId: z.number().int().positive() },
    annotations: destructive,
  }, ({ alertId }) => api.del(`${accountPath}/alerts/${alertId}`, ctx.jwt));

  // ---- trading: two steps ----

  define('prepare_order', 'TRADE', {
    title: 'Prepare an order (step 1 of 2)',
    description: 'Step 1 of 2. Checks an order against this link\'s limits and stores it. NOTHING IS TRADED. '
      + 'Show the customer the symbol, side, quantity, limit price and estimated value, then ask them to confirm '
      + 'in the chat. Only after they clearly say yes, call confirm_order with the returned pendingId. '
      + 'It can be confirmed for about two minutes.',
    inputSchema: {
      symbol: SYMBOL,
      side: z.enum(['BUY', 'SELL']),
      quantity: z.number().int().min(1).max(1_000_000),
      price: z.number().positive().max(1e9).describe('Limit price, at most 2 decimal places'),
    },
    annotations: write,
  }, (order) => api.prepareOrder(ctx.connectorId, order));

  define('confirm_order', 'TRADE', {
    title: 'Confirm and place an order (step 2 of 2)',
    description: 'Step 2 of 2. PLACES A REAL ORDER for a prepared order. Call it only after the customer has '
      + 'explicitly confirmed the exact order shown by prepare_order in this conversation. Never call it on '
      + 'your own initiative, never for an order the customer has not seen, and never to retry after an unknown '
      + 'outcome without checking list_orders first.',
    inputSchema: { pendingId: ORDER_ID.describe('The pendingId returned by prepare_order') },
    annotations: destructive,
  }, ({ pendingId }) => api.confirmOrder(ctx.connectorId, pendingId));

  define('get_prepared_order', 'TRADE', {
    title: 'Prepared order status',
    description: 'The state of a prepared order: PENDING, PLACED, REJECTED, FAILED or EXPIRED.',
    inputSchema: { pendingId: ORDER_ID },
    annotations: readOnly,
  }, ({ pendingId }) => api.pendingOrder(ctx.connectorId, pendingId));

  define('cancel_order', 'TRADE', {
    title: 'Cancel an order',
    description: 'Cancel an open order by its orderId (from list_orders). Confirm with the customer first.',
    inputSchema: { orderId: ORDER_ID },
    annotations: destructive,
  }, ({ orderId }) => api.cancelOrder(ctx.connectorId, orderId));
}

export const SERVER_INSTRUCTIONS = [
  'You act on one customer\'s trading account, with only the permissions they gave this link.',
  'Reading data and creating alerts are low risk. Trading is two steps: prepare_order never trades; confirm_order',
  'places a real order and must only follow the customer\'s explicit "yes" to the exact order you showed them.',
  'Prices can be stale: say so when a quote is marked stale. This is not financial advice.',
].join(' ');
