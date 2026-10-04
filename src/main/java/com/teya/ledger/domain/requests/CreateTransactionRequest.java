package com.teya.ledger.domain.requests;

import com.teya.ledger.domain.base.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull @Positive @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @Size(max = 255) String description) {
}
