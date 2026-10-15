package com.monograph.gateway.customer;

/** Validation shared by every resolver that accepts a customer id argument. */
public final class CustomerIds {

    public static final int MAX_LENGTH = 64;

    private CustomerIds() {
    }

    /**
     * Trims and validates a client-supplied id before it is sent downstream.
     *
     * @throws IllegalArgumentException mapped to a GraphQL BAD_REQUEST by {@code GraphQlExceptionAdvice}
     */
    public static String normalize(String id) {
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Customer id must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Customer id must be at most " + MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
