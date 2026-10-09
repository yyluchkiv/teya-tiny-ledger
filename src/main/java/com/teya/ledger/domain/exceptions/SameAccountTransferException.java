package com.teya.ledger.domain.exceptions;

import java.util.UUID;

public class SameAccountTransferException extends RuntimeException {

    private final UUID accountId;

    public SameAccountTransferException(UUID accountId) {
        super("Cannot transfer from account " + accountId + " to itself");
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
