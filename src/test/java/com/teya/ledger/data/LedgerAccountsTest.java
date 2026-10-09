package com.teya.ledger.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.base.Transfer;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.SameAccountTransferException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Multi-account and transfer behaviour of {@link Ledger}. Single-account behaviour is covered by {@link LedgerTest}.
 */
class LedgerAccountsTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final Ledger ledger = new Ledger(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void defaultAccountExistsWithFixedId() {
        // Act
        var account = ledger.findAccount(Account.DEFAULT_ID);

        // Assert
        assertThat(account).hasValueSatisfying(a -> {
            assertThat(a.name()).isEqualTo("main");
            assertThat(a.createdAt()).isEqualTo(NOW);
        });
        assertThat(ledger.accounts()).extracting(Account::id).containsExactly(Account.DEFAULT_ID);
    }

    @Test
    void createdAccountsAreListedNewestFirstAndFindable() {
        // Arrange
        Account savings = ledger.createAccount("savings");

        // Act
        Account holiday = ledger.createAccount("holiday");

        // Assert
        assertThat(ledger.accounts()).extracting(Account::name).containsExactly("holiday", "savings", "main");
        assertThat(ledger.findAccount(savings.id())).contains(savings);
        assertThat(ledger.findAccount(holiday.id())).contains(holiday);
        assertThat(ledger.balance(holiday.id())).isEqualByComparingTo("0.00");
    }

    @Test
    void findAccountReturnsEmptyForUnknownId() {
        // Act
        var account = ledger.findAccount(UUID.randomUUID());

        // Assert
        assertThat(account).isEmpty();
    }

    @Test
    void accountsHaveSeparateBalancesAndHistories() {
        // Arrange
        Account savings = ledger.createAccount("savings");
        Transaction onMain = ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);

        // Act
        Transaction onSavings = ledger.record(savings.id(), TransactionType.DEPOSIT, amount("25.00"), null);

        // Assert
        assertThat(onMain.accountId()).isEqualTo(Account.DEFAULT_ID);
        assertThat(onSavings.accountId()).isEqualTo(savings.id());
        assertThat(ledger.balance()).isEqualByComparingTo("10.00");
        assertThat(ledger.balance(savings.id())).isEqualByComparingTo("25.00");
        assertThat(ledger.history()).containsExactly(onMain);
        assertThat(ledger.history(savings.id())).containsExactly(onSavings);
    }

    @Test
    void findIsScopedToTheAccount() {
        // Arrange
        Account savings = ledger.createAccount("savings");
        Transaction onSavings = ledger.record(savings.id(), TransactionType.DEPOSIT, amount("25.00"), null);

        // Act
        var viaSavings = ledger.find(savings.id(), onSavings.id());
        var viaDefault = ledger.find(onSavings.id());

        // Assert
        assertThat(viaSavings).contains(onSavings);
        assertThat(viaDefault).isEmpty();
    }

    @Test
    void operationsOnUnknownAccountAreRejected() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> ledger.record(unknown, TransactionType.DEPOSIT, amount("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> ledger.balance(unknown)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> ledger.history(unknown)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> ledger.find(unknown, UUID.randomUUID()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void transferMovesMoneyAndLinksBothLegs() {
        // Arrange
        Account savings = ledger.createAccount("savings");
        ledger.record(TransactionType.DEPOSIT, amount("100.00"), null);

        // Act
        Transfer transfer = ledger.transfer(Account.DEFAULT_ID, savings.id(), amount("40.00"), "save");

        // Assert
        assertThat(ledger.balance()).isEqualByComparingTo("60.00");
        assertThat(ledger.balance(savings.id())).isEqualByComparingTo("40.00");

        Transaction debit = ledger.find(transfer.debitTransactionId()).orElseThrow();
        Transaction credit = ledger.find(savings.id(), transfer.creditTransactionId()).orElseThrow();
        assertThat(debit.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(credit.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(debit.transferId()).isEqualTo(transfer.id());
        assertThat(credit.transferId()).isEqualTo(transfer.id());
        assertThat(debit.timestamp()).isEqualTo(transfer.timestamp());
        assertThat(credit.timestamp()).isEqualTo(transfer.timestamp());
        assertThat(credit.description()).isEqualTo("save");

        assertThat(ledger.findTransfer(transfer.id())).contains(transfer);
        assertThat(transfer.fromAccountId()).isEqualTo(Account.DEFAULT_ID);
        assertThat(transfer.toAccountId()).isEqualTo(savings.id());
    }

    @Test
    void plainTransactionsHaveNoTransferId() {
        // Act
        Transaction transaction = ledger.record(TransactionType.DEPOSIT, amount("1.00"), null);

        // Assert
        assertThat(transaction.transferId()).isNull();
    }

    @Test
    void insufficientFundsTransferLeavesBothAccountsUntouched() {
        // Arrange
        Account savings = ledger.createAccount("savings");
        ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> ledger.transfer(Account.DEFAULT_ID, savings.id(), amount("10.01"), null))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(ledger.balance()).isEqualByComparingTo("10.00");
        assertThat(ledger.balance(savings.id())).isEqualByComparingTo("0.00");
        assertThat(ledger.history()).hasSize(1);
        assertThat(ledger.history(savings.id())).isEmpty();
    }

    @Test
    void sameAccountTransferIsRejected() {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> ledger.transfer(Account.DEFAULT_ID, Account.DEFAULT_ID, amount("1.00"), null))
                .isInstanceOf(SameAccountTransferException.class);
        assertThat(ledger.history()).hasSize(1);
    }

    @Test
    void transferWithUnknownAccountIsRejected() {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);
        UUID unknown = UUID.randomUUID();

        // Act & Assert
        assertThatThrownBy(() -> ledger.transfer(Account.DEFAULT_ID, unknown, amount("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> ledger.transfer(unknown, Account.DEFAULT_ID, amount("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(ledger.balance()).isEqualByComparingTo("10.00");
        assertThat(ledger.history()).hasSize(1);
    }

    @Test
    void findTransferReturnsEmptyForUnknownId() {
        // Act
        var transfer = ledger.findTransfer(UUID.randomUUID());

        // Assert
        assertThat(transfer).isEmpty();
    }

    @Test
    void concurrentOppositeTransfersConserveTotalBalance() throws Exception {
        // Arrange
        Ledger concurrent = new Ledger(Clock.systemUTC());
        UUID a = Account.DEFAULT_ID;
        UUID b = concurrent.createAccount("b").id();
        concurrent.record(a, TransactionType.DEPOSIT, amount("100.00"), null);
        concurrent.record(b, TransactionType.DEPOSIT, amount("100.00"), null);
        int transfersPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> workers = new ArrayList<>();

        // Act
        try {
            for (UUID[] direction : new UUID[][] {{a, b}, {b, a}}) {
                workers.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < transfersPerThread; i++) {
                        try {
                            concurrent.transfer(direction[0], direction[1], amount("1.00"), null);
                        } catch (InsufficientFundsException e) {
                            // Possible when one side is drained; the invariant still holds.
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Assert
        BigDecimal balanceA = concurrent.balance(a);
        BigDecimal balanceB = concurrent.balance(b);
        assertThat(balanceA.add(balanceB)).isEqualByComparingTo("200.00");
        assertThat(balanceA.signum()).isNotNegative();
        assertThat(balanceB.signum()).isNotNegative();
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
