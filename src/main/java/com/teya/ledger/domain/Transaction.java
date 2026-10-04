package com.teya.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Transaction(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        String description,
        Instant timestamp) {

    /**
     * The effect of this transaction on the balance: positive for deposits, negative for withdrawals.
     */
    public BigDecimal signedAmount() {
        return type == TransactionType.DEPOSIT ? amount : amount.negate();
    }
}
