package com.teya.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teya.ledger.domain.base.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String description,
        Instant timestamp,
        UUID debitTransactionId,
        UUID creditTransactionId) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.id(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount(),
                transfer.description(),
                transfer.timestamp(),
                transfer.debitTransactionId(),
                transfer.creditTransactionId());
    }
}
