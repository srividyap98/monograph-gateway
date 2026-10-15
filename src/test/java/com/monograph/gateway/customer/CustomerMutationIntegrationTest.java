package com.monograph.gateway.customer;

import java.util.Map;
import java.util.UUID;

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
 * Mutations through the real gateway into the simulated Customer service over HTTP.
 * The downstream store is shared by every test in the JVM, so each test creates its own
 * customer (with a unique email) instead of modifying the seeded C-1001..C-1003.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class CustomerMutationIntegrationTest {

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        DownstreamServices.register(registry);
    }

    @Autowired
    GraphQlTester graphQlTester;

    private String createCustomer(String email) {
        return graphQlTester.document("""
                        mutation($input: CreateCustomerInput!) { createCustomer(input: $input) { id } }
                        """)
                .variable("input", Map.of("firstName", "Katherine", "lastName", "Johnson", "email", email))
                .execute()
                .errors().verify()
                .path("createCustomer.id").entity(String.class).get();
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @Test
    void createdCustomerIsReadableThroughQueriesAndStartsActive() {
        String email = uniqueEmail();
        String id = createCustomer(email);

        graphQlTester.document("""
                        query($id: ID!) {
                          customer(id: $id) { firstName email status }
                          customerProfile(customerId: $id) { accounts { id } }
                        }
                        """)
                .variable("id", id)
                .execute()
                .errors().verify()
                .path("customer.firstName").entity(String.class).isEqualTo("Katherine")
                .path("customer.email").entity(String.class).isEqualTo(email)
                // initial status is decided by the Customer service, not the gateway
                .path("customer.status").entity(String.class).isEqualTo("ACTIVE")
                .path("customerProfile.accounts").entityList(Object.class).hasSize(0);
    }

    @Test
    void duplicateEmailIsRejectedByTheServiceAsConflict() {
        graphQlTester.document("""
                        mutation { createCustomer(input: {firstName: "Ada", lastName: "Again", email: "ADA@example.com"}) { id } }
                        """)
                .execute()
                .errors()
                .expect(e -> "CONFLICT".equals(e.getErrorType().toString())
                        && e.getMessage().equals("Email ADA@example.com is already registered")
                        && e.getPath().equals("createCustomer"))
                .verify();
    }

    @Test
    void statusUpdateIsPersistedDownstream() {
        String id = createCustomer(uniqueEmail());

        graphQlTester.document("mutation($id: ID!) { updateCustomerStatus(id: $id, status: SUSPENDED) { id status } }")
                .variable("id", id)
                .execute()
                .errors().verify()
                .path("updateCustomerStatus.status").entity(String.class).isEqualTo("SUSPENDED");

        graphQlTester.document("query($id: ID!) { customer(id: $id) { status } }")
                .variable("id", id)
                .execute()
                .errors().verify()
                .path("customer.status").entity(String.class).isEqualTo("SUSPENDED");
    }

    @Test
    void statusUpdateOfUnknownCustomerIsNotFound() {
        graphQlTester.document("mutation { updateCustomerStatus(id: \"C-9999\", status: ACTIVE) { id } }")
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.NOT_FOUND)
                .verify();
    }

    /**
     * GraphQL spec: root mutation fields execute serially, in document order. Three
     * conflicting updates in one request therefore always leave the last one in place.
     */
    @Test
    void rootMutationFieldsRunSeriallyInDocumentOrder() {
        String id = createCustomer(uniqueEmail());

        graphQlTester.document("""
                        mutation($id: ID!) {
                          first:  updateCustomerStatus(id: $id, status: SUSPENDED) { status }
                          second: updateCustomerStatus(id: $id, status: ACTIVE)    { status }
                          third:  updateCustomerStatus(id: $id, status: INACTIVE)  { status }
                        }
                        """)
                .variable("id", id)
                .execute()
                .errors().verify()
                .path("first.status").entity(String.class).isEqualTo("SUSPENDED")
                .path("second.status").entity(String.class).isEqualTo("ACTIVE")
                .path("third.status").entity(String.class).isEqualTo("INACTIVE");

        graphQlTester.document("query($id: ID!) { customer(id: $id) { status } }")
                .variable("id", id)
                .execute()
                .path("customer.status").entity(String.class).isEqualTo("INACTIVE");
    }
}
