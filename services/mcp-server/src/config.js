// Everything the server reads from its environment, in one place. Nothing here is logged.

function int(name, fallback) {
  const value = Number.parseInt(process.env[name] ?? '', 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

export function loadConfig(env = process.env) {
  const serviceKey = env.MCP_SERVICE_KEY ?? '';
  return {
    port: int('PORT', 8090),
    tradeApiUrl: (env.TRADE_API_URL ?? 'http://localhost:8084').replace(/\/+$/, ''),
    serviceKey,
    // Browsers send an Origin header; the assistants' servers do not. Any Origin not listed here is refused,
    // which keeps a web page from driving a customer's link through their own browser (DNS rebinding).
    allowedOrigins: (env.MCP_ALLOWED_ORIGINS ?? '')
      .split(',')
      .map((o) => o.trim())
      .filter(Boolean),
    ratePerMinute: int('MCP_RATE_PER_MINUTE', 60),
    resolveCacheMs: int('MCP_RESOLVE_CACHE_MS', 5000),
    requestTimeoutMs: int('MCP_REQUEST_TIMEOUT_MS', 8000),
    maxBodyBytes: int('MCP_MAX_BODY_BYTES', 65536),
  };
}
