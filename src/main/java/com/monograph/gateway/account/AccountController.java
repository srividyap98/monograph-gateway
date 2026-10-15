package com.monograph.gateway.account;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.monograph.gateway.customer.Customer;

import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

/** Resolves the fields that {@code account.graphqls} adds to {@code Customer}. */
@Controller
public class AccountController {

    private final AccountClient accountClient;

    public AccountController(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    /**
     * {@code Customer.accounts} for every Customer in the response at once. {@code @BatchMapping}
     * registers a DataLoader keyed by the parent object: graphql-java collects all parents at
     * this level, then calls this method once with the whole list.
     */
    @BatchMapping(typeName = "Customer")
    public Map<Customer, List<Account>> accounts(List<Customer> customers) {
        Map<String, List<Account>> byCustomerId =
                accountClient.findByCustomerIds(customers.stream().map(Customer::id).toList());
        Map<Customer, List<Account>> result = new LinkedHashMap<>();
        // Every parent needs an entry: accounts is [Account!]!, so a missing key would be an error.
        customers.forEach(c -> result.put(c, byCustomerId.getOrDefault(c.id(), List.of())));
        return result;
    }
}
