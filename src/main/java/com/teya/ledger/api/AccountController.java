package com.teya.ledger.api;

import com.teya.ledger.api.dto.AccountResponse;
import com.teya.ledger.api.dto.BalanceResponse;
import com.teya.ledger.api.dto.CreateAccountRequest;
import com.teya.ledger.api.dto.CreateTransactionRequest;
import com.teya.ledger.api.dto.TransactionResponse;
import com.teya.ledger.domain.base.Account;
import com.teya.ledger.domain.base.Transaction;
import com.teya.ledger.service.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request.name());
        URI location = URI.create("/api/v1/accounts/" + account.id());
        return ResponseEntity.created(location).body(toResponse(account));
    }

    @GetMapping
    public List<AccountResponse> getAccounts() {
        return accountService.listAccounts().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable UUID accountId) {
        return toResponse(accountService.getAccount(accountId));
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
            @PathVariable UUID accountId, @Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = accountService.recordTransaction(
                accountId, request.type(), request.amount(), request.description());
        URI location = URI.create("/api/v1/accounts/" + accountId + "/transactions/" + transaction.id());
        return ResponseEntity.created(location).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> getTransactions(@PathVariable UUID accountId) {
        return accountService.getHistory(accountId).stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/{accountId}/transactions/{id}")
    public TransactionResponse getTransaction(@PathVariable UUID accountId, @PathVariable UUID id) {
        return TransactionResponse.from(accountService.getTransaction(accountId, id));
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable UUID accountId) {
        return new BalanceResponse(accountService.getBalance(accountId));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.from(account, accountService.getBalance(account.id()));
    }
}
