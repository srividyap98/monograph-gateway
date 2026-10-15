package com.monograph.gateway.account;

/**
 * Gateway-side view of an account from the Account service. {@code customerId} is kept for
 * resolvers even though the GraphQL {@code Account} type does not expose it.
 */
public record Account(
        String id,
        String customerId,
        AccountType type,
        AccountStatus status,
        String currency) {
}
