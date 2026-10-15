package com.monograph.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.monograph.gateway.support.DownstreamServices;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the full application on a random port and calls it over real HTTP,
 * exactly as the customer-service platform would. The gateway in turn calls the
 * simulated downstream services over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GraphQlHttpEndToEndTest {

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/graphql"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/graphql-response+json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void customerQueryOverHttp() throws Exception {
        HttpResponse<String> response = post("""
                {"query":"query($id: ID!){ customer(id: $id){ id firstName status } }",
                 "variables":{"id":"C-1001"}}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("{\"data\":{\"customer\":{\"id\":\"C-1001\",\"firstName\":\"Ada\",\"status\":\"ACTIVE\"}}}");
    }

    @Test
    void customerProfileOverHttpAggregatesThreeServices() throws Exception {
        HttpResponse<String> response = post("""
                {"query":"{ customerProfile(customerId: \\"C-1003\\") { customer { firstName } accounts { id balance { ledger } transactions { description } } } }"}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("""
                {"data":{"customerProfile":{"customer":{"firstName":"Grace"},\
                "accounts":[{"id":"A-2004","balance":{"ledger":"-845.10"},\
                "transactions":[{"description":"Airline tickets"}]}]}}}""");
    }

    @Test
    void mutationOverHttp() throws Exception {
        HttpResponse<String> response = post("""
                {"query":"mutation($in: CreateCustomerInput!){ createCustomer(input: $in){ firstName status } }",
                 "variables":{"in":{"firstName":"Dorothy","lastName":"Vaughan"}}}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("{\"data\":{\"createCustomer\":{\"firstName\":\"Dorothy\",\"status\":\"ACTIVE\"}}}");
    }

    @Test
    void validationErrorOverHttpUsesGraphQlResponseMediaType() throws Exception {
        HttpResponse<String> response = post("{\"query\":\"{ customer { id } }\"}");

        // GraphQL-over-HTTP spec: with application/graphql-response+json, a request that
        // fails validation (no data) is reported with a 4xx status.
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("ValidationError");
    }

    @Test
    void malformedJsonBodyIsRejected() throws Exception {
        HttpResponse<String> response = post("{not json");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void schemaIsPublished() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/graphql/schema")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("customer(id: ID!): Customer", "enum CustomerStatus");
    }
}
