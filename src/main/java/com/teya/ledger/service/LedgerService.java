package com.teya.ledger.service;

import com.teya.ledger.domain.Ledger;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.TransactionNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private static final int SCALE = 2;

    private final Ledger ledger;

    public LedgerService(Ledger ledger) {
        this.ledger = ledger;
    }

    /**
     * Records a deposit or withdrawal. Withdrawals that exceed the current balance are rejected with
     * {@link InsufficientFundsException}.
     */
    public Transaction recordTransaction(TransactionType type, BigDecimal amount, String description) {
        return ledger.record(type, normalise(amount), description);
    }

    public BigDecimal getBalance() {
        return ledger.balance();
    }

    public List<Transaction> getHistory() {
        return ledger.history();
    }

    public Transaction getTransaction(UUID id) {
        return ledger.find(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private static BigDecimal normalise(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
