package com.monograph.gateway.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;

/**
 * GraphQL slice test: loads the schema, CustomerController and @ControllerAdvice,
 * and executes documents in-process (no HTTP server).
 */
@GraphQlTest(CustomerController.class)
@Import(InMemoryCustomerClient.class)
class CustomerControllerTest {

    private static final String CUSTOMER_QUERY = """
            query($id: ID!) {
              customer(id: $id) { id firstName lastName email status }
            }
            """;

    @Autowired
    GraphQlTester graphQlTester;

    @Nested
    class Positive {

        @Test
        void returnsAllFieldsForKnownCustomer() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "C-1001")
                    .execute()
                    .errors().verify()
                    .path("customer.id").entity(String.class).isEqualTo("C-1001")
                    .path("customer.firstName").entity(String.class).isEqualTo("Ada")
                    .path("customer.lastName").entity(String.class).isEqualTo("Lovelace")
                    .path("customer.email").entity(String.class).isEqualTo("ada@example.com")
                    .path("customer.status").entity(String.class).isEqualTo("ACTIVE");
        }

        @Test
        void returnsOnlyRequestedFields() {
            graphQlTester.document("{ customer(id: \"C-1003\") { firstName } }")
                    .execute()
                    .errors().verify()
                    .path("customer.firstName").entity(String.class).isEqualTo("Grace")
                    .path("customer.id").pathDoesNotExist()
                    .path("customer.status").pathDoesNotExist();
        }

        @Test
        void nullableEmailIsReturnedAsNull() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "C-1002")
                    .execute()
                    .errors().verify()
                    .path("customer.email").valueIsNull()
                    .path("customer.status").entity(String.class).isEqualTo("INACTIVE");
        }

        @Test
        void aliasesAreSupported() {
            graphQlTester.document("""
                    { a: customer(id: "C-1001") { firstName }
                      b: customer(id: "C-1002") { firstName } }
                    """)
                    .execute()
                    .errors().verify()
                    .path("a.firstName").entity(String.class).isEqualTo("Ada")
                    .path("b.firstName").entity(String.class).isEqualTo("Alan");
        }
    }

    @Nested
    class Negative {

        @Test
        void unknownIdReturnsNullWithoutErrors() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "C-9999")
                    .execute()
                    .errors().verify()
                    .path("customer").valueIsNull();
        }

        @Test
        void missingRequiredArgumentIsValidationError() {
            graphQlTester.document("{ customer { id } }")
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == graphql.ErrorType.ValidationError)
                    .verify();
        }

        @Test
        void unknownFieldIsValidationError() {
            graphQlTester.document("{ customer(id: \"C-1001\") { id ssn } }")
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == graphql.ErrorType.ValidationError
                            && e.getMessage().contains("ssn"))
                    .verify();
        }

        @Test
        void nullVariableForNonNullIdIsRejected() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", null)
                    .execute()
                    .errors()
                    .satisfy(errors -> assertThat(errors).hasSize(1)
                            .first().satisfies(e -> assertThat(e.getMessage()).contains("id")));
        }

        @Test
        void malformedDocumentIsSyntaxError() {
            graphQlTester.document("{ customer(id: \"C-1001\") { id ")
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == graphql.ErrorType.InvalidSyntax)
                    .verify();
        }

        @Test
        void blankIdIsBadRequest() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "   ")
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
                            && e.getMessage().equals("Customer id must not be blank")
                            && e.getPath().equals("customer"))
                    .verify()
                    .path("customer").valueIsNull();
        }
    }

    @Nested
    class Boundary {

        @Test
        void emptyStringIdIsBadRequest() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "")
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST)
                    .verify();
        }

        @Test
        void surroundingWhitespaceIsTrimmed() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "  C-1001  ")
                    .execute()
                    .errors().verify()
                    .path("customer.id").entity(String.class).isEqualTo("C-1001");
        }

        @Test
        void idAtMaxLengthIsAccepted() {
            String id = "X".repeat(CustomerIds.MAX_LENGTH);
            graphQlTester.document(CUSTOMER_QUERY).variable("id", id)
                    .execute()
                    .errors().verify()
                    .path("customer").valueIsNull();
        }

        @Test
        void idOverMaxLengthIsBadRequest() {
            String id = "X".repeat(CustomerIds.MAX_LENGTH + 1);
            graphQlTester.document(CUSTOMER_QUERY).variable("id", id)
                    .execute()
                    .errors()
                    .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
                            && e.getMessage().contains("at most 64"))
                    .verify();
        }

        @Test
        void idIsCaseSensitive() {
            graphQlTester.document(CUSTOMER_QUERY).variable("id", "c-1001")
                    .execute()
                    .errors().verify()
                    .path("customer").valueIsNull();
        }

        @Test
        void integerLiteralIsCoercedToId() {
            graphQlTester.document("{ customer(id: 1001) { id } }")
                    .execute()
                    .errors().verify()
                    .path("customer").valueIsNull();
        }
    }
}
