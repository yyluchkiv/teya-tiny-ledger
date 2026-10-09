package com.teya.ledger.domain.base;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A deposit or withdrawal on one account. {@code transferId} is set only when the transaction is one leg of a
 * {@link Transfer}.
 */
public record Transaction(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        Instant timestamp,
        UUID transferId) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(timestamp, "timestamp");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount.toPlainString());
        }
    }

    /**
     * A plain (non-transfer) transaction on the {@linkplain Account#DEFAULT_ID default account}.
     */
    public Transaction(UUID id, TransactionType type, BigDecimal amount, String description, Instant timestamp) {
        this(id, Account.DEFAULT_ID, type, amount, description, timestamp, null);
    }

    /**
     * The effect of this transaction on the balance: positive for deposits, negative for withdrawals.
     */
    public BigDecimal signedAmount() {
        return type == TransactionType.DEPOSIT ? amount : amount.negate();
    }
}
