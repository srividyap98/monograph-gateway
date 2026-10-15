import assert from 'node:assert/strict';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { after, before, describe, test } from 'node:test';

import { createHandler, toJson } from '../src/app.js';

async function start(responseDelayMs = 0): Promise<{ server: Server; base: string }> {
  const server = createServer(createHandler({ basePath: '/financial-service', responseDelayMs }));
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return { server, base: `http://127.0.0.1:${port}` };
}

describe('financial-service-node', () => {
  let server: Server;
  let base: string;

  before(async () => ({ server, base } = await start()));
  after(() => server.close());

  async function get(path: string): Promise<{ status: number; type: string | null; body: string }> {
    const res = await fetch(base + path);
    return { status: res.status, type: res.headers.get('content-type'), body: await res.text() };
  }

  // Expected bodies were captured from the Java simulator (FinancialServiceController).
  test('balances: money keeps its scale on the wire, unknown ids are absent, duplicates collapse', async () => {
    const res = await get('/financial-service/balances?accountIds=A-2002&accountIds=A-9999&accountIds=A-2004&accountIds=A-2002');
    assert.equal(res.status, 200);
    assert.equal(res.type, 'application/json');
    assert.equal(res.body,
      '[{"accountId":"A-2002","available":10500.00,"ledger":10500.00,"currency":"USD","asOf":"2026-09-28T00:00:00Z"},'
      + '{"accountId":"A-2004","available":0.00,"ledger":-845.10,"currency":"USD","asOf":"2026-09-28T00:00:00Z"}]');
  });

  test('transactions: newest first, limit applies per account, flat list in accountIds order', async () => {
    const res = await get('/financial-service/transactions?accountIds=A-2001&accountIds=A-2004&limit=2');
    assert.equal(res.status, 200);
    assert.equal(res.body,
      '[{"id":"T-5001","accountId":"A-2001","amount":50.00,"type":"DEBIT","description":"Coffee Roasters","postedAt":"2026-09-27T09:15:00Z"},'
      + '{"id":"T-5002","accountId":"A-2001","amount":2500.00,"type":"CREDIT","description":"Payroll","postedAt":"2026-09-25T06:00:00Z"},'
      + '{"id":"T-5005","accountId":"A-2004","amount":845.10,"type":"DEBIT","description":"Airline tickets","postedAt":"2026-08-30T17:45:00Z"}]');
  });

  test('transactions: default limit is 10; limit 0 is empty; account without transactions is absent', async () => {
    const all = JSON.parse((await get('/financial-service/transactions?accountIds=A-2001&accountIds=A-2003')).body) as { id: string }[];
    assert.deepEqual(all.map((t) => t.id), ['T-5001', 'T-5002', 'T-5003']);
    assert.equal((await get('/financial-service/transactions?accountIds=A-2001&limit=0')).body, '[]');
  });

  test('accountIds binds like Spring: comma-separated values split, empty value is an empty list', async () => {
    const res = JSON.parse((await get('/financial-service/balances?accountIds=A-2001,A-2003')).body) as { accountId: string }[];
    assert.deepEqual(res.map((b) => b.accountId), ['A-2001', 'A-2003']);
    assert.equal((await get('/financial-service/balances?accountIds=')).body, '[]');
  });

  test('missing accountIds or a non-integer limit is a 400 problem', async () => {
    for (const path of ['/financial-service/balances', '/financial-service/transactions?accountIds=A-2001&limit=abc',
      '/financial-service/transactions?accountIds=A-2001&limit=-1']) {
      const res = await get(path);
      assert.equal(res.status, 400, path);
      assert.equal(res.type, 'application/problem+json');
      assert.equal((JSON.parse(res.body) as { status: number }).status, 400);
    }
  });

  test('unknown path is 404, non-GET is 405, health is UP', async () => {
    assert.equal((await get('/financial-service/accounts/A-2001/balance')).status, 404);
    const post = await fetch(`${base}/financial-service/balances?accountIds=A-2001`, { method: 'POST' });
    assert.equal(post.status, 405);
    assert.equal(post.headers.get('allow'), 'GET');
    assert.deepEqual(JSON.parse((await get('/health')).body), { status: 'UP' });
  });
});

describe('fault injection', () => {
  test('FINANCIAL_RESPONSE_DELAY_MS delays API responses but not /health', async () => {
    const { server, base } = await start(300);
    try {
      let t0 = performance.now();
      await fetch(`${base}/financial-service/balances?accountIds=A-2001`).then((r) => r.text());
      assert.ok(performance.now() - t0 >= 290);
      t0 = performance.now();
      await fetch(`${base}/health`).then((r) => r.text());
      assert.ok(performance.now() - t0 < 290);
    } finally {
      server.close();
    }
  });
});

describe('toJson', () => {
  test('writes money fields as raw number tokens, leaves other strings quoted', () => {
    assert.equal(toJson({ amount: '1.10', description: '1.10', n: 2 }), '{"amount":1.10,"description":"1.10","n":2}');
  });
});
