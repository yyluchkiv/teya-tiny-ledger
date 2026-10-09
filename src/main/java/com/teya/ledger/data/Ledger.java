package com.teya.ledger.data;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A single in-memory ledger holding every account, and the owner of its invariant: no account balance ever goes below
 * zero, and each balance always equals the sum of that account's recorded transactions.
 *
 * <p>Accounts, balances, histories, the id indexes and transfers are mutated together, so they are all guarded by one
 * lock rather than by concurrent collections: a concurrent collection only makes each individual operation
 * thread-safe, not the check-balance-then-append sequence. Because there is only one lock, a transfer's two legs are
 * trivially atomic and cannot deadlock.
 *
 * <p>The account-less methods ({@link #record(TransactionType, BigDecimal, String)}, {@link #balance()},
 * {@link #history()}, {@link #find(UUID)}) act on the {@linkplain Account#DEFAULT_ID default account}, which always
 * exists.
 */
public class Ledger {

    private final Clock clock;
    /** Insertion-ordered, so {@link #accounts()} can return creation order. */
    private final Map<UUID, AccountState> accounts = new LinkedHashMap<>();
    private final Map<UUID, Transaction> transactionsById = new HashMap<>();
    private final Map<UUID, Transfer> transfersById = new HashMap<>();

    public Ledger(Clock clock) {
        this.clock = clock;
        Account main = new Account(Account.DEFAULT_ID, Account.DEFAULT_NAME, Instant.now(clock));
        accounts.put(main.id(), new AccountState(main));
    }

    // --- Default account -------------------------------------------------------------------------------------------

    /**
     * Records a deposit or withdrawal on the default account.
     *
     * @throws InsufficientFundsException if the transaction would take the balance below zero
     */
    public Transaction record(TransactionType type, BigDecimal amount, String description) {
        return record(Account.DEFAULT_ID, type, amount, description);
    }

    public BigDecimal balance() {
        return balance(Account.DEFAULT_ID);
    }

    public List<Transaction> history() {
        return history(Account.DEFAULT_ID);
    }

    public Optional<Transaction> find(UUID id) {
        return find(Account.DEFAULT_ID, id);
    }

    // --- Accounts --------------------------------------------------------------------------------------------------

    public Account createAccount(String name) {
        UUID id = UUID.randomUUID();
        synchronized (this) {
            Account account = new Account(id, name, Instant.now(clock));
            accounts.put(id, new AccountState(account));
            return account;
        }
    }

    /** All accounts, newest first. */
    public synchronized List<Account> accounts() {
        List<Account> copy = new ArrayList<>(accounts.size());
        accounts.values().forEach(state -> copy.add(state.account));
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    public synchronized Optional<Account> findAccount(UUID accountId) {
        return Optional.ofNullable(accounts.get(accountId)).map(state -> state.account);
    }

    /**
     * Records a deposit or withdrawal on the given account. The timestamp is taken inside the lock, so history order
     * (insertion order) and timestamp order always agree.
     *
     * @throws AccountNotFoundException if the account does not exist
     * @throws InsufficientFundsException if the transaction would take the balance below zero
     */
    public Transaction record(UUID accountId, TransactionType type, BigDecimal amount, String description) {
        // Generating the id does not affect ordering, so keep it out of the critical section.
        UUID id = UUID.randomUUID();
        synchronized (this) {
            AccountState state = state(accountId);
            Transaction transaction =
                    new Transaction(id, accountId, type, amount, description, Instant.now(clock), null);
            ensureCovered(state, transaction);
            append(state, transaction);
            return transaction;
        }
    }

    /** @throws AccountNotFoundException if the account does not exist */
    public synchronized BigDecimal balance(UUID accountId) {
        return state(accountId).balance;
    }

    /**
     * The account's transactions, newest first.
     *
     * @throws AccountNotFoundException if the account does not exist
     */
    public synchronized List<Transaction> history(UUID accountId) {
        List<Transaction> copy = new ArrayList<>(state(accountId).transactions);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    /**
     * Finds a transaction by id, but only if it belongs to the given account.
     *
     * @throws AccountNotFoundException if the account does not exist
     */
    public synchronized Optional<Transaction> find(UUID accountId, UUID id) {
        state(accountId);
        return Optional.ofNullable(transactionsById.get(id)).filter(t -> t.accountId().equals(accountId));
    }

    // --- Transfers -------------------------------------------------------------------------------------------------

    /**
     * Moves {@code amount} from one account to another: a {@code WITHDRAWAL} on the source and a {@code DEPOSIT} on
     * the destination, sharing one timestamp and one {@code transferId}. Either both legs are recorded or neither is.
     *
     * @throws SameAccountTransferException if both ids are the same account
     * @throws AccountNotFoundException if either account does not exist
     * @throws InsufficientFundsException if the source balance does not cover the amount
     */
    public Transfer transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {
        if (fromAccountId.equals(toAccountId)) {
            throw new SameAccountTransferException(fromAccountId);
        }
        UUID transferId = UUID.randomUUID();
        UUID debitId = UUID.randomUUID();
        UUID creditId = UUID.randomUUID();
        synchronized (this) {
            AccountState from = state(fromAccountId);
            AccountState to = state(toAccountId);
            Instant now = Instant.now(clock);
            Transaction debit = new Transaction(
                    debitId, fromAccountId, TransactionType.WITHDRAWAL, amount, description, now, transferId);
            Transaction credit = new Transaction(
                    creditId, toAccountId, TransactionType.DEPOSIT, amount, description, now, transferId);
            // Check before writing anything, so a rejected transfer leaves no trace.
            ensureCovered(from, debit);
            append(from, debit);
            append(to, credit);
            Transfer transfer = new Transfer(
                    transferId, fromAccountId, toAccountId, amount, description, now, debitId, creditId);
            transfersById.put(transferId, transfer);
            return transfer;
        }
    }

    public synchronized Optional<Transfer> findTransfer(UUID id) {
        return Optional.ofNullable(transfersById.get(id));
    }

    // --- Internals (caller holds the lock) -------------------------------------------------------------------------

    private AccountState state(UUID accountId) {
        AccountState state = accounts.get(accountId);
        if (state == null) {
            throw new AccountNotFoundException(accountId);
        }
        return state;
    }

    private static void ensureCovered(AccountState state, Transaction transaction) {
        if (state.balance.add(transaction.signedAmount()).signum() < 0) {
            throw new InsufficientFundsException(transaction.amount(), state.balance);
        }
    }

    private void append(AccountState state, Transaction transaction) {
        state.transactions.add(transaction);
        state.balance = state.balance.add(transaction.signedAmount());
        transactionsById.put(transaction.id(), transaction);
    }

    /** Per-account balance and history index, so balance reads are O(1) and history needs no filtering. */
    private static final class AccountState {

        private final Account account;
        private final List<Transaction> transactions = new ArrayList<>();
        private BigDecimal balance = BigDecimal.ZERO.setScale(2);

        private AccountState(Account account) {
            this.account = account;
        }
    }
}
