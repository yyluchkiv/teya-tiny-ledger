package com.teya.ledger.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.service.AccountService;
import com.teya.ledger.service.LedgerService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoDataSeederTest {

    private final Ledger ledger = new Ledger(Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC));

    private final LedgerService service = new LedgerService(ledger);

    private final AccountService accountService = new AccountService(ledger);

    private final DemoDataSeeder seeder = new DemoDataSeeder(service, accountService);

    @Test
    void seedsDemoTransactionsAndBalance() {
        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        List<Transaction> history = service.getHistory();
        assertThat(history).hasSize(4);
        assertThat(history).extracting(Transaction::description)
                .containsExactly("freelance", "utilities", "groceries", "salary");
        assertThat(history).extracting(Transaction::type).containsExactly(
                TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.WITHDRAWAL, TransactionType.DEPOSIT);
        assertThat(service.getBalance()).isEqualTo(new BigDecimal("1084.50"));
    }

    @Test
    void seedsSavingsAccountWithOpeningBalance() {
        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        List<Account> accounts = accountService.listAccounts();
        assertThat(accounts).extracting(Account::name).containsExactly("savings", "main");
        Account savings = accounts.get(0);
        assertThat(accountService.getBalance(savings.id())).isEqualTo(new BigDecimal("500.00"));
        assertThat(accountService.getHistory(savings.id()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.type()).isEqualTo(TransactionType.DEPOSIT);
                    assertThat(t.description()).isEqualTo("opening balance");
                });
    }
}
