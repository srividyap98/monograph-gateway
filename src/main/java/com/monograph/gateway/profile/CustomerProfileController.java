package com.monograph.gateway.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.monograph.gateway.account.Account;
import com.monograph.gateway.account.AccountClient;
import com.monograph.gateway.customer.Customer;
import com.monograph.gateway.customer.CustomerIds;
import org.dataloader.DataLoader;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Entry point for the aggregated customer view. Each resolver calls exactly one backend service;
 * graphql-java walks the query and stitches the results into one response tree:
 *
 * <pre>
 * customerProfile            -> Customer service   (via the customer DataLoader)
 *   accounts                 -> Account service    (this class, batched)
 *     balance, transactions  -> Financial service  (FinancialController, batched)
 * </pre>
 *
 * Superseded by {@code Customer.accounts} for Phase 3: a federated graph has no owner for a
 * gateway-only wrapper type.
 */
@Controller
public class CustomerProfileController {

    private final AccountClient accountClient;

    public CustomerProfileController(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    /** {@code Query.customerProfile(customerId: ID!)}; null when the customer does not exist. */
    @QueryMapping
    public CompletableFuture<CustomerProfile> customerProfile(@Argument String customerId,
            DataLoader<String, Customer> customerLoader) {
        return customerLoader.load(CustomerIds.normalize(customerId))
                .thenApply(customer -> customer != null ? new CustomerProfile(customer) : null);
    }

    /** {@code CustomerProfile.accounts} for every profile in the response, in one Account-service call. */
    @BatchMapping(typeName = "CustomerProfile")
    public Map<CustomerProfile, List<Account>> accounts(List<CustomerProfile> profiles) {
        Map<String, List<Account>> byCustomerId =
                accountClient.findByCustomerIds(profiles.stream().map(p -> p.customer().id()).toList());
        Map<CustomerProfile, List<Account>> result = new LinkedHashMap<>();
        profiles.forEach(p -> result.put(p, byCustomerId.getOrDefault(p.customer().id(), List.of())));
        return result;
    }
}
