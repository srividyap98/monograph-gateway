package com.monograph.downstream.account;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Simulated Account service: which accounts a customer holds. Knows nothing about money. */
@RestController
@RequestMapping("/account-service/accounts")
class AccountServiceController {

    record AccountDto(String id, String customerId, String type, String status, String currency) {
    }

    private static final List<AccountDto> ACCOUNTS = List.of(
            new AccountDto("A-2001", "C-1001", "CHECKING", "OPEN", "USD"),
            new AccountDto("A-2002", "C-1001", "SAVINGS", "OPEN", "USD"),
            new AccountDto("A-2003", "C-1002", "CHECKING", "CLOSED", "USD"),
            new AccountDto("A-2004", "C-1003", "CREDIT_CARD", "FROZEN", "USD"));

    /** An unknown customer simply has no accounts: 200 with an empty list, not 404. */
    @GetMapping(params = "customerId")
    List<AccountDto> byCustomer(@RequestParam String customerId) {
        return ACCOUNTS.stream().filter(a -> a.customerId().equals(customerId)).toList();
    }

    /** Batch: {@code ?customerIds=C-1&customerIds=C-2}. Flat list; callers group by {@code customerId}. */
    @GetMapping(params = "customerIds")
    List<AccountDto> byCustomers(@RequestParam Set<String> customerIds) {
        return ACCOUNTS.stream().filter(a -> customerIds.contains(a.customerId())).toList();
    }
}
