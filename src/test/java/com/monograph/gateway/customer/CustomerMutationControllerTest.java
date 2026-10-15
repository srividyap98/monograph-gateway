package com.monograph.gateway.customer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import com.monograph.gateway.error.ConflictException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Mutation resolvers with a mocked port: input binding, edge validation and error mapping. */
@GraphQlTest(CustomerController.class)
class CustomerMutationControllerTest {

    private static final String CREATE = """
            mutation($input: CreateCustomerInput!) {
              createCustomer(input: $input) { id firstName lastName email status }
            }
            """;

    private static final String UPDATE_STATUS = """
            mutation($id: ID!, $status: CustomerStatus!) {
              updateCustomerStatus(id: $id, status: $status) { id status }
            }
            """;

    @Autowired
    GraphQlTester graphQlTester;

    @MockitoBean
    CustomerClient customerClient;

    @Test
    void createBindsInputTrimsItAndReturnsTheServiceResult() {
        given(customerClient.create(new CreateCustomerInput("Katherine", "Johnson", "kj@example.com")))
                .willReturn(new Customer("C-2000", "Katherine", "Johnson", "kj@example.com", CustomerStatus.ACTIVE));

        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of(
                        "firstName", "  Katherine ", "lastName", "Johnson", "email", " kj@example.com "))
                .execute()
                .errors().verify()
                .path("createCustomer.id").entity(String.class).isEqualTo("C-2000")
                .path("createCustomer.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    void blankEmailIsSentAsNull() {
        given(customerClient.create(any())).willReturn(
                new Customer("C-2001", "Mary", "Jackson", null, CustomerStatus.ACTIVE));

        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of("firstName", "Mary", "lastName", "Jackson", "email", "  "))
                .execute()
                .errors().verify();

        verify(customerClient).create(new CreateCustomerInput("Mary", "Jackson", null));
    }

    @Test
    void blankNameIsBadRequestAndNeverReachesTheService() {
        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of("firstName", "   ", "lastName", "Jackson"))
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
                        && e.getMessage().equals("firstName must be 1-100 characters")
                        && e.getPath().equals("createCustomer"))
                .verify();

        verifyNoInteractions(customerClient);
    }

    @Test
    void malformedEmailIsBadRequest() {
        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of("firstName", "Mary", "lastName", "Jackson", "email", "no-at-sign"))
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST && e.getMessage().contains("email"))
                .verify();

        verifyNoInteractions(customerClient);
    }

    @Test
    void missingRequiredInputFieldIsRejectedBySchemaValidation() {
        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of("firstName", "Mary"))
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("lastName"))
                .verify();

        verifyNoInteractions(customerClient);
    }

    @Test
    void conflictFromServiceIsAConflictError() {
        given(customerClient.create(any())).willThrow(new ConflictException("Email ada@example.com is already registered"));

        graphQlTester.document(CREATE)
                .variable("input", java.util.Map.of("firstName", "Ada", "lastName", "L", "email", "ada@example.com"))
                .execute()
                .errors()
                .expect(e -> "CONFLICT".equals(e.getErrorType().toString())
                        && e.getMessage().equals("Email ada@example.com is already registered"))
                .verify();
    }

    @Test
    void updateStatusReturnsTheUpdatedCustomer() {
        given(customerClient.updateStatus("C-1001", CustomerStatus.SUSPENDED)).willReturn(Optional.of(
                new Customer("C-1001", "Ada", "Lovelace", "ada@example.com", CustomerStatus.SUSPENDED)));

        graphQlTester.document(UPDATE_STATUS).variable("id", " C-1001 ").variable("status", "SUSPENDED")
                .execute()
                .errors().verify()
                .path("updateCustomerStatus.status").entity(String.class).isEqualTo("SUSPENDED");
    }

    @Test
    void updateStatusOfUnknownCustomerIsNotFound() {
        given(customerClient.updateStatus("C-9999", CustomerStatus.ACTIVE)).willReturn(Optional.empty());

        graphQlTester.document(UPDATE_STATUS).variable("id", "C-9999").variable("status", "ACTIVE")
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.NOT_FOUND
                        && e.getMessage().equals("Customer C-9999 not found")
                        && e.getPath().equals("updateCustomerStatus"))
                .verify();
    }

    @Test
    void unknownEnumValueIsRejectedBySchemaValidation() {
        graphQlTester.document(UPDATE_STATUS).variable("id", "C-1001").variable("status", "DELETED")
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("DELETED"))
                .verify();

        verifyNoInteractions(customerClient);
    }
}
