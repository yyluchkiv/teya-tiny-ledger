package com.teya.ledger.service;

import com.teya.ledger.domain.Transaction;
import com.teya.ledger.domain.TransactionType;
import com.teya.ledger.repository.InMemoryLedgerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private static final int SCALE = 2;

    private final InMemoryLedgerRepository repository;
    private final Clock clock;

    public LedgerService(InMemoryLedgerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records a deposit or withdrawal. Withdrawals that exceed the current balance are rejected with
     * {@link com.teya.ledger.domain.InsufficientFundsException}.
     */
    public Transaction recordTransaction(TransactionType type, BigDecimal amount, String description) {
        Transaction transaction = new Transaction(
                UUID.randomUUID(), type, normalise(amount), description, Instant.now(clock));
        return type == TransactionType.WITHDRAWAL
                ? repository.appendIfSufficient(transaction)
                : repository.append(transaction);
    }

    public BigDecimal getBalance() {
        return normalise(repository.balance());
    }

    public List<Transaction> getHistory() {
        return repository.findAllNewestFirst();
    }

    private static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
