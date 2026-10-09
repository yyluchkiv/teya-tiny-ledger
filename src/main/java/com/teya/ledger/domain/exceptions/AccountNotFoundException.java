package com.teya.ledger.domain.exceptions;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    private final UUID id;

    public AccountNotFoundException(UUID id) {
        super("Account " + id + " not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
