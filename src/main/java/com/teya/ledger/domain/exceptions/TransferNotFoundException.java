package com.teya.ledger.domain.exceptions;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {

    private final UUID id;

    public TransferNotFoundException(UUID id) {
        super("Transfer " + id + " not found");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
