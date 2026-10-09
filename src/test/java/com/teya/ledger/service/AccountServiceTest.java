package com.teya.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teya.ledger.data.Ledger;
import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.TransactionNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final AccountService service = new AccountService(new Ledger(Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void createAccountIsListedAndFindable() {
        // Act
        Account account = service.createAccount("savings");

        // Assert
        assertThat(account.name()).isEqualTo("savings");
        assertThat(account.createdAt()).isEqualTo(NOW);
        assertThat(service.listAccounts()).extracting(Account::id).containsExactly(account.id(), Account.DEFAULT_ID);
        assertThat(service.getAccount(account.id())).isEqualTo(account);
    }

    @Test
    void getAccountThrowsForUnknownId() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> service.getAccount(unknown)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void depositIsNormalisedAndRecordedOnTheAccount() {
        // Arrange
        Account account = service.createAccount("savings");

        // Act
        Transaction transaction =
                service.recordTransaction(account.id(), TransactionType.DEPOSIT, new BigDecimal("100"), "salary");

        // Assert
        assertThat(transaction.accountId()).isEqualTo(account.id());
        assertThat(transaction.amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(transaction.timestamp()).isEqualTo(NOW);
        assertThat(service.getBalance(account.id())).isEqualTo(new BigDecimal("100.00"));
        assertThat(service.getBalance(Account.DEFAULT_ID)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void overdraftIsRejectedAndStateIsUnchanged() {
        // Arrange
        Account account = service.createAccount("savings");
        service.recordTransaction(account.id(), TransactionType.DEPOSIT, new BigDecimal("10.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> service.recordTransaction(
                        account.id(), TransactionType.WITHDRAWAL, new BigDecimal("10.01"), null))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(service.getBalance(account.id())).isEqualTo(new BigDecimal("10.00"));
        assertThat(service.getHistory(account.id())).hasSize(1);
    }

    @Test
    void historyIsNewestFirst() {
        // Arrange
        Account account = service.createAccount("savings");
        Transaction first =
                service.recordTransaction(account.id(), TransactionType.DEPOSIT, new BigDecimal("5.00"), "first");
        Transaction second =
                service.recordTransaction(account.id(), TransactionType.WITHDRAWAL, new BigDecimal("2.00"), "second");

        // Act
        var history = service.getHistory(account.id());

        // Assert
        assertThat(history).containsExactly(second, first);
    }

    @Test
    void getTransactionReturnsTransactionOfTheAccount() {
        // Arrange
        Account account = service.createAccount("savings");
        Transaction recorded =
                service.recordTransaction(account.id(), TransactionType.DEPOSIT, new BigDecimal("5.00"), null);

        // Act
        Transaction found = service.getTransaction(account.id(), recorded.id());

        // Assert
        assertThat(found).isEqualTo(recorded);
    }

    @Test
    void getTransactionOfAnotherAccountThrowsNotFound() {
        // Arrange
        Account account = service.createAccount("savings");
        Transaction recorded =
                service.recordTransaction(account.id(), TransactionType.DEPOSIT, new BigDecimal("5.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> service.getTransaction(Account.DEFAULT_ID, recorded.id()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void operationsOnUnknownAccountThrowNotFound() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> service.recordTransaction(
                        unknown, TransactionType.DEPOSIT, new BigDecimal("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getBalance(unknown)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getHistory(unknown)).isInstanceOf(AccountNotFoundException.class);
    }
}
