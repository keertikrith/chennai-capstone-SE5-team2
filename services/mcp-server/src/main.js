import { loadConfig } from './config.js';
import { createTradeApi } from './tradeApi.js';
import { createApp } from './app.js';

const config = loadConfig();

if (!config.serviceKey) {
  console.error('MCP_SERVICE_KEY is not set. The Trade API refuses this server without it, so it will not start.');
  process.exit(1);
}

const api = createTradeApi({
  baseUrl: config.tradeApiUrl,
  serviceKey: config.serviceKey,
  timeoutMs: config.requestTimeoutMs,
});

const server = createApp({ config, api });
server.listen(config.port, () => {
  console.log(`[mcp] listening on :${config.port}, Trade API at ${config.tradeApiUrl}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
