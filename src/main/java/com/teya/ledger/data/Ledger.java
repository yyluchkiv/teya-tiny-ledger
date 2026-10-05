package com.teya.ledger.data;

import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A single in-memory ledger and the owner of its invariant: the balance never goes below zero and always equals the
 * sum of the recorded transactions.
 *
 * <p>The history, the id index and the running balance are mutated together, so they are guarded by one lock rather
 * than by concurrent collections: a concurrent collection only makes each individual operation thread-safe, not the
 * check-balance-then-append sequence across all three.
 */
public class Ledger {

    private final Clock clock;
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<UUID, Transaction> transactionsById = new HashMap<>();
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    public Ledger(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records a deposit or withdrawal. The timestamp is taken inside the lock, so history order (insertion order) and
     * timestamp order always agree.
     *
     * @throws InsufficientFundsException if the transaction would take the balance below zero
     */
    public Transaction record(TransactionType type, BigDecimal amount, String description) {
        // Generating the id does not affect ordering, so keep it out of the critical section.
        UUID id = UUID.randomUUID();
        synchronized (this) {
            Transaction transaction = new Transaction(id, type, amount, description, Instant.now(clock));
            BigDecimal newBalance = balance.add(transaction.signedAmount());
            if (newBalance.signum() < 0) {
                throw new InsufficientFundsException(amount, balance);
            }
            transactions.add(transaction);
            transactionsById.put(id, transaction);
            balance = newBalance;
            return transaction;
        }
    }

    public synchronized BigDecimal balance() {
        return balance;
    }

    public synchronized List<Transaction> history() {
        List<Transaction> copy = new ArrayList<>(transactions);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    public synchronized Optional<Transaction> find(UUID id) {
        return Optional.ofNullable(transactionsById.get(id));
    }
}
