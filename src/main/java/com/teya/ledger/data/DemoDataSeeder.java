package com.teya.ledger.data;

import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.service.LedgerService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fills the ledger with a few demo transactions on startup, so the API has data to show straight away. Goes through
 * {@link LedgerService} like any other client, so the seed obeys the same rules (scale, no overdraft).
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    /** Recorded in this order; the withdrawals are always covered by the deposits before them. */
    static final List<SeedTransaction> SEED = List.of(
            new SeedTransaction(TransactionType.DEPOSIT, new BigDecimal("1000.00"), "salary"),
            new SeedTransaction(TransactionType.WITHDRAWAL, new BigDecimal("45.50"), "groceries"),
            new SeedTransaction(TransactionType.WITHDRAWAL, new BigDecimal("120.00"), "utilities"),
            new SeedTransaction(TransactionType.DEPOSIT, new BigDecimal("250.00"), "freelance"));

    private final LedgerService ledgerService;

    public DemoDataSeeder(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        SEED.forEach(seed -> ledgerService.recordTransaction(seed.type(), seed.amount(), seed.description()));
    }

    record SeedTransaction(TransactionType type, BigDecimal amount, String description) {
    }
}
