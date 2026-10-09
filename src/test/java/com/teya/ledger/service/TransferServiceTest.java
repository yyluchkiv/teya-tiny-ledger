package com.teya.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teya.ledger.data.Ledger;
import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.base.Transfer;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.SameAccountTransferException;
import com.teya.ledger.domain.exceptions.TransferNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final Ledger ledger = new Ledger(Clock.fixed(NOW, ZoneOffset.UTC));
    private final AccountService accountService = new AccountService(ledger);
    private final TransferService service = new TransferService(ledger);

    @Test
    void transferIsNormalisedAndMovesMoney() {
        // Arrange
        Account savings = accountService.createAccount("savings");
        accountService.recordTransaction(Account.DEFAULT_ID, TransactionType.DEPOSIT, new BigDecimal("100.00"), null);

        // Act
        Transfer transfer = service.transfer(Account.DEFAULT_ID, savings.id(), new BigDecimal("40"), "save");

        // Assert
        assertThat(transfer.amount()).isEqualTo(new BigDecimal("40.00"));
        assertThat(transfer.description()).isEqualTo("save");
        assertThat(transfer.timestamp()).isEqualTo(NOW);
        assertThat(accountService.getBalance(Account.DEFAULT_ID)).isEqualTo(new BigDecimal("60.00"));
        assertThat(accountService.getBalance(savings.id())).isEqualTo(new BigDecimal("40.00"));
        assertThat(service.getTransfer(transfer.id())).isEqualTo(transfer);
    }

    @Test
    void insufficientFundsLeavesBothBalancesUnchanged() {
        // Arrange
        Account savings = accountService.createAccount("savings");
        accountService.recordTransaction(Account.DEFAULT_ID, TransactionType.DEPOSIT, new BigDecimal("10.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> service.transfer(Account.DEFAULT_ID, savings.id(), new BigDecimal("10.01"), null))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(accountService.getBalance(Account.DEFAULT_ID)).isEqualTo(new BigDecimal("10.00"));
        assertThat(accountService.getBalance(savings.id())).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void sameAccountTransferIsRejected() {
        // Act & Assert
        assertThatThrownBy(() -> service.transfer(
                        Account.DEFAULT_ID, Account.DEFAULT_ID, new BigDecimal("1.00"), null))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void unknownAccountIsRejected() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> service.transfer(Account.DEFAULT_ID, unknown, new BigDecimal("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getTransferThrowsForUnknownId() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> service.getTransfer(unknown)).isInstanceOf(TransferNotFoundException.class);
    }
}
