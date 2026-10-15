package com.monograph.gateway.profile;

import com.monograph.gateway.customer.Customer;

/**
 * Root of the {@code customerProfile} result. Holds only what the root resolver fetched;
 * {@code accounts} (and everything below it) is resolved lazily from this object by
 * {@link CustomerProfileController#accounts}, and only when the client selects it.
 */
public record CustomerProfile(Customer customer) {
}
