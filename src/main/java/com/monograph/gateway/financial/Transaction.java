package com.monograph.gateway.financial;

import java.math.BigDecimal;
import java.time.Instant;

/** A posted transaction from the Financial service. {@code amount} is always positive; {@code type} gives the sign. */
public record Transaction(
        String id,
        String accountId,
        BigDecimal amount,
        TransactionType type,
        String description,
        Instant postedAt) {
}
