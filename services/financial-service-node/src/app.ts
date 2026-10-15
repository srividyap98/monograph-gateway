import type { IncomingMessage, RequestListener, ServerResponse } from 'node:http';
import { setTimeout as sleep } from 'node:timers/promises';

import { BALANCES, TRANSACTIONS, type Balance, type Transaction } from './data.js';

// JSON.rawJSON (V8 11.4+, Node >= 21) is not in TypeScript's ES2023 lib yet.
declare global {
  interface JSON {
    rawJSON(text: string): unknown;
  }
}

export interface Options {
  /** Path prefix shared with the Java simulator, e.g. "/financial-service". */
  basePath: string;
  /** Demo-only fault injection: delay every API response, to exercise the gateway's read timeout. */
  responseDelayMs: number;
}

const DEFAULT_LIMIT = 10;

/** Thrown for a request the service rejects with 400. */
class BadRequest extends Error {}

/**
 * The two batch endpoints the gateway's HttpFinancialClient calls. The Java simulator also has
 * per-account endpoints; the gateway stopped using them when DataLoader batching arrived, so
 * they are not ported.
 */
export function createHandler(options: Options): RequestListener {
  const routes: Record<string, (params: URLSearchParams) => unknown[]> = {
    [`${options.basePath}/balances`]: balances,
    [`${options.basePath}/transactions`]: transactions,
  };

  return (req, res) => {
    handle(req, res).catch((err: unknown) => {
      console.error('Unhandled error', err);
      if (!res.headersSent) {
        problem(res, 500, 'Internal Server Error', 'Unexpected error');
      } else {
        res.destroy();
      }
    });
  };

  async function handle(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? '/', 'http://localhost');
    if (url.pathname === '/health') {
      return json(res, 200, '{"status":"UP"}');
    }
    const route = routes[url.pathname];
    if (!route) {
      return problem(res, 404, 'Not Found', `No route for ${url.pathname}`);
    }
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return problem(res, 405, 'Method Not Allowed', `${req.method} is not supported`);
    }
    let body: string;
    try {
      body = toJson(route(url.searchParams));
    } catch (err) {
      if (err instanceof BadRequest) {
        return problem(res, 400, 'Bad Request', err.message);
      }
      throw err;
    }
    if (options.responseDelayMs > 0) {
      await sleep(options.responseDelayMs);
    }
    return json(res, 200, body);
  }
}

/** GET /balances?accountIds=..&accountIds=..; accounts without a balance are absent. */
function balances(params: URLSearchParams): Balance[] {
  return accountIds(params)
    .map((id) => BALANCES.get(id))
    .filter((b): b is Balance => b !== undefined);
}

/** GET /transactions?accountIds=..&limit=N: newest N per account, as one flat list in accountIds order. */
function transactions(params: URLSearchParams): Transaction[] {
  const limit = parseLimit(params.get('limit'));
  return accountIds(params).flatMap((id) =>
    TRANSACTIONS.filter((t) => t.accountId === id)
      .toSorted((a, b) => Date.parse(b.postedAt) - Date.parse(a.postedAt))
      .slice(0, limit),
  );
}

/**
 * Same binding as Spring's {@code @RequestParam Set<String>}: repeated parameters, each also
 * split on commas; blanks dropped; duplicates removed keeping first-seen order. The parameter
 * itself is required (400 when absent), but may be empty.
 */
function accountIds(params: URLSearchParams): string[] {
  const raw = params.getAll('accountIds');
  if (raw.length === 0) {
    throw new BadRequest("Required parameter 'accountIds' is not present");
  }
  const ids = raw.flatMap((v) => v.split(',')).map((v) => v.trim()).filter((v) => v !== '');
  return [...new Set(ids)];
}

/**
 * Non-negative integer, default 10. (The Java simulator answers a negative limit with 500;
 * a 400 is the honest status. The gateway only ever sends 1..50.)
 */
function parseLimit(value: string | null): number {
  if (value === null) {
    return DEFAULT_LIMIT;
  }
  if (!/^\d+$/.test(value)) {
    throw new BadRequest('limit must be a non-negative integer');
  }
  return Number(value);
}

const MONEY_FIELDS = new Set(['available', 'ledger', 'amount']);

/**
 * Money fields are written as raw JSON number tokens from their decimal text, so 10500.00 goes
 * on the wire exactly as Jackson writes a BigDecimal. JSON.stringify(10500.00) would give 10500,
 * and the gateway (which exposes money as decimal strings) would return "10500" instead of "10500.00".
 */
export function toJson(value: unknown): string {
  return JSON.stringify(value, (key, v: unknown) =>
    MONEY_FIELDS.has(key) && typeof v === 'string' ? JSON.rawJSON(v) : v,
  );
}

function json(res: ServerResponse, status: number, body: string): void {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

/** RFC 9457 problem details, the same error shape the Customer simulator uses. */
function problem(res: ServerResponse, status: number, title: string, detail: string): void {
  const body = JSON.stringify({ type: 'about:blank', title, status, detail });
  res.writeHead(status, { 'Content-Type': 'application/problem+json', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}
