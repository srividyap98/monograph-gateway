package com.monograph.downstream.financial;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Simulated Financial service: balances and posted transactions, keyed by account id. */
@RestController
@RequestMapping("/financial-service")
class FinancialServiceController {

    record BalanceDto(String accountId, BigDecimal available, BigDecimal ledger, String currency, Instant asOf) {
    }

    record TransactionDto(String id, String accountId, BigDecimal amount, String type, String description,
            Instant postedAt) {
    }

    private static final Instant AS_OF = Instant.parse("2026-09-28T00:00:00Z");

    private static final Map<String, BalanceDto> BALANCES = Map.of(
            "A-2001", new BalanceDto("A-2001", new BigDecimal("1250.75"), new BigDecimal("1300.75"), "USD", AS_OF),
            "A-2002", new BalanceDto("A-2002", new BigDecimal("10500.00"), new BigDecimal("10500.00"), "USD", AS_OF),
            "A-2003", new BalanceDto("A-2003", new BigDecimal("0.00"), new BigDecimal("0.00"), "USD", AS_OF),
            "A-2004", new BalanceDto("A-2004", new BigDecimal("0.00"), new BigDecimal("-845.10"), "USD", AS_OF));

    private static final List<TransactionDto> TRANSACTIONS = List.of(
            new TransactionDto("T-5001", "A-2001", new BigDecimal("50.00"), "DEBIT", "Coffee Roasters",
                    Instant.parse("2026-09-27T09:15:00Z")),
            new TransactionDto("T-5002", "A-2001", new BigDecimal("2500.00"), "CREDIT", "Payroll",
                    Instant.parse("2026-09-25T06:00:00Z")),
            new TransactionDto("T-5003", "A-2001", new BigDecimal("120.40"), "DEBIT", "Utility bill",
                    Instant.parse("2026-09-20T12:30:00Z")),
            new TransactionDto("T-5004", "A-2002", new BigDecimal("500.00"), "CREDIT", "Transfer from checking",
                    Instant.parse("2026-09-01T08:00:00Z")),
            new TransactionDto("T-5005", "A-2004", new BigDecimal("845.10"), "DEBIT", "Airline tickets",
                    Instant.parse("2026-08-30T17:45:00Z")));

    @GetMapping("/accounts/{accountId}/balance")
    ResponseEntity<BalanceDto> balance(@PathVariable String accountId) {
        return ResponseEntity.of(Optional.ofNullable(BALANCES.get(accountId)));
    }

    /** Newest first, capped at {@code limit}. */
    @GetMapping("/accounts/{accountId}/transactions")
    List<TransactionDto> transactions(@PathVariable String accountId,
            @RequestParam(defaultValue = "10") int limit) {
        return newestFirst(accountId, limit).toList();
    }

    /** Batch: {@code ?accountIds=A-1&accountIds=A-2}. Accounts without a balance are absent. */
    @GetMapping("/balances")
    List<BalanceDto> balances(@RequestParam Set<String> accountIds) {
        return accountIds.stream().map(BALANCES::get).filter(Objects::nonNull).toList();
    }

    /** Batch: newest {@code limit} transactions <em>per account</em>, as one flat list. */
    @GetMapping("/transactions")
    List<TransactionDto> transactions(@RequestParam Set<String> accountIds,
            @RequestParam(defaultValue = "10") int limit) {
        return accountIds.stream().flatMap(id -> newestFirst(id, limit)).toList();
    }

    private static Stream<TransactionDto> newestFirst(String accountId, int limit) {
        return TRANSACTIONS.stream()
                .filter(t -> t.accountId().equals(accountId))
                .sorted(Comparator.comparing(TransactionDto::postedAt).reversed())
                .limit(limit);
    }
}
