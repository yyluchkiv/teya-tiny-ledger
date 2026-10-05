package com.teya.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private static final int THREADS = 16;
    private static final int OPS_PER_THREAD = 50;
    private static final int TOTAL_OPS = THREADS * OPS_PER_THREAD;

    private final Ledger ledger = new Ledger(Clock.systemUTC());

    @Test
    void recordAssignsIdAndTimestampFromClock() {
        // Arrange
        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        Ledger fixedClockLedger = new Ledger(Clock.fixed(now, ZoneOffset.UTC));

        // Act
        Transaction transaction = fixedClockLedger.record(TransactionType.DEPOSIT, amount("10.00"), "salary");

        // Assert
        assertThat(transaction.id()).isNotNull();
        assertThat(transaction.timestamp()).isEqualTo(now);
        assertThat(transaction.description()).isEqualTo("salary");
    }

    @Test
    void recordUpdatesBalanceForDepositsAndWithdrawals() {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("100.00"), null);

        // Act
        ledger.record(TransactionType.WITHDRAWAL, amount("30.00"), null);

        // Assert
        assertThat(ledger.balance()).isEqualByComparingTo("70.00");
    }

    @Test
    void newLedgerHasZeroBalanceAndNoHistory() {
        // Arrange & Act
        BigDecimal balance = ledger.balance();
        List<Transaction> history = ledger.history();

        // Assert
        assertThat(balance).isEqualByComparingTo("0.00");
        assertThat(history).isEmpty();
    }

    @Test
    void historyIsNewestFirst() {
        // Arrange
        Transaction first = ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);
        Transaction second = ledger.record(TransactionType.DEPOSIT, amount("20.00"), null);
        Transaction third = ledger.record(TransactionType.WITHDRAWAL, amount("5.00"), null);

        // Act
        List<Transaction> history = ledger.history();

        // Assert
        assertThat(history).containsExactly(third, second, first);
    }

    @Test
    void historyIsUnmodifiableDefensiveCopy() {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);
        List<Transaction> snapshot = ledger.history();

        // Act
        ledger.record(TransactionType.DEPOSIT, amount("20.00"), null);

        // Assert
        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void overdraftIsRejectedAndStateIsUnchanged() {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("50.00"), null);

        // Act & Assert
        assertThatThrownBy(() -> ledger.record(TransactionType.WITHDRAWAL, amount("50.01"), null))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(ledger.balance()).isEqualByComparingTo("50.00");
        assertThat(ledger.history()).hasSize(1);
    }

    @Test
    void findReturnsRecordedTransaction() {
        // Arrange
        Transaction recorded = ledger.record(TransactionType.DEPOSIT, amount("10.00"), null);

        // Act
        var found = ledger.find(recorded.id());

        // Assert
        assertThat(found).contains(recorded);
    }

    @Test
    void findReturnsEmptyForUnknownId() {
        // Act
        var found = ledger.find(UUID.randomUUID());

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void concurrentDepositsAreAllRecorded() throws Exception {
        // Act
        runConcurrently(() -> ledger.record(TransactionType.DEPOSIT, amount("1.00"), null));

        // Assert
        assertThat(ledger.balance()).isEqualByComparingTo(String.valueOf(TOTAL_OPS));
        assertThat(ledger.history()).hasSize(TOTAL_OPS);
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        // Arrange
        ledger.record(TransactionType.DEPOSIT, amount("100.00"), null);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        // Act
        runConcurrently(() -> {
            try {
                ledger.record(TransactionType.WITHDRAWAL, amount("1.00"), null);
                succeeded.incrementAndGet();
            } catch (InsufficientFundsException e) {
                rejected.incrementAndGet();
            }
        });

        // Assert
        assertThat(succeeded).hasValue(100);
        assertThat(rejected).hasValue(TOTAL_OPS - 100);
        assertThat(ledger.balance()).isEqualByComparingTo("0.00");
        assertThat(ledger.history()).hasSize(101);
    }

    @Test
    void concurrentHistoryOrderMatchesTimestampOrder() throws Exception {
        // Act
        runConcurrently(() -> ledger.record(TransactionType.DEPOSIT, amount("1.00"), null));

        // Assert
        assertThat(ledger.history())
                .hasSize(TOTAL_OPS)
                .extracting(Transaction::timestamp)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    /**
     * Runs {@code operation} {@link #OPS_PER_THREAD} times on each of {@link #THREADS} threads, all released together
     * by a start gate. Any unexpected exception fails the test via {@link Future#get}.
     */
    private static void runConcurrently(Runnable operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> workers = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                workers.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        operation.run();
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
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
