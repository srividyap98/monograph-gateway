package com.monograph.gateway.financial;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.monograph.gateway.downstream.DownstreamHttp;
import com.monograph.gateway.downstream.DownstreamProperties;
import com.monograph.gateway.error.DownstreamUnavailableException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link FinancialClient} backed by the Financial service's REST API. Both calls are reads with no
 * domain-specific error statuses, so every failure becomes a {@link DownstreamUnavailableException}
 * (GraphQL {@code DOWNSTREAM_UNAVAILABLE}). The service may be the Java simulator or the Node one
 * in {@code services/financial-service-node}; only the base URL tells them apart.
 */
@Component
class HttpFinancialClient implements FinancialClient {

    static final String SERVICE_NAME = "Financial service";

    private static final ParameterizedTypeReference<List<Balance>> BALANCE_LIST = new ParameterizedTypeReference<>() {
    };

    private static final ParameterizedTypeReference<List<Transaction>> TRANSACTION_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    HttpFinancialClient(RestClient.Builder builder, DownstreamProperties downstream) {
        this.restClient = DownstreamHttp.restClient(builder, downstream.financial());
    }

    /**
     * GET /balances?accountIds=..&accountIds=..
     *
     * @throws DownstreamUnavailableException on timeout, connection failure, error status or unreadable body
     */
    @Override
    public Map<String, Balance> findBalances(Collection<String> accountIds) {
        List<Balance> balances = DownstreamHttp.call(SERVICE_NAME, () -> restClient.get()
                .uri(uri -> uri.path("/balances").queryParam("accountIds", accountIds).build())
                .retrieve()
                .body(BALANCE_LIST));
        return balances == null ? Map.of()
                : balances.stream().collect(Collectors.toMap(Balance::accountId, Function.identity()));
    }

    /**
     * GET /transactions?accountIds=..&limit=N; order within each account is preserved by groupingBy.
     *
     * @throws DownstreamUnavailableException on timeout, connection failure, error status or unreadable body
     */
    @Override
    public Map<String, List<Transaction>> findTransactions(Collection<String> accountIds, int limitPerAccount) {
        List<Transaction> transactions = DownstreamHttp.call(SERVICE_NAME, () -> restClient.get()
                .uri(uri -> uri.path("/transactions")
                        .queryParam("accountIds", accountIds)
                        .queryParam("limit", limitPerAccount)
                        .build())
                .retrieve()
                .body(TRANSACTION_LIST));
        return transactions == null ? Map.of()
                : transactions.stream().collect(Collectors.groupingBy(Transaction::accountId));
    }
}
