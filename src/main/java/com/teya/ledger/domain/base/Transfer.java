package com.teya.ledger.domain.base;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A move of money between two accounts, recorded as a {@code WITHDRAWAL} on the source (the debit leg) and a
 * {@code DEPOSIT} on the destination (the credit leg), both carrying this transfer's id.
 */
public record Transfer(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String description,
        Instant timestamp,
        UUID debitTransactionId,
        UUID creditTransactionId) {

    public Transfer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(debitTransactionId, "debitTransactionId");
        Objects.requireNonNull(creditTransactionId, "creditTransactionId");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount.toPlainString());
        }
    }
}
