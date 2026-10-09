package com.teya.ledger.service;

import com.teya.ledger.data.Ledger;
import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.domain.base.TransactionType;
import com.teya.ledger.domain.exceptions.AccountNotFoundException;
import com.teya.ledger.domain.exceptions.InsufficientFundsException;
import com.teya.ledger.domain.exceptions.TransactionNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final Ledger ledger;

    public AccountService(Ledger ledger) {
        this.ledger = ledger;
    }

    public Account createAccount(String name) {
        return ledger.createAccount(name);
    }

    public List<Account> listAccounts() {
        return ledger.accounts();
    }

    /** @throws AccountNotFoundException if the account does not exist */
    public Account getAccount(UUID accountId) {
        return ledger.findAccount(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    /**
     * Records a deposit or withdrawal on the account. Withdrawals that exceed the account's balance are rejected with
     * {@link InsufficientFundsException}.
     *
     * @throws AccountNotFoundException if the account does not exist
     */
    public Transaction recordTransaction(UUID accountId, TransactionType type, BigDecimal amount, String description) {
        return ledger.record(accountId, type, Amounts.normalise(amount), description);
    }

    /** @throws AccountNotFoundException if the account does not exist */
    public BigDecimal getBalance(UUID accountId) {
        return ledger.balance(accountId);
    }

    /** @throws AccountNotFoundException if the account does not exist */
    public List<Transaction> getHistory(UUID accountId) {
        return ledger.history(accountId);
    }

    /**
     * @throws AccountNotFoundException if the account does not exist
     * @throws TransactionNotFoundException if no transaction with this id exists on the account
     */
    public Transaction getTransaction(UUID accountId, UUID id) {
        return ledger.find(accountId, id).orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
