package com.teya.ledger.domain.base;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Account(UUID id, String name, Instant createdAt) {

    /** The account the legacy single-ledger endpoints act on. It always exists. */
    public static final UUID DEFAULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String DEFAULT_NAME = "main";

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
