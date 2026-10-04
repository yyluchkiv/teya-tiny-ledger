package com.teya.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void signedAmountIsNegativeForWithdrawal() {
        // Arrange
        Transaction withdrawal =
                new Transaction(UUID.randomUUID(), TransactionType.WITHDRAWAL, new BigDecimal("5.00"), null, NOW);

        // Act
        BigDecimal signed = withdrawal.signedAmount();

        // Assert
        assertThat(signed).isEqualByComparingTo("-5.00");
    }

    @Test
    void nonPositiveAmountIsRejected() {
        // Act & Assert
        assertThatThrownBy(() ->
                        new Transaction(UUID.randomUUID(), TransactionType.DEPOSIT, BigDecimal.ZERO, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transaction(
                        UUID.randomUUID(), TransactionType.DEPOSIT, new BigDecimal("-1.00"), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingRequiredFieldIsRejected() {
        // Act & Assert
        assertThatThrownBy(() -> new Transaction(UUID.randomUUID(), null, BigDecimal.ONE, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
