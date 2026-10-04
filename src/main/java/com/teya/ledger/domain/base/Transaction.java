package com.teya.ledger.domain.base;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Transaction(
        UUID id,
        TransactionType type,
        BigDecimal amount,
        String description,
        Instant timestamp) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(timestamp, "timestamp");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount.toPlainString());
        }
    }

    /**
     * The effect of this transaction on the balance: positive for deposits, negative for withdrawals.
     */
    public BigDecimal signedAmount() {
        return type == TransactionType.DEPOSIT ? amount : amount.negate();
    }
}
