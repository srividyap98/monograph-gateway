package com.monograph.gateway.customer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test double for {@link CustomerClient}: the Phase 1 in-memory data, now used only by slice tests
 * via {@code @Import}. Production uses {@link HttpCustomerClient}.
 */
public class InMemoryCustomerClient implements CustomerClient {

    private static final Map<String, Customer> CUSTOMERS = Map.of(
            "C-1001", new Customer("C-1001", "Ada", "Lovelace", "ada@example.com", CustomerStatus.ACTIVE),
            "C-1002", new Customer("C-1002", "Alan", "Turing", null, CustomerStatus.INACTIVE),
            "C-1003", new Customer("C-1003", "Grace", "Hopper", "grace@example.com", CustomerStatus.SUSPENDED));

    @Override
    public Map<String, Customer> findByIds(Collection<String> ids) {
        Map<String, Customer> found = new HashMap<>();
        ids.stream().filter(CUSTOMERS::containsKey).forEach(id -> found.put(id, CUSTOMERS.get(id)));
        return found;
    }

    /** Read-only double: mutation tests mock {@link CustomerClient} instead. */
    @Override
    public Customer create(CreateCustomerInput input) {
        throw new UnsupportedOperationException("read-only test double");
    }

    @Override
    public Optional<Customer> updateStatus(String id, CustomerStatus status) {
        throw new UnsupportedOperationException("read-only test double");
    }
}
