package com.monograph.gateway.customer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Port to the Customer domain. The gateway depends only on this interface.
 * Level 1 used an in-memory implementation; {@link HttpCustomerClient} now calls the
 * Customer microservice without the schema or controller having changed.
 */
public interface CustomerClient {

    /**
     * Batch lookup, one downstream call for all ids. Keyed by id; ids that do not exist are
     * absent. This is the shape a DataLoader (and, in Phase 3, a federation entity resolver) needs.
     */
    Map<String, Customer> findByIds(Collection<String> ids);

    /**
     * @throws com.monograph.gateway.error.ConflictException if the service rejects the customer
     */
    Customer create(CreateCustomerInput input);

    /** The updated customer, or empty if no customer has this id. */
    Optional<Customer> updateStatus(String id, CustomerStatus status);
}
