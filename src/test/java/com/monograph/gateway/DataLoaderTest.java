package com.monograph.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.monograph.gateway.support.DownstreamCallRecorder;
import com.monograph.gateway.support.DownstreamServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Counts real HTTP requests from the gateway to the downstream services.
 *
 * <p>Measured before DataLoader (one resolver call per parent object), for
 * {@code customers(ids: [C-1001, C-1002, C-1003]) { accounts { balance transactions } }}:
 * <pre>
 * 3 x GET /customers/{id}                 one per customer
 * 3 x GET /accounts?customerId=           one per customer
 * 4 x GET /accounts/{id}/balance          one per account
 * 4 x GET /accounts/{id}/transactions     one per account
 * = 14 calls, growing as 2C + 2A with customers C and accounts A.
 * </pre>
 * Three aliased {@code customer} lookups (two of them for the same id) took 3 calls.
 * With DataLoader: 4 and 1, independent of how many customers or accounts are involved.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@Import(DownstreamCallRecorder.class)
class DataLoaderTest {

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
    }

    private static final String CUSTOMERS_WITH_MONEY = """
            query($ids: [ID!]!) {
              customers(ids: $ids) {
                firstName
                accounts { id balance { available } transactions(limit: 5) { id } }
              }
            }
            """;

    @Autowired
    GraphQlTester graphQlTester;

    @Autowired
    DownstreamCallRecorder recorder;

    @BeforeEach
    void reset() {
        recorder.reset();
    }

    /** "GET /path?query" -> "GET /path", for order- and parameter-independent assertions. */
    private List<String> endpoints() {
        return recorder.calls().stream().map(call -> call.replaceFirst("\\?.*", "")).toList();
    }

    @Test
    void oneCallPerServiceEndpointInsteadOfOnePerObject() {
        graphQlTester.document(CUSTOMERS_WITH_MONEY)
                .variable("ids", List.of("C-1001", "C-1002", "C-1003"))
                .execute()
                .errors().verify()
                .path("customers[*].firstName").entityList(String.class).containsExactly("Ada", "Alan", "Grace")
                .path("customers[0].accounts[*].id").entityList(String.class).containsExactly("A-2001", "A-2002")
                .path("customers[0].accounts[0].balance.available").entity(String.class).isEqualTo("1250.75")
                .path("customers[0].accounts[0].transactions[*].id").entityList(String.class)
                .containsExactly("T-5001", "T-5002", "T-5003")
                .path("customers[2].accounts[0].transactions[*].id").entityList(String.class)
                .containsExactly("T-5005");

        assertThat(endpoints()).containsExactlyInAnyOrder(
                "GET /customer-service/customers",
                "GET /account-service/accounts",
                "GET /financial-service/balances",
                "GET /financial-service/transactions");
        assertThat(recorder.calls().getFirst()).contains("ids=C-1001", "ids=C-1002", "ids=C-1003");
    }

    @Test
    void callCountDoesNotGrowWithTheNumberOfCustomers() {
        graphQlTester.document(CUSTOMERS_WITH_MONEY).variable("ids", List.of("C-1001")).execute().errors().verify();
        int forOneCustomer = recorder.calls().size();
        recorder.reset();

        graphQlTester.document(CUSTOMERS_WITH_MONEY)
                .variable("ids", List.of("C-1001", "C-1002", "C-1003")).execute().errors().verify();

        assertThat(recorder.calls()).hasSize(forOneCustomer).hasSize(4);
    }

    @Test
    void duplicateLookupsInOneRequestAreFetchedOnce() {
        graphQlTester.document("""
                        { a: customer(id: "C-1001") { firstName }
                          b: customer(id: "C-1001") { lastName }
                          c: customer(id: "C-1002") { firstName }
                          d: customerProfile(customerId: "C-1001") { customer { email } } }
                        """)
                .execute()
                .errors().verify()
                .path("a.firstName").entity(String.class).isEqualTo("Ada")
                .path("b.lastName").entity(String.class).isEqualTo("Lovelace")
                .path("c.firstName").entity(String.class).isEqualTo("Alan")
                .path("d.customer.email").entity(String.class).isEqualTo("ada@example.com");

        // Four lookups across two different root fields -> one request, each id once.
        assertThat(recorder.calls()).hasSize(1);
        assertThat(recorder.calls().getFirst()).containsOnlyOnce("ids=C-1001").containsOnlyOnce("ids=C-1002");
    }

    @Test
    void unknownAndDuplicateIdsKeepPositionInTheResult() {
        graphQlTester.document("{ customers(ids: [\"C-1002\", \"C-9999\", \"C-1002\"]) { id } }")
                .execute()
                .errors().verify()
                .path("customers[0].id").entity(String.class).isEqualTo("C-1002")
                .path("customers[1]").valueIsNull()
                .path("customers[2].id").entity(String.class).isEqualTo("C-1002");

        assertThat(recorder.calls()).hasSize(1);
    }

    @Test
    void differentFieldArgumentsAreDifferentCacheKeys() {
        graphQlTester.document("""
                        { customer(id: "C-1001") { accounts {
                            latest: transactions(limit: 1) { id }
                            recent: transactions(limit: 10) { id } } } }
                        """)
                .execute()
                .errors().verify()
                .path("customer.accounts[0].latest[*].id").entityList(String.class).containsExactly("T-5001")
                .path("customer.accounts[0].recent[*].id").entityList(String.class)
                .containsExactly("T-5001", "T-5002", "T-5003");

        // One transactions call per distinct limit, each still batched across both accounts.
        assertThat(recorder.calls()).filteredOn(c -> c.startsWith("GET /financial-service/transactions"))
                .hasSize(2)
                .allSatisfy(c -> assertThat(c).contains("accountIds=A-2001", "accountIds=A-2002"));
    }

    @Test
    void cacheIsPerRequestNotShared() {
        String query = "{ customer(id: \"C-1003\") { firstName } }";
        graphQlTester.document(query).execute().errors().verify();
        graphQlTester.document(query).execute().errors().verify();

        // A cross-request cache would serve stale data after a mutation; DataLoader never does that.
        assertThat(recorder.calls()).hasSize(2);
    }

    @Test
    void tooManyIdsIsBadRequest() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 51).mapToObj(i -> "C-" + i).toList();
        graphQlTester.document("query($ids: [ID!]!) { customers(ids: $ids) { id } }")
                .variable("ids", ids)
                .execute()
                .errors()
                .expect(e -> e.getMessage().equals("At most 50 ids per request"))
                .verify();

        assertThat(recorder.calls()).isEmpty();
    }
}
