package com.teya.ledger.repository;

import com.teya.ledger.domain.InsufficientFundsException;
import com.teya.ledger.domain.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Single in-memory ledger. All access is synchronized on this instance so the stored history and the
 * running balance always stay consistent with each other.
 */
@Repository
public class InMemoryLedgerRepository {

    private final List<Transaction> transactions = new ArrayList<>();
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    public synchronized Transaction append(Transaction transaction) {
        transactions.add(transaction);
        balance = balance.add(transaction.signedAmount());
        return transaction;
    }

    /**
     * Appends the transaction only if it would not take the balance below zero. The check and the write
     * happen under the same lock, so concurrent withdrawals cannot overdraw the ledger.
     */
    public synchronized Transaction appendIfSufficient(Transaction transaction) {
        if (balance.add(transaction.signedAmount()).signum() < 0) {
            throw new InsufficientFundsException(transaction.amount(), balance);
        }
        return append(transaction);
    }

    public synchronized BigDecimal balance() {
        return balance;
    }

    public synchronized List<Transaction> findAllNewestFirst() {
        List<Transaction> copy = new ArrayList<>(transactions);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }
}
