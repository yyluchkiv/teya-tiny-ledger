package com.teya.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teya.ledger.domain.base.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(Account account, BigDecimal balance) {
        return new AccountResponse(account.id(), account.name(), balance, account.createdAt());
    }
}
