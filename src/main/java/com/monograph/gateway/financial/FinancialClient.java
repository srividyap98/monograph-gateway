package com.monograph.gateway.financial;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Port to the Financial domain. Batch-only: one downstream call per method invocation. */
public interface FinancialClient {

    /** Keyed by account id; accounts without a balance are absent. */
    Map<String, Balance> findBalances(Collection<String> accountIds);

    /**
     * The newest {@code limitPerAccount} transactions of each account, newest first, keyed by
     * account id. Accounts without transactions are absent.
     */
    Map<String, List<Transaction>> findTransactions(Collection<String> accountIds, int limitPerAccount);
}
