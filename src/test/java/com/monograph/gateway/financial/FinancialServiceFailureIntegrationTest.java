package com.monograph.gateway.financial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.monograph.gateway.support.DownstreamServices;
import com.monograph.gateway.support.StubHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full gateway with real Customer and Account services and a misbehaving Financial service:
 * what a GraphQL client sees when the Financial dependency fails. Errors carry
 * {@code DOWNSTREAM_UNAVAILABLE}; how much of the response survives is decided by the schema's
 * nullability, which this change deliberately leaves as it was.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class FinancialServiceFailureIntegrationTest {

    private static final StubHttpServer FINANCIAL = new StubHttpServer();

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
        registry.add("monograph.downstream.financial.base-url", FINANCIAL::baseUrl);
        registry.add("monograph.downstream.financial.read-timeout", () -> "300ms");
    }

    @AfterAll
    static void stop() {
        FINANCIAL.close();
    }

    @Autowired
    GraphQlTester graphQlTester;

    private static boolean isDownstreamUnavailable(ResponseError e) {
        return "DOWNSTREAM_UNAVAILABLE".equals(e.getErrorType().toString());
    }

    @Test
    void timedOutBalanceIsNullWithAnErrorWhileAccountsStillResolve() {
        FINANCIAL.respond(200, "[]").delay(Duration.ofSeconds(3));

        graphQlTester.document("{ customer(id: \"C-1001\") { firstName accounts { id balance { available } } } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors)
                        .allSatisfy(e -> {
                            assertThat(isDownstreamUnavailable(e)).isTrue();
                            assertThat(e.getMessage()).isEqualTo("Financial service timed out");
                        })
                        .extracting(e -> e.getParsedPath())
                        // one batched call failed, so each account's balance carries the error
                        .containsExactlyInAnyOrder(
                                List.of("customer", "accounts", 0, "balance"),
                                List.of("customer", "accounts", 1, "balance")))
                .path("customer.firstName").entity(String.class).isEqualTo("Ada")
                .path("customer.accounts[*].id").entityList(String.class).containsExactly("A-2001", "A-2002")
                .path("customer.accounts[0].balance").valueIsNull();
    }

    /** {@code transactions} is non-null, so its error nulls up to the nearest nullable parent. */
    @Test
    void failedTransactionsNullTheProfileBecauseTheFieldIsNonNull() {
        FINANCIAL.respond(503, "");

        graphQlTester.document("{ customerProfile(customerId: \"C-1003\") { accounts { transactions { id } } } }")
                .execute()
                .errors()
                .expect(e -> isDownstreamUnavailable(e)
                        && e.getMessage().equals("Financial service responded with HTTP 503")
                        && e.getParsedPath().equals(List.of("customerProfile", "accounts", 0, "transactions")))
                .verify()
                .path("customerProfile").valueIsNull();
    }

    @Test
    void selectionsWithoutMoneyFieldsAreUnaffectedByTheOutage() {
        FINANCIAL.respond(503, "");

        graphQlTester.document("{ customerProfile(customerId: \"C-1001\") { customer { lastName } accounts { type } } }")
                .execute()
                .errors().verify()
                .path("customerProfile.customer.lastName").entity(String.class).isEqualTo("Lovelace")
                .path("customerProfile.accounts[*].type").entityList(String.class)
                .containsExactly("CHECKING", "SAVINGS");
    }
}
