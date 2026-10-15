package com.monograph.gateway.customer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.monograph.gateway.error.NotFoundException;
import org.dataloader.DataLoader;
import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;

/** GraphQL resolvers for the Customer domain. */
@Controller
public class CustomerController {

    /** Max ids per {@code customers} call, and per batched call to the Customer service. */
    static final int MAX_BATCH_SIZE = 50;

    private final CustomerClient customerClient;

    /**
     * Registers the customer-by-id DataLoader. Spring creates a fresh DataLoader from this
     * registration for every GraphQL request, so batching and caching never leak across requests.
     */
    public CustomerController(CustomerClient customerClient, BatchLoaderRegistry registry) {
        this.customerClient = customerClient;
        registry.forTypePair(String.class, Customer.class)
                .withOptions(options -> options.setMaxBatchSize(MAX_BATCH_SIZE))
                .registerMappedBatchLoader((ids, env) -> Mono.fromCallable(() -> customerClient.findByIds(ids)));
    }

    /**
     * Resolves {@code Query.customer(id: ID!)}. Returns a future instead of a Customer: the
     * lookup is queued, and every customer lookup in the request is fetched in one batch.
     */
    @QueryMapping
    public CompletableFuture<Customer> customer(@Argument String id, DataLoader<String, Customer> customerLoader) {
        return customerLoader.load(CustomerIds.normalize(id));
    }

    /** {@code Query.customers(ids)}: one Customer-service call, duplicates fetched once, order preserved. */
    @QueryMapping
    public CompletableFuture<List<Customer>> customers(@Argument List<String> ids,
            DataLoader<String, Customer> customerLoader) {
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("At most " + MAX_BATCH_SIZE + " ids per request");
        }
        return customerLoader.loadMany(ids.stream().map(CustomerIds::normalize).toList());
    }

    /** {@code Mutation.createCustomer}; the input object is bound to the record by field name. */
    @MutationMapping
    public Customer createCustomer(@Argument CreateCustomerInput input) {
        return customerClient.create(input.normalized());
    }

    /**
     * {@code Mutation.updateCustomerStatus}. Unlike the {@code customer} query, a missing
     * customer is an error here: the caller asked to change something that does not exist.
     */
    @MutationMapping
    public Customer updateCustomerStatus(@Argument String id, @Argument CustomerStatus status) {
        String customerId = CustomerIds.normalize(id);
        return customerClient.updateStatus(customerId, status)
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found"));
    }
}
