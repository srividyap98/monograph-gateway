package com.monograph.gateway.financial;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account balance from the Financial service. Money is {@link BigDecimal}, never {@code double},
 * and is exposed in GraphQL as a decimal string so no client parses it into a float.
 */
public record Balance(
        String accountId,
        BigDecimal available,
        BigDecimal ledger,
        String currency,
        Instant asOf) {
}
