/**
 * Seed data, copied from FinancialServiceController in com.monograph.downstream.financial.
 * The two simulators must stay identical; the gateway's NodeFinancialServiceContractTest
 * compares their responses byte for byte.
 *
 * Money is kept as decimal *text* ("10500.00"), never a JS number: a number would lose the
 * scale (10500.00 -> 10500) and could lose precision.
 */

export type Decimal = string & { readonly __decimal: unique symbol };

const DECIMAL = /^-?\d+(\.\d+)?$/;

export function decimal(text: string): Decimal {
  if (!DECIMAL.test(text)) {
    throw new Error(`Not a decimal: ${text}`);
  }
  return text as Decimal;
}

export interface Balance {
  accountId: string;
  available: Decimal;
  ledger: Decimal;
  currency: string;
  /** ISO-8601 instant, UTC, second precision: the format Jackson writes for java.time.Instant. */
  asOf: string;
}

export interface Transaction {
  id: string;
  accountId: string;
  /** Always positive; type gives the direction. */
  amount: Decimal;
  type: 'DEBIT' | 'CREDIT';
  description: string;
  postedAt: string;
}

const AS_OF = '2026-09-28T00:00:00Z';

export const BALANCES: ReadonlyMap<string, Balance> = new Map(
  [
    { accountId: 'A-2001', available: decimal('1250.75'), ledger: decimal('1300.75'), currency: 'USD', asOf: AS_OF },
    { accountId: 'A-2002', available: decimal('10500.00'), ledger: decimal('10500.00'), currency: 'USD', asOf: AS_OF },
    { accountId: 'A-2003', available: decimal('0.00'), ledger: decimal('0.00'), currency: 'USD', asOf: AS_OF },
    { accountId: 'A-2004', available: decimal('0.00'), ledger: decimal('-845.10'), currency: 'USD', asOf: AS_OF },
  ].map((b) => [b.accountId, b] as const),
);

export const TRANSACTIONS: readonly Transaction[] = [
  { id: 'T-5001', accountId: 'A-2001', amount: decimal('50.00'), type: 'DEBIT', description: 'Coffee Roasters', postedAt: '2026-09-27T09:15:00Z' },
  { id: 'T-5002', accountId: 'A-2001', amount: decimal('2500.00'), type: 'CREDIT', description: 'Payroll', postedAt: '2026-09-25T06:00:00Z' },
  { id: 'T-5003', accountId: 'A-2001', amount: decimal('120.40'), type: 'DEBIT', description: 'Utility bill', postedAt: '2026-09-20T12:30:00Z' },
  { id: 'T-5004', accountId: 'A-2002', amount: decimal('500.00'), type: 'CREDIT', description: 'Transfer from checking', postedAt: '2026-09-01T08:00:00Z' },
  { id: 'T-5005', accountId: 'A-2004', amount: decimal('845.10'), type: 'DEBIT', description: 'Airline tickets', postedAt: '2026-08-30T17:45:00Z' },
];
