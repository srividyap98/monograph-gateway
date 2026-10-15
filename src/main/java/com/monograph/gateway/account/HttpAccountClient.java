package com.monograph.gateway.account;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.monograph.gateway.downstream.DownstreamHttp;
import com.monograph.gateway.downstream.DownstreamProperties;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** {@link AccountClient} backed by the Account service's REST API. */
@Component
class HttpAccountClient implements AccountClient {

    /** Captures {@code List<Account>} at runtime; {@code List.class} alone would lose the element type. */
    private static final ParameterizedTypeReference<List<Account>> ACCOUNT_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    HttpAccountClient(RestClient.Builder builder, DownstreamProperties downstream) {
        this.restClient = DownstreamHttp.restClient(builder, downstream.account());
    }

    /** GET /accounts?customerIds=..&customerIds=..; the service returns a flat list we group here. */
    @Override
    public Map<String, List<Account>> findByCustomerIds(Collection<String> customerIds) {
        List<Account> accounts = restClient.get()
                .uri(uri -> uri.path("/accounts").queryParam("customerIds", customerIds).build())
                .retrieve()
                .body(ACCOUNT_LIST);
        return accounts == null ? Map.of()
                : accounts.stream().collect(Collectors.groupingBy(Account::customerId));
    }
}
