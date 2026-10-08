// The only way this server reaches the platform: the Trade API over HTTP. It holds no business rules; the Trade API
// decides every limit. Two kinds of call: with the service key (resolve, order steps, audit) and with the
// one-minute token the Trade API minted for the link's account (reads, alerts).

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export function createTradeApi({ baseUrl, serviceKey, timeoutMs, fetchImpl = fetch }) {
  async function request(method, path, { jwt, service = false, body } = {}) {
    const headers = { Accept: 'application/json' };
    if (jwt) headers.Authorization = `Bearer ${jwt}`;
    if (service) headers['X-Mcp-Service-Key'] = serviceKey;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    let response;
    try {
      response = await fetchImpl(`${baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (error) {
      const reason = error?.name === 'TimeoutError' ? 'timed out' : 'could not be reached';
      throw new ApiError(502, 'UPSTREAM', `The trading platform ${reason}.`);
    }

    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        payload = null;
      }
    }
    if (!response.ok) {
      throw new ApiError(
        response.status,
        payload?.errorCode ?? String(response.status),
        payload?.message ?? `The trading platform answered ${response.status}.`,
      );
    }
    return payload;
  }

  return {
    resolve: (token) => request('POST', '/internal/mcp/resolve', { service: true, body: { token } }),
    prepareOrder: (connectorId, order) =>
      request('POST', `/internal/mcp/connectors/${connectorId}/orders/prepare`, { service: true, body: order }),
    confirmOrder: (connectorId, pendingId) =>
      request('POST', `/internal/mcp/connectors/${connectorId}/orders/${pendingId}/confirm`, { service: true }),
    pendingOrder: (connectorId, pendingId) =>
      request('GET', `/internal/mcp/connectors/${connectorId}/orders/${pendingId}`, { service: true }),
    cancelOrder: (connectorId, orderId) =>
      request('DELETE', `/internal/mcp/connectors/${connectorId}/placed-orders/${orderId}`, { service: true }),
    audit: (connectorId, entry) =>
      request('POST', `/internal/mcp/connectors/${connectorId}/audit`, { service: true, body: entry }),
    get: (path, jwt) => request('GET', path, { jwt }),
    post: (path, jwt, body) => request('POST', path, { jwt, body }),
    del: (path, jwt) => request('DELETE', path, { jwt }),
  };
}
