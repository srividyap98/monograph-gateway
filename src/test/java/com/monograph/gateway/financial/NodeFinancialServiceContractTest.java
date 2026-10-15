package com.monograph.gateway.financial;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.monograph.gateway.support.DownstreamServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cross-language contract check for {@code services/financial-service-node}. Skipped unless the Node
 * service is running and its URL is exported:
 *
 * <pre>
 * FINANCIAL_SERVICE_NODE_URL=http://localhost:8091/financial-service mvn test -Dtest=NodeFinancialServiceContractTest
 * </pre>
 *
 * It compares the Node responses byte for byte with the Java simulator's, then runs the gateway
 * against the Node service.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@EnabledIfEnvironmentVariable(named = NodeFinancialServiceContractTest.NODE_URL, matches = "https?://.+")
class NodeFinancialServiceContractTest {

    static final String NODE_URL = "FINANCIAL_SERVICE_NODE_URL";

    /** Every request shape the gateway sends, plus the edge cases of the parameter binding. */
    private static final List<String> REQUESTS = List.of(
            "/balances?accountIds=A-2001&accountIds=A-2002&accountIds=A-2003&accountIds=A-2004",
            "/balances?accountIds=A-2002&accountIds=A-9999&accountIds=A-2002",
            "/balances?accountIds=",
            "/transactions?accountIds=A-2001&accountIds=A-2002&accountIds=A-2003&accountIds=A-2004&limit=10",
            "/transactions?accountIds=A-2004&accountIds=A-2001&limit=2",
            "/transactions?accountIds=A-2001",
            "/transactions?accountIds=A-2001,A-2002&limit=1",
            "/transactions?accountIds=A-2001&limit=0");

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
        registry.add("monograph.downstream.financial.base-url", () -> System.getenv(NODE_URL));
    }

    @Autowired
    GraphQlTester graphQlTester;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void nodeResponsesAreIdenticalToTheJavaSimulator() throws Exception {
        String java = DownstreamServices.baseUrl() + "/financial-service";
        String node = System.getenv(NODE_URL);
        for (String request : REQUESTS) {
            HttpResponse<String> expected = get(java + request);
            HttpResponse<String> actual = get(node + request);
            assertThat(actual.statusCode()).as(request).isEqualTo(expected.statusCode()).isEqualTo(200);
            assertThat(actual.headers().firstValue("Content-Type")).as(request).hasValue("application/json");
            assertThat(actual.body()).as(request).isEqualTo(expected.body());
        }
    }

    @Test
    void missingRequiredParameterIsABadRequestInBoth() throws Exception {
        assertThat(get(System.getenv(NODE_URL) + "/balances").statusCode())
                .isEqualTo(get(DownstreamServices.baseUrl() + "/financial-service/balances").statusCode())
                .isEqualTo(400);
    }

    @Test
    void gatewayServesMoneyFromTheNodeServiceWithItsScale() {
        graphQlTester.document("""
                        { customers(ids: ["C-1001", "C-1003"]) { accounts {
                            id balance { available ledger asOf } transactions(limit: 2) { amount type postedAt } } } }
                        """)
                .execute()
                .errors().verify()
                .path("customers[0].accounts[1].balance.available").entity(String.class).isEqualTo("10500.00")
                .path("customers[0].accounts[0].balance.asOf").entity(String.class).isEqualTo("2026-09-28T00:00:00Z")
                .path("customers[0].accounts[0].transactions[*].amount").entityList(String.class)
                .containsExactly("50.00", "2500.00")
                .path("customers[1].accounts[0].balance.ledger").entity(String.class).isEqualTo("-845.10");
    }
}
