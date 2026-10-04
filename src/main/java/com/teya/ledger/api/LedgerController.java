package com.teya.ledger.api;

import com.teya.ledger.api.dto.BalanceResponse;
import com.teya.ledger.api.dto.CreateTransactionRequest;
import com.teya.ledger.api.dto.TransactionResponse;
import com.teya.ledger.domain.Transaction;
import com.teya.ledger.service.LedgerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction =
                ledgerService.recordTransaction(request.type(), request.amount(), request.description());
        URI location = URI.create("/api/v1/transactions/" + transaction.id());
        return ResponseEntity.created(location).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> getTransactions() {
        return ledgerService.getHistory().stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/balance")
    public BalanceResponse getBalance() {
        return new BalanceResponse(ledgerService.getBalance());
    }
}
