package com.monograph.gateway.financial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.monograph.gateway.account.Account;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoader;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;

/**
 * Resolves the fields that {@code financial.graphqls} adds to {@code Account}. The Financial domain
 * extends a type owned by the Account domain: the source {@link Account} parent object supplies
 * the key ({@code account.id()}) for the Financial service call.
 */
@Controller
public class FinancialController {

    static final int MAX_TRANSACTIONS = 50;

    static final String TRANSACTIONS_LOADER = "accountTransactions";

    /**
     * DataLoader key for {@code Account.transactions(limit)}. The argument is part of the key:
     * two selections with different limits must not share a cached result.
     */
    record TransactionsKey(String accountId, int limit) {
    }

    private final FinancialClient financialClient;

    public FinancialController(FinancialClient financialClient, BatchLoaderRegistry registry) {
        this.financialClient = financialClient;
        registry.<TransactionsKey, List<Transaction>>forName(TRANSACTIONS_LOADER)
                .registerMappedBatchLoader((keys, env) -> Mono.fromCallable(() -> loadTransactions(keys)));
    }

    /** {@code Account.balance} for every Account in the response, in one Financial-service call. */
    @BatchMapping(typeName = "Account")
    public Map<Account, Balance> balance(List<Account> accounts) {
        Map<String, Balance> byAccountId = financialClient.findBalances(accounts.stream().map(Account::id).toList());
        Map<Account, Balance> result = new HashMap<>();
        // balance is nullable: an account missing from the map resolves to null.
        accounts.forEach(a -> {
            Balance balance = byAccountId.get(a.id());
            if (balance != null) {
                result.put(a, balance);
            }
        });
        return result;
    }

    /**
     * {@code Account.transactions(limit: Int! = 10)}. {@code @BatchMapping} cannot take field
     * arguments (its key is only the parent object), so this uses an explicitly registered
     * DataLoader whose key carries the limit.
     */
    @SchemaMapping(typeName = "Account")
    public CompletableFuture<List<Transaction>> transactions(Account account, @Argument int limit,
            DataFetchingEnvironment env) {
        if (limit < 1 || limit > MAX_TRANSACTIONS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_TRANSACTIONS);
        }
        DataLoader<TransactionsKey, List<Transaction>> loader = env.getDataLoader(TRANSACTIONS_LOADER);
        return loader.load(new TransactionsKey(account.id(), limit));
    }

    /** One downstream call per distinct limit in the batch (usually exactly one). */
    private Map<TransactionsKey, List<Transaction>> loadTransactions(Set<TransactionsKey> keys) {
        Map<TransactionsKey, List<Transaction>> result = new HashMap<>();
        keys.stream()
                .collect(Collectors.groupingBy(TransactionsKey::limit,
                        Collectors.mapping(TransactionsKey::accountId, Collectors.toSet())))
                .forEach((limit, accountIds) -> {
                    Map<String, List<Transaction>> byAccountId = financialClient.findTransactions(accountIds, limit);
                    accountIds.forEach(id -> result.put(new TransactionsKey(id, limit),
                            byAccountId.getOrDefault(id, List.of())));
                });
        return result;
    }
}
