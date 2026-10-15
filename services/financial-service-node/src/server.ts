import { createServer } from 'node:http';

import { createHandler } from './app.js';

/**
 * Entry point. Configuration, all optional:
 *   PORT                        default 8091 (the Java simulators use 8090)
 *   BASE_PATH                   default /financial-service
 *   FINANCIAL_RESPONSE_DELAY_MS default 0; demo-only fault injection
 */
const port = intEnv('PORT', 8091);
const basePath = process.env.BASE_PATH ?? '/financial-service';
const responseDelayMs = intEnv('FINANCIAL_RESPONSE_DELAY_MS', 0);

if (typeof JSON.rawJSON !== 'function') {
  // Without it money would be serialized as JS numbers and lose its scale.
  console.error('JSON.rawJSON is unavailable: Node.js >= 22 is required');
  process.exit(1);
}

const server = createServer(createHandler({ basePath, responseDelayMs }));
server.listen(port, () => {
  console.log(`financial-service-node listening on http://localhost:${port}${basePath}`
    + (responseDelayMs > 0 ? ` (delaying responses by ${responseDelayMs}ms)` : ''));
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, () => {
    // Stop accepting connections, let in-flight requests finish, then exit.
    server.close(() => process.exit(0));
    server.closeIdleConnections();
  });
}

function intEnv(name: string, fallback: number): number {
  const value = process.env[name];
  if (value === undefined || value === '') {
    return fallback;
  }
  if (!/^\d+$/.test(value)) {
    console.error(`${name} must be a non-negative integer, got "${value}"`);
    process.exit(1);
  }
  return Number(value);
}
