package com.teya.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teya.ledger.domain.Transaction;
import com.teya.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String description,
        Instant timestamp) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.type(),
                transaction.amount(),
                transaction.description(),
                transaction.timestamp());
    }
}
