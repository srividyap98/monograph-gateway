package com.monograph.gateway.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.monograph.gateway.support.DownstreamServices;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full gateway context with the real HTTP clients, talking to the simulated Customer, Account
 * and Financial services over HTTP. GraphQL documents are executed in-process (the incoming
 * HTTP layer is covered by {@code GraphQlHttpEndToEndTest}).
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class CustomerProfileIntegrationTest {

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
    }

    private static final String FULL_PROFILE = """
            query($id: ID!, $limit: Int!) {
              customerProfile(customerId: $id) {
                customer { id firstName lastName status }
                accounts {
                  id type status currency
                  balance { available ledger currency asOf }
                  transactions(limit: $limit) { id amount type description postedAt }
                }
              }
            }
            """;

    @Autowired
    GraphQlTester graphQlTester;

    @Test
    void assemblesCustomerAccountsBalancesAndTransactionsFromThreeServices() {
        graphQlTester.document(FULL_PROFILE).variable("id", "C-1001").variable("limit", 2)
                .execute()
                .errors().verify()
                .path("customerProfile.customer.firstName").entity(String.class).isEqualTo("Ada")
                .path("customerProfile.customer.status").entity(String.class).isEqualTo("ACTIVE")
                .path("customerProfile.accounts[*].id").entityList(String.class).containsExactly("A-2001", "A-2002")
                .path("customerProfile.accounts[0].type").entity(String.class).isEqualTo("CHECKING")
                .path("customerProfile.accounts[0].balance.available").entity(String.class).isEqualTo("1250.75")
                .path("customerProfile.accounts[0].balance.ledger").entity(String.class).isEqualTo("1300.75")
                .path("customerProfile.accounts[0].balance.asOf").entity(String.class).isEqualTo("2026-09-28T00:00:00Z")
                // limit is honoured and ordering is newest first
                .path("customerProfile.accounts[0].transactions[*].id").entityList(String.class)
                .containsExactly("T-5001", "T-5002")
                .path("customerProfile.accounts[0].transactions[1].amount").entity(String.class).isEqualTo("2500.00")
                .path("customerProfile.accounts[0].transactions[1].type").entity(String.class).isEqualTo("CREDIT")
                .path("customerProfile.accounts[1].balance.available").entity(String.class).isEqualTo("10500.00");
    }

    @Test
    void closedAccountWithNoTransactionsIsAnEmptyListNotNull() {
        graphQlTester.document(FULL_PROFILE).variable("id", "C-1002").variable("limit", 10)
                .execute()
                .errors().verify()
                .path("customerProfile.customer.firstName").entity(String.class).isEqualTo("Alan")
                .path("customerProfile.accounts[0].status").entity(String.class).isEqualTo("CLOSED")
                .path("customerProfile.accounts[0].balance.available").entity(String.class).isEqualTo("0.00")
                .path("customerProfile.accounts[0].transactions").entityList(Object.class).hasSize(0);
    }

    @Test
    void unknownCustomerIsNullWithoutErrors() {
        graphQlTester.document(FULL_PROFILE).variable("id", "C-9999").variable("limit", 10)
                .execute()
                .errors().verify()
                .path("customerProfile").valueIsNull();
    }

    @Test
    void existingCustomerQueryStillWorksAgainstTheCustomerService() {
        graphQlTester.document("{ customer(id: \"C-1002\") { lastName email status } }")
                .execute()
                .errors().verify()
                .path("customer.lastName").entity(String.class).isEqualTo("Turing")
                .path("customer.email").valueIsNull()
                .path("customer.status").entity(String.class).isEqualTo("INACTIVE");
    }

    @Test
    void blankCustomerIdIsBadRequestBeforeAnyDownstreamCall() {
        graphQlTester.document(FULL_PROFILE).variable("id", " ").variable("limit", 10)
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST && e.getPath().equals("customerProfile"))
                .verify()
                .path("customerProfile").valueIsNull();
    }

    /**
     * {@code transactions} is {@code [Transaction!]!} and {@code accounts} is {@code [Account!]!}, so an
     * error there nulls every non-null parent up to the first nullable field: {@code customerProfile}.
     * Stage 9 revisits this nullability trade-off for downstream failures.
     */
    @Test
    void invalidTransactionLimitNullsUpToNearestNullableParent() {
        graphQlTester.document(FULL_PROFILE).variable("id", "C-1001").variable("limit", 51)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors)
                        .allSatisfy(e -> {
                            assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                            assertThat(e.getMessage()).isEqualTo("limit must be between 1 and 50");
                        })
                        .extracting(e -> e.getParsedPath())
                        .contains(List.of("customerProfile", "accounts", 0, "transactions")))
                .path("customerProfile").valueIsNull();
    }
}
