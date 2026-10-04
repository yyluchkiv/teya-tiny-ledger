package com.teya.ledger.domain.exceptions;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    private final UUID id;

    public TransactionNotFoundException(UUID id) {
        super("Transaction " + id + " not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
