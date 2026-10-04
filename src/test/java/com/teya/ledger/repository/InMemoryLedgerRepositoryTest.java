package com.teya.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teya.ledger.domain.InsufficientFundsException;
import com.teya.ledger.domain.Transaction;
import com.teya.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryLedgerRepositoryTest {

    private final InMemoryLedgerRepository repository = new InMemoryLedgerRepository();

    @Test
    void appendUpdatesBalanceForDepositsAndWithdrawals() {
        // Arrange
        Transaction deposit = transaction(TransactionType.DEPOSIT, "100.00");
        Transaction withdrawal = transaction(TransactionType.WITHDRAWAL, "30.00");

        // Act
        repository.append(deposit);
        repository.append(withdrawal);

        // Assert
        assertThat(repository.balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void newRepositoryHasZeroBalanceAndNoHistory() {
        // Arrange & Act
        BigDecimal balance = repository.balance();
        List<Transaction> history = repository.findAllNewestFirst();

        // Assert
        assertThat(balance).isEqualByComparingTo("0.00");
        assertThat(history).isEmpty();
    }

    @Test
    void findAllReturnsTransactionsNewestFirst() {
        // Arrange
        Transaction first = repository.append(transaction(TransactionType.DEPOSIT, "10.00"));
        Transaction second = repository.append(transaction(TransactionType.DEPOSIT, "20.00"));
        Transaction third = repository.append(transaction(TransactionType.WITHDRAWAL, "5.00"));

        // Act
        List<Transaction> history = repository.findAllNewestFirst();

        // Assert
        assertThat(history).containsExactly(third, second, first);
    }

    @Test
    void findAllReturnsUnmodifiableDefensiveCopy() {
        // Arrange
        repository.append(transaction(TransactionType.DEPOSIT, "10.00"));
        List<Transaction> snapshot = repository.findAllNewestFirst();

        // Act
        repository.append(transaction(TransactionType.DEPOSIT, "20.00"));

        // Assert
        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(transaction(TransactionType.DEPOSIT, "1.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void appendIfSufficientRejectsOverdraftAndLeavesStateUnchanged() {
        // Arrange
        repository.append(transaction(TransactionType.DEPOSIT, "50.00"));
        Transaction overdraft = transaction(TransactionType.WITHDRAWAL, "50.01");

        // Act & Assert
        assertThatThrownBy(() -> repository.appendIfSufficient(overdraft))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(repository.balance()).isEqualByComparingTo("50.00");
        assertThat(repository.findAllNewestFirst()).hasSize(1);
    }

    @Test
    void concurrentDepositsAreAllRecorded() throws Exception {
        // Arrange
        int count = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Callable<Transaction>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> repository.append(transaction(TransactionType.DEPOSIT, "1.00")));
        }

        // Act
        executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        assertThat(repository.balance()).isEqualByComparingTo("1000.00");
        assertThat(repository.findAllNewestFirst()).hasSize(count);
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        // Arrange
        repository.append(transaction(TransactionType.DEPOSIT, "100.00"));
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Callable<Transaction>> tasks = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            tasks.add(() -> repository.appendIfSufficient(transaction(TransactionType.WITHDRAWAL, "1.00")));
        }

        // Act
        List<Future<Transaction>> results = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Assert
        long succeeded = results.stream().filter(InMemoryLedgerRepositoryTest::succeeded).count();
        assertThat(succeeded).isEqualTo(100);
        assertThat(repository.balance()).isEqualByComparingTo("0.00");
    }

    private static boolean succeeded(Future<Transaction> future) {
        try {
            future.get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Transaction transaction(TransactionType type, String amount) {
        return new Transaction(UUID.randomUUID(), type, new BigDecimal(amount), null, Instant.now());
    }
}
