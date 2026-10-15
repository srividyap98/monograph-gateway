package com.monograph.gateway.customer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.monograph.gateway.downstream.DownstreamHttp;
import com.monograph.gateway.downstream.DownstreamProperties;
import com.monograph.gateway.error.ConflictException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** {@link CustomerClient} backed by the Customer service's REST API. */
@Component
class HttpCustomerClient implements CustomerClient {

    private static final ParameterizedTypeReference<List<Customer>> CUSTOMER_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    /** {@code RestClient.Builder} is a prototype bean: each client gets its own, preconfigured by Boot. */
    HttpCustomerClient(RestClient.Builder builder, DownstreamProperties downstream) {
        this.restClient = DownstreamHttp.restClient(builder, downstream.customer());
    }

    /** GET /customers?ids=..&ids=..; unknown ids are simply missing from the response. */
    @Override
    public Map<String, Customer> findByIds(Collection<String> ids) {
        List<Customer> found = restClient.get()
                .uri(uri -> uri.path("/customers").queryParam("ids", ids).build())
                .retrieve()
                .body(CUSTOMER_LIST);
        return found == null ? Map.of()
                : found.stream().collect(Collectors.toMap(Customer::id, Function.identity()));
    }

    /** POST /customers. Not idempotent: never retry blindly, or a timeout could create two customers. */
    @Override
    public Customer create(CreateCustomerInput input) {
        try {
            return restClient.post()
                    .uri("/customers")
                    .body(input)
                    .retrieve()
                    .body(Customer.class);
        }
        catch (HttpClientErrorException.Conflict conflict) {
            throw new ConflictException(detail(conflict, "Customer conflicts with an existing customer"));
        }
    }

    /** PATCH /customers/{id}/status. Idempotent: setting the same status twice has the same effect. */
    @Override
    public Optional<Customer> updateStatus(String id, CustomerStatus status) {
        try {
            return Optional.ofNullable(restClient.patch()
                    .uri("/customers/{id}/status", id)
                    .body(Map.of("status", status))
                    .retrieve()
                    .body(Customer.class));
        }
        catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        }
    }

    /** The service explains rejections in an RFC 9457 problem body; relay its detail if present. */
    private static String detail(HttpClientErrorException ex, String fallback) {
        ProblemDetail problem = ex.getResponseBodyAs(ProblemDetail.class);
        return problem != null && problem.getDetail() != null ? problem.getDetail() : fallback;
    }
}
