package com.teya.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code transferId} is only emitted for transfer legs, so plain deposits and withdrawals keep their original shape.
 */
public record TransactionResponse(
        UUID id,
        TransactionType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String description,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID transferId) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.type(),
                transaction.amount(),
                transaction.description(),
                transaction.timestamp(),
                transaction.transferId());
    }
}
