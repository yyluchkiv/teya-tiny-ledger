package com.teya.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teya.ledger.domain.InsufficientFundsException;
import com.teya.ledger.domain.Transaction;
import com.teya.ledger.domain.TransactionType;
import com.teya.ledger.repository.InMemoryLedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private final LedgerService service =
            new LedgerService(new InMemoryLedgerRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void depositIncreasesBalanceAndIsRecorded() {
        // Arrange
        BigDecimal amount = new BigDecimal("100");

        // Act
        Transaction transaction = service.recordTransaction(TransactionType.DEPOSIT, amount, "salary");

        // Assert
        assertThat(transaction.id()).isNotNull();
        assertThat(transaction.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(transaction.description()).isEqualTo("salary");
        assertThat(transaction.timestamp()).isEqualTo(NOW);
        assertThat(service.getBalance()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawalDecreasesBalance() {
        // Arrange
        service.recordTransaction(TransactionType.DEPOSIT, new BigDecimal("100.00"), null);

        // Act
        service.recordTransaction(TransactionType.WITHDRAWAL, new BigDecimal("30.00"), "groceries");

        // Assert
        assertThat(service.getBalance()).isEqualTo(new BigDecimal("70.00"));
    }

    @Test
    void withdrawalOfExactBalanceIsAllowed() {
        // Arrange
        service.recordTransaction(TransactionType.DEPOSIT, new BigDecimal("42.50"), null);

        // Act
        service.recordTransaction(TransactionType.WITHDRAWAL, new BigDecimal("42.50"), null);

        // Assert
        assertThat(service.getBalance()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void overdraftIsRejectedAndStateIsUnchanged() {
        // Arrange
        service.recordTransaction(TransactionType.DEPOSIT, new BigDecimal("10.00"), null);

        // Act & Assert
        assertThatThrownBy(() ->
                        service.recordTransaction(TransactionType.WITHDRAWAL, new BigDecimal("10.01"), null))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(e -> {
                    InsufficientFundsException ex = (InsufficientFundsException) e;
                    assertThat(ex.getRequestedAmount()).isEqualByComparingTo("10.01");
                    assertThat(ex.getCurrentBalance()).isEqualByComparingTo("10.00");
                });
        assertThat(service.getBalance()).isEqualTo(new BigDecimal("10.00"));
        assertThat(service.getHistory()).hasSize(1);
    }

    @Test
    void historyIsNewestFirst() {
        // Arrange
        Transaction first = service.recordTransaction(TransactionType.DEPOSIT, new BigDecimal("5.00"), "first");
        Transaction second = service.recordTransaction(TransactionType.WITHDRAWAL, new BigDecimal("2.00"), "second");

        // Act
        List<Transaction> history = service.getHistory();

        // Assert
        assertThat(history).containsExactly(second, first);
    }

    @Test
    void emptyLedgerHasZeroBalance() {
        // Arrange & Act
        BigDecimal balance = service.getBalance();

        // Assert
        assertThat(balance).isEqualTo(new BigDecimal("0.00"));
    }
}
