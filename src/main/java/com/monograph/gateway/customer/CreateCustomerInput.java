package com.monograph.gateway.customer;

/**
 * Binds the GraphQL {@code CreateCustomerInput} (by component name) and doubles as the JSON
 * request body sent to the Customer service.
 */
public record CreateCustomerInput(String firstName, String lastName, String email) {

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_EMAIL_LENGTH = 254;

    /**
     * Cheap shape checks at the edge, so obviously bad input never costs a downstream call.
     * The Customer service still owns the business rules (e.g. email uniqueness).
     *
     * @throws IllegalArgumentException mapped to a GraphQL BAD_REQUEST
     */
    public CreateCustomerInput normalized() {
        String email = this.email == null || this.email.isBlank() ? null : this.email.trim();
        if (email != null && (email.length() > MAX_EMAIL_LENGTH || email.indexOf('@') < 1)) {
            throw new IllegalArgumentException("email must be a valid address");
        }
        return new CreateCustomerInput(name("firstName", firstName), name("lastName", lastName), email);
    }

    private static String name(String field, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(field + " must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }
}
