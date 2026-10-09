package com.teya.ledger.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class Amounts {

    private static final int SCALE = 2;

    private Amounts() {
    }

    /** Brings an amount to scale 2; more decimal places are rejected rather than rounded. */
    static BigDecimal normalise(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
