package com.monograph.gateway.customer;

/**
 * Gateway-side view of a customer. Component names match the fields of the
 * GraphQL {@code Customer} type, so Spring for GraphQL resolves them by property name.
 */
public record Customer(
        String id,
        String firstName,
        String lastName,
        String email,
        CustomerStatus status) {
}
